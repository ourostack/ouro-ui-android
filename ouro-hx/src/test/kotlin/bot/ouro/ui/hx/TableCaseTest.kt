// One test per case in core's Table spec (vendor/core/components/table.spec.json), so CI's result names every case and its
// outcome. The case's props and slots are drawn, and the agent tree TalkBack and agents read must be the one the case gives.
package bot.ouro.ui.hx

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
class TableCaseTest(private val id: String) {
    companion object {
        private val dir = File("../vendor/core/components")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun ids(): List<Array<Any>> {
            // Parameters are read outside Robolectric's sandbox, where Android's org.json is only a stub.
            val spec = bot.ouro.ui.hx.composed.ComposedJson.parse(File(dir, "table.spec.json").readText()) as Map<*, *>
            return (spec["cases"] as List<*>).map { arrayOf<Any>("table/${(it as Map<*, *>)["id"]}") }
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun kindIs(kind: String) = SemanticsMatcher("kind=$kind") { n -> n.config.getOrNull(OuroReport)?.let { parseOuroReport(it)["kind"] } == kind }

    private fun SemanticsNode.words() = (config.getOrNull(SemanticsProperties.Text) ?: emptyList()).joinToString(" ") { it.text }

    private fun SemanticsNode.described() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")

    @Test
    fun case_draws_the_agent_tree_its_spec_gives() {
        val caseId = id.substringAfter("/")
        val cases = JSONObject(File(dir, "table.spec.json").readText()).getJSONArray("cases")
        val case = (0 until cases.length()).map { cases.getJSONObject(it) }.first { it.getString("id") == caseId }
        val props = case.getJSONObject("props")
        val slots = case.optJSONObject("slots") ?: JSONObject()
        val rows = if (slots.has("rows")) slots.getString("rows").split("; ").map { it.split(" | ") } else emptyList()
        val sortable = props.optString("sortable").split(", ").filter { it.isNotEmpty() }.toSet()
        rule.setContent {
            OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                OuroTable(
                    props.getString("caption"), props.getString("columns").split(", "), rows,
                    hideCaption = props.optBoolean("hideCaption"),
                    sortedBy = props.optString("sortedBy").ifEmpty { null },
                    sortDirection = if (props.optString("sortDirection") == "descending") OuroSortDirection.Descending else OuroSortDirection.Ascending,
                    sortable = sortable.toList(),
                    empty = props.optString("empty").ifEmpty { "Nothing here yet." },
                    onRowPress = if (props.optBoolean("rowHref")) ({ _ -> }) else null,
                )
            }
        }
        val root = rule.onAllNodes(kindIs("table")).fetchSemanticsNodes().single()
        val tree = JSONObject().put("role", "group").put("name", root.described() ?: "").put("focusable", false)
            .put("children", org.json.JSONArray(root.children.map { ch ->
                val role = ch.config.getOrNull(SemanticsProperties.Role)
                val name = ch.described() ?: ch.words()
                val kind = when {
                    role == Role.Button -> "button"
                    ch.config.contains(SemanticsProperties.Heading) -> "heading"
                    else -> "listitem"
                }
                JSONObject().put("role", kind).put("name", name)
            }))
        assertEquals(id + "\n" + rule.onRoot().printToString(), case.getJSONObject("agent").toString(), tree.toString())
    }
}
