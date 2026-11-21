// One test per case in core's specs for the chat components (vendor/core/components/<name>.spec.json), so CI's result
// names every case and its outcome. The case's props and slots are drawn, and the agent tree TalkBack and agents read
// must be the one the case gives.
package bot.ouro.ui.hx

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
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
@Config(qualifiers = "w411dp-h6000dp-xxhdpi")
class ChatCaseTest(private val id: String) {
    companion object {
        private val dir = File("../vendor/core/components")

        /** The chat components whose cases this runs; each joins when its composable does. */
        private val components = listOf("message", "reaction-bar", "message-list")

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

    @Test
    fun case_draws_the_agent_tree_its_spec_gives() {
        val (name, caseId) = id.split("/")
        val spec = JSONObject(File(dir, "$name.spec.json").readText()).getJSONArray("cases")
        val case = (0 until spec.length()).map { spec.getJSONObject(it) }.first { it.getString("id") == caseId }
        val props = case.getJSONObject("props")
        val slots = case.optJSONObject("slots") ?: JSONObject()
        when (name) {
            "message" -> rule.setContent {
                OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                    OuroMessage(
                        props.getString("name"), slots.getString("text"),
                        role = when (props.optString("role")) {
                            "user" -> OuroMessageRole.User
                            "system" -> OuroMessageRole.System
                            else -> OuroMessageRole.Assistant
                        },
                        time = props.optString("time").ifEmpty { null },
                        continued = props.optBoolean("continued"),
                        streaming = props.optBoolean("streaming"),
                        failed = props.optBoolean("failed"),
                        pending = props.optBoolean("pending"),
                    )
                }
            }
            "reaction-bar" -> rule.setContent {
                OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                    OuroReactionBar(
                        // "key: emoji | name | count | mine", one per "; ".
                        slots.optString("reactions").split("; ").filter { it.isNotBlank() }.map { line ->
                            val (key, rest) = line.split(": ", limit = 2)
                            val f = rest.split(" | ")
                            OuroReaction(key, f[0], f[1], f[2].toInt(), mine = f.getOrNull(3) == "mine")
                        },
                        onReact = {},
                        label = props.optString("label", "Reactions"),
                        disabled = props.optBoolean("disabled"),
                        showMore = props.optBoolean("showMore"),
                    )
                }
            }
            "message-list" -> rule.setContent {
                OuroTheme(LIGHT.theme, dark = false, increasedContrast = false, reduceMotion = false) {
                    OuroMessageList(
                        props.getString("label"),
                        // "id: author | time | text | mine", one per "; ".
                        slots.optString("messages").split("; ").filter { it.isNotBlank() }.map { line ->
                            val (id, rest) = line.split(": ", limit = 2)
                            val f = rest.split(" | ")
                            OuroChatMessage(id, f[0], f[1], f[2], mine = f.getOrNull(3) == "mine")
                        },
                        hasOlder = props.optBoolean("hasOlder"),
                    )
                }
            }
            else -> error("no renderer for $name. Fix: add one to ChatCaseTest.")
        }
        val root = rule.onAllNodes(kindIs(name)).fetchSemanticsNodes().single()
        val agent = case.getJSONObject("agent")
        fun SemanticsNode.within(): List<SemanticsNode> = listOf(this) + children.flatMap { it.within() }
        fun SemanticsNode.cd() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
        val tree = if (name == "message-list") JSONObject().put("role", "group").put("name", root.cd()).put("focusable", false).put(
            "children",
            JSONArray(
                root.children.filter { it.cd().isNotBlank() }
                    .map { JSONObject().put("role", if (it.config.getOrNull(SemanticsProperties.Role) == Role.Button) "button" else "listitem").put("name", it.cd()) },
            ),
        ) else if (name == "reaction-bar") JSONObject().put("role", "group").put("name", root.cd()).put("focusable", false).put(
            "children",
            JSONArray(
                root.children.flatMap { it.within() }
                    .filter { it.config.getOrNull(SemanticsProperties.Role).let { r -> r == Role.Checkbox || r == Role.Button } }
                    .map { JSONObject().put("role", "button").put("name", it.cd()) },
            ),
        ) else
        // A message is one list item named by its phrase; it is never focusable.
        JSONObject().put("role", "listitem").put("name", root.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: "")
            .put("focusable", root.config.contains(SemanticsProperties.Focused))
        assertEquals(id + "\n" + rule.onRoot().printToString(), agent.toString(), tree.toString())
    }
}
