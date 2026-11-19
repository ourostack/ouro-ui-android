// The A2UI adapter's port (A2UI.kt) against core's A2UI conformance cases, vendored by SHA-256 from the core tag
// (core-provenance.json): each stream resolves to the case's expect, written as core writes it (every surface's tree, or
// every problem), and every tree it gives passes the composed tree's own validator. Core's test/a2ui.test.mts and
// test/composed.test.mts are the model for the rest.
package bot.ouro.ui.hx.composed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class A2UITest {
    private val dir = File("../vendor/core/composed")
    private fun read(name: String): Any? = ComposedJson.parse(File(dir, name).readText())
    private val app = ComposedCatalog.fromJson(read("recipe.catalog.json"))
    private val hx = ComposedCatalog.fromJson(read("catalog.json"))
    private val catalogs = mapOf("catalog.json" to hx, "recipe.catalog.json" to app)

    /** The cases core-provenance.json records, so every one is core's at the tag, byte for byte. */
    private val caseNames = ((ComposedJson.parse(File("../core-provenance.json").readText()) as Map<*, *>)["files"] as Map<*, *>).keys
        .map { it.toString() }.filter { it.startsWith("vendor/core/composed/cases/") }.sorted().map { it.substringAfterLast('/') }

    private fun case(name: String) = ComposedJson.parse(File(dir, "cases/$name").readText()) as Map<*, *>

    private fun create(components: String, data: String = "{}"): List<Any?> =
        ComposedJson.parse("""[{ "version": "1.0", "createSurface": { "surfaceId": "s", "catalogId": "${app.catalogId}", "dataModel": $data, "components": $components } }]""") as List<Any?>

    private fun problems(components: String, data: String = "{}") = OuroA2UI.validateMessages(app, create(components, data))

    private fun surface(components: String, data: String = "{}", label: ((LabelContext) -> String?)? = null): Map<String, Any?> {
        val resolved = OuroA2UI.resolveMessages(app, create(components, data), label)
        assertTrue(resolved.problems.joinToString("; ") { "${it.path} ${it.message}" }, resolved.valid)
        return resolved.surfaces.single().tree!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.at(vararg keys: Any): Any? = keys.fold(this) { v, k -> if (k is Int) (v as List<Any?>)[k] else (v as Map<String, Any?>)[k] }

    @Test
    fun every_case_core_ships_is_here() {
        assertEquals(13, caseNames.size)
    }

    @Test
    fun each_case_resolves_to_its_expect() {
        for (name in caseNames) {
            val c = case(name)
            val got = OuroA2UI.resolveMessages(catalogs.getValue(c["catalog"] as String), c["messages"]).toJson()
            assertEquals(name, ComposedJson.stringify(c["expect"]), ComposedJson.stringify(got))
            assertEquals(name, name.startsWith("invalid-"), got.containsKey("problems"))
        }
    }

    @Test
    fun each_tree_a_case_gives_passes_the_tree_validator() {
        for (name in caseNames) {
            val c = case(name)
            val cat = catalogs.getValue(c["catalog"] as String)
            for (s in OuroA2UI.resolveMessages(cat, c["messages"]).surfaces) {
                val tree = s.tree ?: continue
                assertEquals("$name ${s.surfaceId}", emptyList<TreeProblem>(), OuroComposed.validateTree(cat, tree))
            }
        }
    }

    @Test
    fun a_name_counts_only_when_the_catalog_itself_has_it() {
        for (name in listOf("constructor", "toString", "__proto__")) {
            assertTrue(name, problems("""[{ "id": "root", "component": "$name" }]""").any { it.message.contains("is not in this catalog") })
            assertTrue(name, problems("""[{ "id": "root", "component": "ActionButton", "action": "$name" }]""").any { it.message.contains("not one of the app's actions") })
            assertTrue(name, problems("""[{ "id": "root", "component": "ActionButton", "action": "share_recipe", "input": { "recipe": "r", "with": "s@e.com", "$name": true } }]""").any { it.message.contains("share_recipe has no input $name") })
        }
    }

    @Test
    fun no_pointer_reaches_a_prototype() {
        val stream = create("""[{ "id": "root", "component": "Text", "text": "hi" }]""") +
            listOf(ComposedJson.parse("""{ "version": "1.0", "updateDataModel": { "surfaceId": "s", "path": "/__proto__/polluted", "value": "yes" } }"""))
        assertTrue(OuroA2UI.validateMessages(app, stream).any { it.path.endsWith("/updateDataModel/path") })
        for (pointer in listOf("/__proto__/x", "constructor", "items/__proto__", "/a/prototype")) {
            val bound = problems("""[{ "id": "root", "component": "Card", "children": ["f", "b"] }, { "id": "f", "component": "Text", "text": { "@path": "$pointer" } }, { "id": "b", "component": "ActionButton", "action": "share_recipe", "input": { "recipe": { "@path": "$pointer" }, "with": "s@e.com" } }]""")
            assertTrue(pointer, bound.count { it.message.contains("never through __proto__") } >= 2)
        }
        assertTrue(problems("""[{ "id": "root", "component": "Stack", "children": { "componentId": "t", "path": "/__proto__" } }, { "id": "t", "component": "Text", "text": "x" }]""").any { it.message.contains("never through __proto__") })
        assertTrue(problems("""[{ "id": "root", "component": "TextField", "label": "L", "value": { "@path": "/constructor" } }]""").any { it.message.contains("never through __proto__") })
    }

    @Test
    fun a_button_is_the_catalog_s_and_its_label_and_consent_can_t_be_sent() {
        val forged = problems("""[{ "id": "root", "component": "ActionButton", "action": "share_recipe", "label": "Free gift", "press": "run", "consent": "read", "input": { "recipe": "r", "with": "s@e.com" } }]""")
        assertEquals(listOf("label", "press", "consent").map { "/0/createSurface/components/0/$it" }, forged.map { it.path })
        assertTrue(forged.all { it.message.startsWith("ActionButton has no ") })
        val share = surface("""[{ "id": "root", "component": "ActionButton", "action": "share_recipe", "input": { "recipe": "r", "with": "s@e.com" }, "@accessibility": { "label": "Free gift" } }]""")
        assertEquals("Share recipe", share.at("props", "label"))
        assertEquals("confirm", share.at("props", "press"))
        assertEquals("@label", (share["props"] as Map<*, *>).keys.last())
        assertTrue(problems("""[{ "id": "root", "component": "Button", "label": "Pay" }]""").any { it.message.startsWith("\"Button\" is not in this catalog") })
        val order = problems("""[{ "id": "root", "component": "ActionButton", "action": "order_ingredients", "input": { "recipe": "r1" } }]""")
        assertEquals("person-only", (order.single().refusal!!["error"] as Map<*, *>)["code"])
    }

    @Test
    fun a_button_bound_to_data_no_field_writes_reads_it_now_and_never_follows_a_value_shaped_like_a_binding() {
        val button = """[{ "id": "root", "component": "Card", "children": ["share"] }, { "id": "share", "component": "ActionButton", "action": "share_recipe", "input": { "recipe": { "@path": "/recipe" }, "with": "sam@example.com" }, "@accessibility": { "label": "Share it" } }]"""
        val obj = surface(button, """{ "recipe": { "id": "r1" } }""").at("children", 0)
        assertEquals("""{"recipe":{"id":"r1"},"with":"sam@example.com"}""", ComposedJson.stringify(obj.at("props", "input")))
        val posing = surface(button, """{ "recipe": { "@path": "/secret" }, "secret": "S" }""").at("children", 0)
        assertEquals("""{"@path":"/secret"}""", posing.at("props", "input", "recipe"))
        val press = OuroComposed.personPress(ComposedNode.fromJson(posing), true, mapOf("secret" to "S"))
        assertEquals("""{"@path":"/secret"}""", press.input["recipe"])
        assertEquals("@label", (posing.at("props") as Map<*, *>).keys.last())
    }

    @Test
    fun a_button_bound_to_a_field_stays_bound_and_sends_what_the_person_entered() {
        val tree = surface("""[{ "id": "root", "component": "Card", "children": ["servings", "shop"] }, { "id": "servings", "component": "TextField", "label": "Servings", "value": { "@path": "/servings" }, "type": "number" }, { "id": "shop", "component": "ActionButton", "action": "add_recipe_to_shopping_list", "input": { "recipe": { "@path": "/recipe" }, "servings": { "@path": "servings" } } }]""", """{ "recipe": "rcp_1", "servings": 2 }""")
        val shop = ComposedNode.fromJson(tree.at("children", 1))
        assertEquals("""{"recipe":"rcp_1","servings":{"@path":"/servings"}}""", ComposedJson.stringify(shop.props["input"]))
        assertEquals("""{"recipe":"rcp_1","servings":6}""", ComposedJson.stringify(OuroComposed.personPress(shop, false, mapOf("recipe" to "rcp_1", "servings" to 6L)).input))
    }

    @Test
    fun the_app_s_resolver_names_an_id_and_only_an_id() {
        val seen = mutableListOf<String>()
        val tree = surface(
            """[{ "id": "root", "component": "ActionButton", "action": "share_recipe", "input": { "recipe": { "@path": "/recipe/id" }, "recipeTitle": "Something else", "with": "sam@example.com" } }]""",
            """{ "recipe": { "id": "rcp_lemon_orzo", "title": "Lemon orzo" } }""",
        ) { c -> seen += "${c.action} ${c.field} ${c.value}"; if (c.field == "recipe") "Lemon orzo (yours)" else null }
        assertEquals("""[{"label":"Recipe","value":"Lemon orzo (yours)","raw":"rcp_lemon_orzo"},{"label":"Send to","value":"sam@example.com"}]""", ComposedJson.stringify(tree.at("props", "confirm", "fields")))
        assertEquals(listOf("share_recipe recipe rcp_lemon_orzo"), seen)
    }

    @Test
    fun the_envelope_the_surfaces_and_the_tree() {
        assertEquals(emptyList<A2UIProblem>(), problems("""[{ "id": "root", "component": "Text", "text": "Hi" }]"""))
        assertTrue(OuroA2UI.validateMessages(app, ComposedJson.parse("""[{ "version": "0.9", "createSurface": {} }]"""))[0].message.contains("\"version\": \"1.0\""))
        assertTrue(OuroA2UI.validateMessages(app, ComposedJson.parse("""[{ "version": "1.0", "updateComponents": { "surfaceId": "x", "components": [] } }]"""))[0].message.contains("Create surface x"))
        val once = create("""[{ "id": "root", "component": "Text", "text": "Hi" }]""")
        assertEquals("INTEGRITY_ERROR", OuroA2UI.validateMessages(app, once + once)[0].code)
        assertTrue(problems("""[{ "id": "a", "component": "Text", "text": "Hi" }]""")[0].message.contains("id root"))
        assertTrue(problems("""[{ "id": "root", "component": "Card", "children": ["a"] }, { "id": "a", "component": "Card", "children": ["root"] }]""").any { it.message == "root can't be a child." })
        assertTrue(problems("""[{ "id": "root", "component": "Card", "children": ["a"] }, { "id": "a", "component": "Card", "children": ["b"] }, { "id": "b", "component": "Card", "children": ["a"] }]""").any { it.message == "The tree loops: root > a > b > a." })
        assertTrue(problems("""[{ "id": "root", "component": "Image", "url": "https://x/y.jpg" }]""")[0].message.contains("needs description"))
        assertTrue(problems("""[{ "id": "1root", "component": "Text", "text": "Hi" }]""")[0].message.contains("is not an identifier"))
        assertTrue(problems("""[{ "id": "\u309b", "component": "Text", "text": "Hi" }]""")[0].message.contains("is not an identifier"))
        assertEquals(emptyList<A2UIProblem>(), problems("""[{ "id": "root", "component": "Card", "children": ["été_1"] }, { "id": "été_1", "component": "Text", "text": "Hi" }]"""))
        assertEquals("Send a list of A2UI messages.", OuroA2UI.validateMessages(app, emptyMap<String, Any?>()).single().message)
    }

    @Test
    fun an_agent_can_t_press_a_button_through_the_screen() {
        assertTrue(OuroA2UI.functionCallProblem(app, "share_recipe").message.contains("through their own tool (MCP share_recipe)"))
        assertEquals("refused", OuroA2UI.functionCallProblem(app, "order_ingredients").refusal!!["state"])
        assertTrue(OuroA2UI.functionCallProblem(app, "openUrl").message.contains("no functions an agent may call, so openUrl is not one"))
    }

    @Test
    fun a_press_is_the_a2ui_action_message_that_says_the_person_pressed_it() {
        val tree = ComposedNode.fromJson(OuroA2UI.resolveMessages(app, case("action-buttons.json")["messages"]).surfaces.single().tree)
        val (open, shop, share, order) = tree.children
        assertEquals(
            """{"version":"1.0","action":{"name":"add_recipe_to_shopping_list","surfaceId":"buttons","sourceComponentId":"shop","timestamp":"2026-11-13T12:00:00Z","userMessage":"Add ingredients to list","context":{"recipe":"rcp_123","servings":4},"metadata":{"extensions":{"bot_ouroboros_action":{"consent":"write","by":"person"}}}}}""",
            ComposedJson.stringify(OuroA2UI.actionMessage("buttons", shop, "2026-11-13T12:00:00Z")),
        )
        assertEquals("get_recipe", OuroA2UI.actionMessage("b", open, "t").at("action", "name"))
        assertThrows(OuroComposed.PressRefused::class.java) { OuroA2UI.actionMessage("b", share, "t") }
        assertEquals("""{"extensions":{"bot_ouroboros_action":{"consent":"consequential","by":"person","confirmed":true}}}""", ComposedJson.stringify(OuroA2UI.actionMessage("b", share, "t", confirmed = true).at("action", "metadata")))
        assertThrows(OuroComposed.PressRefused::class.java) { OuroA2UI.actionMessage("b", order, "t", confirmed = true) }
        assertEquals("2026-11-13T12:00:00.000Z", OuroA2UI.timestamp(1_794_571_200_000L))
    }

    @Test
    fun data_model_writes_follow_json_pointer_and_an_empty_relative_path_is_the_template_item() {
        val resolved = OuroA2UI.resolveMessages(hx, ComposedJson.parse("""
            [{ "version": "1.0", "createSurface": { "surfaceId": "s", "catalogId": "${hx.catalogId}", "dataModel": { "a": { "b": 1 }, "list": [1, 2, 3] } } },
             { "version": "1.0", "updateDataModel": { "surfaceId": "s", "path": "/a/c~1d", "value": "x" } },
             { "version": "1.0", "updateDataModel": { "surfaceId": "s", "path": "/list/1", "value": null } },
             { "version": "1.0", "updateDataModel": { "surfaceId": "s", "path": "/new/deep", "value": true } }]
        """))
        assertEquals("""{"a":{"b":1,"c/d":"x"},"list":[1,3],"new":{"deep":true}}""", ComposedJson.stringify(resolved.surfaces.single().data))
        assertNull(resolved.surfaces.single().tree)
        val tags = OuroA2UI.resolveMessages(hx, ComposedJson.parse("""
            [{ "version": "1.0", "createSurface": { "surfaceId": "s", "catalogId": "${hx.catalogId}", "dataModel": { "tags": ["Vegetarian", "One pan"] }, "components": [
              { "id": "root", "component": "Stack", "children": { "componentId": "tag", "path": "/tags" } },
              { "id": "tag", "component": "Badge", "text": { "@path": "" } }] } }]
        """)).surfaces.single().tree
        assertEquals(listOf("Vegetarian", "One pan"), (tags.at("children") as List<*>).map { it.at("props", "text") })
    }

    @Test
    fun a_stream_too_deep_or_too_wide_is_refused_not_a_crash() {
        fun stream(count: Int, children: (Int) -> List<String>): List<Any?> {
            val components = (0 until count).map { i -> linkedMapOf("id" to (if (i == 0) "root" else "c$i"), "component" to "Card", "children" to children(i)) }
            return listOf(linkedMapOf("version" to "1.0", "createSurface" to linkedMapOf("surfaceId" to "s", "catalogId" to app.catalogId, "dataModel" to emptyMap<String, Any?>(), "components" to components)))
        }
        val deep = stream(20000) { i -> if (i == 19999) emptyList() else listOf("c${i + 1}") }
        assertTrue(OuroA2UI.validateMessages(app, deep).any { it.message == "The tree is more than 24 components deep at c24; nest less." })
        assertFalse(OuroA2UI.resolveMessages(app, deep).valid)
        val wide = stream(20) { i -> if (i == 19) emptyList() else listOf("c${i + 1}", "c${i + 1}") }
        assertEquals(emptyList<A2UIProblem>(), OuroA2UI.validateMessages(app, wide))
        assertEquals("Surface s resolves to more than 5000 components; show fewer, or fewer list items.", OuroA2UI.resolveMessages(app, wide).problems.single().message)
    }

    @Test
    fun keys_and_numbers_read_as_javascript_holds_them() {
        val resolved = OuroA2UI.resolveMessages(app, ComposedJson.parse("""
            [{ "version": "1.0", "createSurface": { "surfaceId": "b", "catalogId": "${app.catalogId}", "components": [{ "id": "root", "component": "Heading", "text": "B", "level": 2.0 }] } },
             { "version": "1.0", "createSurface": { "surfaceId": "1", "catalogId": "${app.catalogId}", "dataModel": { "o": { "b": 1.50, "1": 2 } }, "components": [{ "id": "root", "component": "Text", "text": { "@path": "/o" } }] } }]
        """))
        assertEquals(listOf("1", "b"), resolved.surfaces.map { it.surfaceId })
        assertEquals("""{"surfaces":{"1":{"id":"root","component":"Text","props":{"text":"{\"1\":2,\"b\":1.5}","tone":"default"},"children":[]},"b":{"id":"root","component":"Heading","props":{"text":"B","level":2},"children":[]}}}""", ComposedJson.stringify(resolved.toJson()))
    }

    @Test
    fun a_tool_result_carries_a2ui_messages_and_the_sample_s_a2ui_card_is_its_composed_card() {
        val assets = File("../samples/shop/src/main/assets")
        val shop = ComposedCatalog.parse(File(assets, "shopping.catalog.json").readText())
        val card = (ComposedJson.parse(File(assets, "agent-card.json").readText()) as Map<*, *>).at("structuredContent", "ouro")
        val messages = OuroA2UI.messagesOf(ComposedJson.parse(File(assets, "agent-card-a2ui.json").readText()))
        val surface = OuroA2UI.resolveMessages(shop, messages).surfaces.single()
        assertEquals(ComposedJson.stringify(card.at("tree")), ComposedJson.stringify(surface.tree))
        assertEquals(ComposedJson.stringify(card.at("data")), ComposedJson.stringify(surface.data))
        assertSame(OuroA2UI.NoMessages, OuroA2UI.messagesOf(ComposedJson.parse(File(assets, "agent-card.json").readText())))
        @Suppress("UNCHECKED_CAST")
        val result = OuroA2UI.mcpAppToolResult("A list", shop, messages as List<Any?>)
        assertEquals(shop.catalogId, result.at("structuredContent", "a2ui", "catalogId"))
        assertThrows(IllegalArgumentException::class.java) { OuroA2UI.mcpAppToolResult("x", shop, listOf(mapOf("version" to "1.0"))) }
    }
}
