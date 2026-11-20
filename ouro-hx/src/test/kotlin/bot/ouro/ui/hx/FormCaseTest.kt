// One test per case in core's component specs for the form components (vendor/core/components/<name>.spec.json), so CI's
// result names every case and its outcome (JUnit XML, and the per-case table in the job summary). The case's props are
// drawn, and the agent tree TalkBack and agents read must be the one the case gives.
package bot.ouro.ui.hx

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h4000dp-xxhdpi")
class FormCaseTest(private val id: String) {
    companion object {
        private val dir = File("../vendor/core/components")

        /** The components whose cases this runs; each joins when its composable does. */
        private val components = listOf("checkbox", "text-field", "switch")

        private fun cases(name: String) = JSONObject(File(dir, "$name.spec.json").readText()).getJSONArray("cases").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun ids(): List<Array<Any>> = components.flatMap { n ->
            // Parameters are read outside Robolectric's sandbox, where Android's org.json is only a stub.
            val spec = bot.ouro.ui.hx.composed.ComposedJson.parse(File(dir, "$n.spec.json").readText()) as Map<*, *>
            (spec["cases"] as List<*>).map { arrayOf<Any>("$n/${(it as Map<*, *>)["id"]}") }
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun kindIs(kind: String) = SemanticsMatcher("kind=$kind") { n -> n.config.getOrNull(OuroReport)?.let { parseOuroReport(it)["kind"] } == kind }

    private fun SemanticsNode.within(): List<SemanticsNode> = listOf(this) + children.flatMap { it.within() }
    private fun SemanticsNode.name() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: (config.getOrNull(SemanticsProperties.Text) ?: emptyList()).joinToString(" ") { it.text }

    /** The role an agent's tree gives a control; a case's agent role has no switch, combobox or dropdown, so those read as the nearest one. */
    private fun agentRole(n: SemanticsNode): String? = when {
        n.config.contains(SemanticsActions.SetText) -> "textbox"
        n.config.getOrNull(SemanticsProperties.Role) in listOf(Role.Checkbox, Role.Switch) -> "checkbox"
        n.config.getOrNull(SemanticsProperties.Role) in listOf(Role.DropdownList, Role.Button) -> "button"
        else -> null
    }

    /** The tree an agent reads for the one control a component draws: its role, name and focus, and the buttons inside it. */
    private fun agentTree(root: SemanticsNode): JSONObject {
        val control = root.within().first { agentRole(it) != null }
        val tree = JSONObject().put("role", agentRole(control)).put("name", control.name()).put("focusable", control.config.contains(SemanticsProperties.Focused))
        val inside = control.within().drop(1).filter { agentRole(it) == "button" && it.config.contains(SemanticsActions.OnClick) }
        if (inside.isNotEmpty()) tree.put("children", JSONArray(inside.map { JSONObject().put("role", "button").put("name", it.name()) }))
        return tree
    }

    @Test
    fun case_draws_the_agent_tree_its_spec_gives() {
        val (name, caseId) = id.split("/")
        val case = cases(name).first { it.getString("id") == caseId }
        val props = case.getJSONObject("props")
        fun text(key: String) = if (props.has(key)) props.getString(key) else null
        val content: @Composable () -> Unit = when (name) {
            "checkbox" -> ({
                OuroCheckbox(
                    props.getString("label"), checked = props.optBoolean("checked"), onCheckedChange = {},
                    description = text("description"), error = text("error"), required = props.optBoolean("required"),
                    indeterminate = props.optBoolean("indeterminate"), hideLabel = props.optBoolean("hideLabel"),
                )
            })
            "text-field" -> ({
                OuroTextField(
                    props.getString("label"), value = text("value") ?: "", onValueChange = {},
                    description = text("description"), error = text("error"), required = props.optBoolean("required"),
                    type = when (text("type")) {
                        "email" -> OuroTextFieldType.Email
                        "password" -> OuroTextFieldType.Password
                        else -> OuroTextFieldType.Text
                    },
                )
            })
            "switch" -> ({
                OuroSwitch(
                    props.getString("label"), checked = props.optBoolean("checked"), onCheckedChange = {},
                    description = text("description"), error = text("error"), required = props.optBoolean("required"),
                )
            })
            else -> error("no renderer for $name. Fix: add one to FormCaseTest.")
        }
        rule.setContent { OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) { content() } }
        val root = rule.onAllNodes(kindIs(name)).fetchSemanticsNodes().single()
        assertEquals(id, case.getJSONObject("agent").toString(), agentTree(root).toString())
    }
}
