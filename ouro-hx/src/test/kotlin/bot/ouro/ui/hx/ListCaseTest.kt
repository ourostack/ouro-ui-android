// One test per case in core's specs for the list components (vendor/core/components/<name>.spec.json), so CI's result
// names every case and its outcome. The case's props and slots are drawn, and the agent tree TalkBack and agents read
// must be the one the case gives.
package bot.ouro.ui.hx

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
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
@Config(qualifiers = "w411dp-h6000dp-xxhdpi")
class ListCaseTest(private val id: String) {
    companion object {
        private val dir = File("../vendor/core/components")

        /** The list components whose cases this runs; each joins when its composable does. */
        private val components = listOf("item-group", "swipe-actions")

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

    private fun SemanticsNode.rowWords(): String = (listOf(this) + children).map { it.words() }.filter { it.isNotBlank() }.joinToString(" ")

    private fun SemanticsNode.words() = (config.getOrNull(SemanticsProperties.Text) ?: emptyList()).joinToString(" ") { it.text }

    @Test
    fun case_draws_the_agent_tree_its_spec_gives() {
        val (name, caseId) = id.split("/")
        val spec = JSONObject(File(dir, "$name.spec.json").readText()).getJSONArray("cases")
        val case = (0 until spec.length()).map { spec.getJSONObject(it) }.first { it.getString("id") == caseId }
        val props = case.getJSONObject("props")
        val slots = case.optJSONObject("slots") ?: JSONObject()
        when (name) {
            "item-group" -> {
                val rows = if (slots.has("items")) slots.getString("items").split(", ") else emptyList()
                rule.setContent {
                    OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                        OuroItemGroup(props.optString("label").ifEmpty { null }, empty = props.optString("empty").ifEmpty { null }) {
                            for (r in rows) item { OuroItem(r) }
                        }
                    }
                }
            }
            "swipe-actions" -> {
                fun acts(k: String) = if (props.has(k)) props.getString(k).split(", ").map { OuroSwipeAction(it) {} } else emptyList()
                rule.setContent {
                    OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                        OuroSwipeActions(
                            props.getString("label"), leading = acts("leading"), trailing = acts("trailing"),
                            actionsLabel = props.optString("actionsLabel").ifEmpty { "Actions for ${props.getString("label")}" },
                            tone = if (props.optString("tone") == "destructive") OuroTone.Destructive else OuroTone.Secondary,
                        ) { BasicText(slots.getString("content")) }
                    }
                }
            }
            else -> error("no renderer for $name. Fix: add one to ListCaseTest.")
        }
        val root = rule.onAllNodes(kindIs(name)).fetchSemanticsNodes().single()
        val agent = case.getJSONObject("agent")
        // A collection is a group named by its label; a row is a listitem; plain words are text, never focusable.
        val tree = if (name == "swipe-actions") {
            JSONObject().put("role", "group").put("name", root.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
                .put("focusable", false)
                .put("children", org.json.JSONArray(root.children.map { ch ->
                    if (ch.config.getOrNull(SemanticsProperties.Role) == Role.Button) JSONObject().put("role", "button").put("name", ch.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
                    else JSONObject().put("role", "text").put("name", ch.words())
                }))
        } else if (root.config.contains(SemanticsProperties.CollectionInfo)) {
            JSONObject().put("role", "group").put("name", root.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
                .put("focusable", false)
                .put("children", org.json.JSONArray(root.children.filter { it.config.contains(SemanticsProperties.CollectionItemInfo) }.map { JSONObject().put("role", "listitem").put("name", it.rowWords()) }))
        } else {
            JSONObject().put("role", "text").put("name", root.words()).put("focusable", root.config.contains(SemanticsProperties.Focused))
        }
        assertEquals(id + "\n" + rule.onRoot().printToString(), agent.toString(), tree.toString())
    }
}
