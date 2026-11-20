// One test per case in core's component specs for the navigation components (vendor/core/components/<name>.spec.json),
// so CI's result names every case and its outcome (JUnit XML, and the per-case table in the job summary). The case's
// props and slots are drawn, and the agent tree TalkBack and agents read must be the one the case gives.
package bot.ouro.ui.hx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class ComponentCaseTest(private val id: String) {
    companion object {
        private val dir = File("../vendor/core/components")

        /** The components whose cases this runs; each joins when its composable does. */
        private val components = listOf("tabs")

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

    private fun SemanticsNode.words() = (config.getOrNull(SemanticsProperties.Text) ?: emptyList()).joinToString(" ") { it.text }

    /** The tree an agent reads: the group's name, and what it holds, each a button named by its words. */
    private fun agentTree(group: SemanticsNode): JSONObject {
        val children = group.children.map { n ->
            val role = if (n.config.contains(SemanticsActions.OnClick)) "button" else "text"
            JSONObject().put("role", role).put("name", n.words())
        }
        return JSONObject().put("role", "group").put("name", group.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
            .put("children", org.json.JSONArray(children))
    }

    @Test
    fun case_draws_the_agent_tree_its_spec_gives() {
        val (name, caseId) = id.split("/")
        val case = cases(name).first { it.getString("id") == caseId }
        val props = case.getJSONObject("props")
        val slots = case.getJSONObject("slots")
        val pressed = mutableListOf<String>()
        var value by mutableStateOf(props.optString("value"))
        when (name) {
            "tabs" -> {
                val tabs = slots.getString("items").split("; ").map { s -> OuroTab(s.removeSuffix(" (disabled)").lowercase(), s.removeSuffix(" (disabled)"), s.endsWith(" (disabled)")) }
                val orientation = if (props.optString("orientation") == "vertical") OuroOrientation.Vertical else OuroOrientation.Horizontal
                rule.setContent {
                    OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                        OuroTabs(props.getString("label"), value, { pressed += it; value = it }, tabs, orientation = orientation, panel = if (slots.has("panel")) ({ v -> androidx.compose.foundation.text.BasicText("${slots.getString("panel")} $v") }) else null)
                    }
                }
            }
            else -> error("no renderer for $name. Fix: add one to ComponentCaseTest.")
        }
        val group = rule.onAllNodes(kindIs(name)).fetchSemanticsNodes().single()
        assertEquals(id, case.getJSONObject("agent").toString(), agentTree(group).toString())
        for (child in group.children) {
            val selected = child.config.getOrNull(SemanticsProperties.Selected)
            assertEquals("$id ${child.words()} selected", child.words().lowercase() == props.getString("value"), selected)
        }
        val before = value
        for ((i, child) in group.children.withIndex()) {
            val disabled = child.config.contains(SemanticsProperties.Disabled)
            val matcher = SemanticsMatcher("child $i") { n -> n.id == child.id }
            pressed.clear()
            if (!disabled && child.words().lowercase() != before) {
                rule.onNode(matcher).performClick()
                rule.waitForIdle()
                assertEquals("$id pressing ${child.words()}", listOf(child.words().lowercase()), pressed)
                assertTrue("$id the pressed tab is selected", rule.onNode(matcher).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true)
            } else if (disabled) {
                rule.onNode(matcher).performClick()
                rule.waitForIdle()
                assertEquals("$id ${child.words()} stays unpressed", emptyList<String>(), pressed)
            }
        }
    }
}
