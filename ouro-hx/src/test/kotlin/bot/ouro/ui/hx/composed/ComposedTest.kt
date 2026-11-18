// The composed tree's port against core's own cases (vendored by SHA-256 from ouro-ui-core v0.18.0): every tree a
// valid case expects decodes and validates, and every forged tree core's tests reject is rejected here, for the same
// reason. Then the data model, the confirm sheet and the press, as test/composed.test.mts checks them on web.
package bot.ouro.ui.hx.composed

import bot.ouro.ui.actions.OuroConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComposedTest {
    private val dir = File("../vendor/core/composed")
    private fun read(name: String): Any? = ComposedJson.parse(File(dir, name).readText())
    private val app = ComposedCatalog.fromJson(read("recipe.catalog.json"))
    private val hx = ComposedCatalog.fromJson(read("catalog.json"))
    private val catalogs = mapOf("catalog.json" to hx, "recipe.catalog.json" to app)
    private val cases = File(dir, "cases").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { it.name to (ComposedJson.parse(it.readText()) as Map<*, *>) }

    private fun surfaces(case: Map<*, *>): Map<*, *> = (case["expect"] as Map<*, *>)["surfaces"] as Map<*, *>
    private fun case(name: String) = cases.first { it.first == name }.second
    private val buttons = ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"])
    private val open = buttons.children[0]
    private val shop = buttons.children[1]
    private val share = buttons.children[2]
    private val order = buttons.children[3]

    private fun card(vararg children: Any?): Map<String, Any?> = linkedMapOf("id" to "root", "component" to "Card", "props" to emptyMap<String, Any?>(), "children" to children.toList())
    private fun node(component: String, props: Map<String, Any?>, children: List<Any?> = emptyList()): Map<String, Any?> =
        linkedMapOf("id" to "root", "component" to component, "props" to props, "children" to children)
    private fun withKey(m: Any?, key: String, value: Any?): Map<String, Any?> =
        LinkedHashMap<String, Any?>().apply { (m as Map<*, *>).forEach { (k, v) -> put(k.toString(), v) }; put(key, value) }
    private fun forge(button: ComposedNode, change: MutableMap<String, Any?>.() -> Unit): Map<String, Any?> =
        button.toJson().toMutableMap().also { it["props"] = LinkedHashMap(button.props).apply(change) }
    private fun first(problems: List<TreeProblem>): TreeProblem {
        assertTrue("expected a problem, got none", problems.isNotEmpty())
        return problems.first()
    }
    private fun find(n: ComposedNode, id: String): ComposedNode? = if (n.id == id) n else n.children.firstNotNullOfOrNull { find(it, id) }

    @Test
    fun every_tree_a_valid_case_expects_decodes_and_passes_the_validator() {
        val valid = cases.filter { !it.first.startsWith("invalid-") }
        assertTrue("core has at least six valid cases", valid.size >= 6)
        var trees = 0
        for ((name, c) in valid) for ((id, tree) in surfaces(c)) {
            if (tree == null) continue
            val cat = catalogs[c["catalog"]] ?: error("$name names an unknown catalog ${c["catalog"]}")
            assertEquals("$name surface $id", emptyList<TreeProblem>(), OuroComposed.validateTree(cat, tree))
            val decoded = ComposedNode.fromJson(tree)
            assertEquals("$name surface $id reads back as it was written", ComposedJson.stringify(tree), ComposedJson.stringify(decoded.toJson()))
            assertEquals(emptyList<TreeProblem>(), OuroComposed.validateTree(cat, decoded))
            trees++
        }
        assertTrue(trees >= 6)
    }

    @Test
    fun every_button_in_the_cases_is_what_the_catalog_says_its_action_s_button_is() {
        for ((name, c) in cases.filter { !it.first.startsWith("invalid-") }) for (tree in surfaces(c).values) {
            if (tree == null) continue
            fun walk(n: ComposedNode) {
                if (n.component == "ActionButton") {
                    @Suppress("UNCHECKED_CAST")
                    val expected = OuroComposed.actionButtonProps(catalogs[c["catalog"]]!!, n.props["action"] as String, n.props["input"] as Map<String, Any?>)
                    for (key in listOf("label", "consent", "press", "handoff", "confirm")) {
                        assertEquals("$name ${n.id} $key", ComposedJson.stringify(expected[key]), ComposedJson.stringify(n.props[key]))
                    }
                }
                n.children.forEach(::walk)
            }
            walk(ComposedNode.fromJson(tree))
        }
    }

    @Test
    fun a_tree_can_t_relabel_a_button_loosen_its_consent_or_drop_its_confirm_sheet() {
        val relabeled = OuroComposed.validateTree(app, card(forge(share) { this["label"] = "Add to list" }))
        assertEquals("share_recipe's label is the catalog's \"Share recipe\", never the sender's.", first(relabeled).message)
        assertEquals("/children/0/props/label", relabeled.first().path)
        val loosened = OuroComposed.validateTree(app, card(forge(share) { this["press"] = "run" }))
        assertTrue(loosened.any { it.path == "/children/0/props/press" })
        val consent = OuroComposed.validateTree(app, card(forge(share) { this["consent"] = "write" }))
        assertTrue(consent.any { it.path == "/children/0/props/consent" })
        val unconfirmed = OuroComposed.validateTree(app, card(forge(share) { remove("confirm") }))
        assertEquals("share_recipe confirms, with the catalog's title and description and a row per prepared input.", first(unconfirmed).message)
        val retitled = OuroComposed.validateTree(app, card(forge(share) { this["confirm"] = withKey(this["confirm"], "title", "Just a look") }))
        assertEquals("/children/0/props/confirm", first(retitled).path)
        val confirmsWrite = OuroComposed.validateTree(app, card(forge(shop) { this["confirm"] = share.props["confirm"] }))
        assertEquals("add_recipe_to_shopping_list doesn't confirm; only a consequential action does.", first(confirmsWrite).message)
        val noHandoff = OuroComposed.validateTree(app, card(forge(order) { remove("handoff") }))
        assertEquals("/children/0/props/handoff", first(noHandoff).path)
        val unknown = OuroComposed.validateTree(app, card(forge(open) { this["action"] = "pay" }))
        assertTrue(first(unknown).message, first(unknown).message.startsWith("\"pay\" is not one of the app's actions: get_recipe,"))
    }

    @Test
    fun input_on_a_person_only_action_is_refused_with_core_s_typed_refusal() {
        val problems = OuroComposed.validateTree(app, card(forge(order) { this["input"] = mapOf("recipe" to "rcp_123") }))
        val refusal = first(problems).refusal
        assertNotNull(refusal)
        assertEquals("person-only", (refusal!!["error"] as Map<*, *>)["code"])
        assertEquals("/children/0/props/input", problems.first().path)
        assertTrue(problems.first().message.startsWith("Order ingredients: You can't place an order or pay for the person"))
    }

    @Test
    fun a_tree_names_only_the_catalog_s_components_and_their_props_resolved() {
        assertTrue(first(OuroComposed.validateTree(hx, node("Button", emptyMap()))).message.startsWith("\"Button\" is not in this catalog; use one of Card, Stack,"))
        assertEquals("text is text, got {\"@path\":\"/x\"}.", first(OuroComposed.validateTree(hx, node("Text", mapOf("text" to mapOf("@path" to "/x"), "tone" to "default")))).message)
        assertEquals("tone is one of neutral, info, success, warning, danger, got \"loud\".", first(OuroComposed.validateTree(hx, node("Badge", mapOf("text" to "Hi", "tone" to "loud")))).message)
        assertEquals("Text has no colour.", first(OuroComposed.validateTree(hx, node("Text", mapOf("text" to "Hi", "tone" to "default", "colour" to "red")))).message)
        assertTrue(first(OuroComposed.validateTree(hx, node("Image", mapOf("url" to "https://x/y.jpg", "aspect" to "square")))).message.startsWith("Image needs description"))
        assertEquals("Separator has no children.", first(OuroComposed.validateTree(hx, node("Separator", emptyMap(), listOf(node("Separator", emptyMap()))))).message)
        assertEquals("level is 1, 2 or 3, got 4.", first(OuroComposed.validateTree(hx, node("Heading", mapOf("text" to "Hi", "level" to 4L)))).message)
        assertEquals("A node is { id, component, props, children }.", first(OuroComposed.validateTree(hx, mapOf("id" to "root", "component" to "Card"))).message)
        val button = linkedMapOf("id" to "a", "component" to "ActionButton", "props" to emptyMap<String, Any?>(), "children" to emptyList<Any?>())
        assertTrue("HX's own catalog has no actions, so no button", first(OuroComposed.validateTree(hx, card(button))).message.contains("not in this catalog"))
        assertEquals(OuroComposed.COMPONENTS.keys, OuroComposed.componentsFor(emptyMap()).keys)
        assertEquals(OuroComposed.COMPONENTS.keys + "ActionButton", app.components.keys)
    }

    @Test
    fun a_field_s_value_is_a_pointer_into_the_data_and_a_bound_input_is_one_too() {
        fun field(extra: Map<String, Any?>) = card(linkedMapOf("id" to "f", "component" to "TextField", "props" to mapOf<String, Any?>("label" to "Servings", "type" to "text", "multiline" to false) + extra, "children" to emptyList<Any?>()))
        assertEquals(emptyList<TreeProblem>(), OuroComposed.validateTree(hx, field(mapOf("value" to "/servings"))))
        assertTrue(first(OuroComposed.validateTree(hx, field(mapOf("value" to "servings")))).message.startsWith("value is a JSON pointer into the data model"))
        assertTrue(first(OuroComposed.validateTree(hx, field(emptyMap()))).message.startsWith("TextField needs value"))
        val bound = OuroComposed.validateTree(app, card(forge(shop) { this["input"] = mapOf("recipe" to "rcp_123", "servings" to mapOf("@path" to "servings")) }))
        assertEquals("servings is a value, or {\"@path\": \"/...\"} bound to the data model.", first(bound).message)
    }

    @Test
    fun a_catalog_can_t_widen_hx_or_loosen_an_action() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            ComposedCatalog.fromJson(mapOf("catalogId" to "x", "components" to mapOf("Card" to emptyMap<String, Any?>(), "Video" to emptyMap<String, Any?>())))
        }
        assertEquals("catalog x: component Video is not Ouro HX's. Fix: regenerate it with ouro-a2ui catalog.", e.message)
        val loose = mapOf("catalogId" to "x", "components" to mapOf("ActionButton" to emptyMap<String, Any?>()), "metadata" to mapOf("extensions" to mapOf("bot_ouroboros_hx" to mapOf("actions" to mapOf("pay" to mapOf("consent" to "consequential", "press" to "run", "label" to "Pay", "description" to "Pay.", "input" to emptyMap<String, Any?>()))))))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { ComposedCatalog.fromJson(loose) }.message!!.contains("presses run, but a consequential action presses confirm"))
    }

    @Test
    fun a_press_is_the_action_the_prepared_input_and_that_the_person_pressed_it_a_confirm_only_after_they_confirm() {
        val press = OuroComposed.personPress(shop)
        assertEquals(PersonPress("add_recipe_to_shopping_list", mapOf("recipe" to "rcp_123", "servings" to 4L), OuroConsent.Write, "Add ingredients to list"), press)
        assertEquals("""{"action":"add_recipe_to_shopping_list","input":{"recipe":"rcp_123","servings":4},"consent":"write","label":"Add ingredients to list","by":"person"}""", ComposedJson.stringify(press.toJson()))
        assertEquals("share_recipe is consequential; send its action only after the person confirms.", assertThrows(OuroComposed.PressRefused::class.java) { OuroComposed.personPress(share) }.message)
        assertTrue(OuroComposed.personPress(share, confirmed = true).confirmed)
        assertEquals(true, OuroComposed.personPress(share, confirmed = true).toJson()["confirmed"])
        assertTrue(assertThrows(OuroComposed.PressRefused::class.java) { OuroComposed.personPress(order, confirmed = true) }.message!!.contains("person-only"))
    }

    @Test
    fun a_field_writes_the_person_s_answer_into_the_data_and_a_button_bound_to_it_sends_that_answer() {
        val c = case("inputs.json")
        val plan = ComposedNode.fromJson(surfaces(c)["plan"])
        for (name in OuroComposed.INPUT_COMPONENTS) assertTrue("the case covers $name", ComposedJson.stringify(plan.toJson()).contains("\"component\":\"$name\""))
        val shopBound = find(plan, "shop")!!
        val shareBound = find(plan, "share")!!
        var data = (((c["messages"] as List<*>)[0] as Map<*, *>)["createSurface"] as Map<*, *>)["dataModel"]
        assertEquals("a field starts with what the data holds", 4L, OuroComposed.getPointer(data, find(plan, "servings")!!.props["value"] as String))
        data = OuroComposed.setPointer(data, "/servings", 6L)
        data = OuroComposed.setPointer(data, "/share/with", "sam@example.com")
        assertEquals(mapOf("recipe" to "rcp_lemon_orzo", "servings" to 6L), OuroComposed.personPress(shopBound, data = data).input)
        val sheet = (OuroComposed.resolveButton(shareBound, data).props["confirm"] as Map<*, *>)["fields"]
        assertEquals("""[{"label":"Recipe","value":"Lemon orzo","raw":"rcp_lemon_orzo"},{"label":"Send to","value":"sam@example.com"}]""", ComposedJson.stringify(sheet))
        assertEquals("sam@example.com", OuroComposed.personPress(shareBound, confirmed = true, data = data).input["with"])
        val emptied = OuroComposed.personPress(shareBound, confirmed = true, data = OuroComposed.setPointer(data, "/share/with", null))
        assertTrue("an empty answer is sent as null", emptied.input.containsKey("with") && emptied.input["with"] == null)
        assertSame("a node with nothing bound comes back as it is", find(plan, "title"), OuroComposed.resolveButton(find(plan, "title")!!, data))
    }

    @Test
    fun a_confirm_sheet_names_an_id_by_the_app_s_word_then_the_agent_s_then_the_id() {
        val input = mapOf("recipe" to "rcp_1", "recipeTitle" to "Lemon orzo", "with" to "sam@example.com")
        fun rows(label: ((LabelContext) -> String?)?) = ComposedJson.stringify((OuroComposed.actionButtonProps(app, "share_recipe", input, label)["confirm"] as Map<*, *>)["fields"])
        assertEquals("""[{"label":"Recipe","value":"Lemon orzo","raw":"rcp_1"},{"label":"Send to","value":"sam@example.com"}]""", rows(null))
        assertEquals("""[{"label":"Recipe","value":"My orzo","raw":"rcp_1"},{"label":"Send to","value":"sam@example.com"}]""", rows { if (it.field == "recipe") "My orzo" else null })
        assertEquals("person-only buttons carry no input", emptyMap<String, Any?>(), OuroComposed.actionButtonProps(app, "order_ingredients", mapOf("recipe" to "rcp_123"))["input"])
    }

    @Test
    fun a_confirm_sheet_can_t_show_the_person_one_value_and_send_another() {
        @Suppress("UNCHECKED_CAST")
        fun rows(edit: MutableList<MutableMap<String, Any?>>.() -> Unit) = card(forge(share) {
            val fields = ((this["confirm"] as Map<*, *>)["fields"] as List<*>).map { LinkedHashMap(it as Map<String, Any?>) }.toMutableList<MutableMap<String, Any?>>()
            fields.edit()
            this["confirm"] = withKey(this["confirm"], "fields", fields)
        })
        val sheet = "share_recipe's confirm sheet shows a row for each prepared input"
        val forgedValue = OuroComposed.validateTree(app, rows { this[lastIndex]["value"] = "friend@example.com" })
        assertTrue(first(forgedValue).message.startsWith(sheet))
        assertEquals("/children/0/props/confirm/fields", forgedValue.first().path)
        assertTrue(first(OuroComposed.validateTree(app, rows { removeAt(lastIndex) })).message.startsWith(sheet))
        assertTrue(first(OuroComposed.validateTree(app, rows { this[0]["label"] = "Dinner" })).message.startsWith(sheet))
        assertEquals("the app's own words for an id may differ, beside the id itself", emptyList<TreeProblem>(), OuroComposed.validateTree(app, rows { this[0]["raw"] = this[0]["value"]; this[0]["value"] = "My lemon orzo" }))
        assertTrue("a field that is no id can't show other words with the real value beside them", first(OuroComposed.validateTree(app, rows { this[lastIndex]["raw"] = this[lastIndex]["value"]; this[lastIndex]["value"] = "friend@example.com" })).message.startsWith(sheet))
        assertTrue("an id's name can't stand beside a different id", first(OuroComposed.validateTree(app, rows { this[0]["raw"] = "rcp_evil"; this[0]["value"] = "Lemon orzo" })).message.startsWith(sheet))
        val extra = OuroComposed.validateTree(app, card(forge(share) { this["input"] = withKey(this["input"], "everyone", true) }))
        assertEquals("share_recipe has no input everyone; its fields are recipe, recipeTitle, with.", first(extra).message)
        assertEquals("/children/0/props/input/everyone", extra.first().path)
        // Core 0.18.1 checks with JavaScript's `in`, which lets prototype names through (ouro-ui-core#91); the catalog's own keys decide here.
        for (k in listOf("constructor", "toString", "__proto__")) assertTrue(k, OuroComposed.validateTree(app, card(forge(share) { this["input"] = withKey(this["input"], k, "x") })).any { it.path == "/children/0/props/input/$k" })
        val plan = ComposedNode.fromJson(surfaces(case("inputs.json"))["plan"])
        val bound = find(plan, "share")!!
        @Suppress("UNCHECKED_CAST")
        val rebound = bound.toJson().toMutableMap().also { n ->
            n["props"] = LinkedHashMap(bound.props).apply {
                val fields = ((this["confirm"] as Map<*, *>)["fields"] as List<*>).map { LinkedHashMap(it as Map<String, Any?>) }
                fields[fields.lastIndex]["bind"] = "/somewhere/else"
                this["confirm"] = withKey(this["confirm"], "fields", fields)
            }
        }
        assertTrue("a bound row reads the same binding the input does", first(OuroComposed.validateTree(app, card(rebound))).message.startsWith(sheet))
    }

    @Test
    fun a_name_a_sender_chooses_is_the_catalog_s_only_when_the_catalog_itself_has_it() {
        for (name in listOf("constructor", "toString", "__proto__", "hasOwnProperty")) {
            val extra = card(forge(share) { this["input"] = linkedMapOf("recipe" to "rcp_123", "with" to "sam@example.com", name to true) })
            assertTrue("input $name", OuroComposed.validateTree(app, extra).any { it.message.startsWith("share_recipe has no input $name") })
            assertTrue("component $name", first(OuroComposed.validateTree(app, card(linkedMapOf("id" to "x", "component" to name, "props" to emptyMap<String, Any?>(), "children" to emptyList<Any?>())))).message.contains("is not in this catalog"))
            assertTrue("action $name", first(OuroComposed.validateTree(app, card(forge(open) { this["action"] = name }))).message.contains("not one of the app's actions"))
            val e = assertThrows(IllegalArgumentException::class.java) { ComposedCatalog.fromJson(mapOf("catalogId" to "x", "components" to mapOf("Text" to emptyMap<String, Any?>(), name to emptyMap<String, Any?>()))) }
            assertEquals("catalog x: component $name is not Ouro HX's. Fix: regenerate it with ouro-a2ui catalog.", e.message)
        }
    }

    @Test
    fun a_pointer_never_names_a_prototype_writes_through_one_write_nothing_and_the_validator_refuses_it() {
        for (p in listOf("/__proto__/polluted", "/constructor/prototype/polluted", "/a/__proto__/toString", "/a/prototype")) {
            assertEquals(p, """{"a":{}}""", ComposedJson.stringify(OuroComposed.setPointer(mapOf("a" to emptyMap<String, Any?>()), p, "yes")))
            assertEquals(p, """{"a":{}}""", ComposedJson.stringify(OuroComposed.setPointer(mapOf("a" to emptyMap<String, Any?>()), p, null)))
            assertEquals(p, false, OuroComposed.isDataPointer(p))
        }
        for (p in listOf("constructor", "items/__proto__")) assertEquals("relative $p", false, OuroComposed.isSafePointer(p))
        assertEquals(true, OuroComposed.isDataPointer("/recipe/title"))
        assertEquals(null, OuroComposed.getPointer(mapOf("a" to 1L), "/toString"))
        val field = card(linkedMapOf("id" to "f", "component" to "TextField", "props" to mapOf("label" to "X", "value" to "/__proto__/x", "type" to "text", "multiline" to false), "children" to emptyList<Any?>()))
        assertTrue(first(OuroComposed.validateTree(hx, field)).message.startsWith("value is a JSON pointer into the data model"))
        val bound = OuroComposed.validateTree(app, card(forge(shop) { this["input"] = mapOf("recipe" to "rcp_123", "servings" to mapOf("@path" to "/__proto__/servings")) }))
        assertEquals("servings is a value, or {\"@path\": \"/...\"} bound to the data model.", first(bound).message)
    }

    @Test
    fun the_app_s_resolver_names_only_id_fields_every_other_row_shows_what_will_be_sent() {
        val input = mapOf("recipe" to "rcp_1", "with" to "sam@example.com")
        val asked = mutableListOf<String>()
        val fields = (OuroComposed.actionButtonProps(app, "share_recipe", input, label = { asked += it.field; "Somebody else" })["confirm"] as Map<*, *>)["fields"]
        assertEquals("""[{"label":"Recipe","value":"Somebody else","raw":"rcp_1"},{"label":"Send to","value":"sam@example.com"}]""", ComposedJson.stringify(fields))
        assertEquals(listOf("recipe"), asked)
    }

    @Test
    fun a_confirm_sheet_s_rows_come_from_the_catalog_and_the_input_never_from_the_tree() {
        // The validator refuses forged rows (core 0.18.1); the sheet is still built from the catalog and the input, so it shows what is sent.
        val forged = ComposedNode.fromJson(forge(share) {
            this["confirm"] = withKey(this["confirm"], "fields", listOf(mapOf("label" to "Send to", "value" to "your partner")))
        })
        val sheet = OuroComposed.confirmSheet(app, forged, emptyMap<String, Any?>())!!
        assertEquals(OuroComposed.ConfirmSheet("Share recipe", "Send this recipe to someone.", listOf(OuroComposed.ConfirmRow("Recipe", "rcp_123"), OuroComposed.ConfirmRow("Send to", "sam@example.com"))), sheet)
        val plan = ComposedNode.fromJson(surfaces(case("inputs.json"))["plan"])
        val bound = OuroComposed.confirmSheet(app, find(plan, "share")!!, mapOf("share" to mapOf("with" to "kim@example.com")), label = { if (it.field == "recipe") "Our orzo" else null })!!
        assertEquals(listOf(OuroComposed.ConfirmRow("Recipe", "Our orzo", "rcp_lemon_orzo"), OuroComposed.ConfirmRow("Send to", "kim@example.com")), bound.rows)
        assertEquals(null, OuroComposed.confirmSheet(app, shop, emptyMap<String, Any?>()))
    }

    @Test
    fun a_pointer_past_the_end_of_a_list_or_in_other_digits_writes_nowhere() {
        val data = mapOf("a" to listOf(1L))
        assertEquals("""{"a":[1]}""", ComposedJson.stringify(OuroComposed.setPointer(data, "/a/2000000000", "x")))
        assertEquals("""{"a":[1,"x"]}""", ComposedJson.stringify(OuroComposed.setPointer(data, "/a/1", "x")))
        assertEquals("""{"a":[1]}""", ComposedJson.stringify(OuroComposed.setPointer(data, "/a/\u0661", "x")))
        assertEquals(null, OuroComposed.getPointer(mapOf("a" to listOf(1L, 2L)), "/a/\u0661"))
    }

    @Test
    fun json_nested_too_deep_and_trees_nested_too_deep_are_refused_not_a_crash() {
        assertThrows(IllegalArgumentException::class.java) { ComposedJson.parse("[".repeat(10_000) + "]".repeat(10_000)) }
        var deep: Map<String, Any?> = linkedMapOf("id" to "s", "component" to "Separator", "props" to emptyMap<String, Any?>(), "children" to emptyList<Any?>())
        repeat(OuroComposed.MAX_DEPTH + 1) { deep = card(deep) }
        assertTrue(OuroComposed.validateTree(hx, deep).any { it.message.startsWith("The tree nests more than") })
    }

    @Test
    fun pointers_read_and_write_as_rfc_6901_says_and_leave_the_data_as_it_was() {
        val data = ComposedJson.parse("""{"a/b":{"m~n":1},"list":[10,20,30],"x":{"y":null}}""")
        assertEquals(1L, OuroComposed.getPointer(data, "/a~1b/m~0n"))
        assertEquals(20L, OuroComposed.getPointer(data, "/list/1"))
        assertEquals(null, OuroComposed.getPointer(data, "/list/9"))
        assertEquals(null, OuroComposed.getPointer(data, "/x/y/z"))
        assertSame(data, OuroComposed.getPointer(data, ""))
        val written = OuroComposed.setPointer(data, "/new/deep", "v")
        assertEquals("v", OuroComposed.getPointer(written, "/new/deep"))
        assertEquals("the original is untouched", null, OuroComposed.getPointer(data, "/new"))
        assertEquals("""[10,30]""", ComposedJson.stringify(OuroComposed.getPointer(OuroComposed.setPointer(data, "/list/1", null), "/list")))
        assertEquals("""{"m~n":1}""", ComposedJson.stringify(OuroComposed.getPointer(OuroComposed.setPointer(data, "/x", null), "/a~1b")))
        assertEquals(false, (OuroComposed.setPointer(data, "/x", null) as Map<*, *>).containsKey("x"))
        assertEquals(emptyMap<String, Any?>(), OuroComposed.setPointer(data, "", null))
        assertEquals("4", OuroComposed.asText(4.0))
        assertEquals("2.5", OuroComposed.asText(2.5))
        assertEquals("", OuroComposed.asText(null))
        assertEquals("""{"a":[1,true]}""", OuroComposed.asText(mapOf("a" to listOf(1L, true))))
    }

    @Test
    fun a_tool_result_carries_ouro_s_tree_itself_validated_and_a2ui_is_not_read_here() {
        val tree = card(shop.toJson())
        val result = mapOf("content" to listOf(mapOf("type" to "text", "text" to "Lemon orzo")), "structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to app.catalogId, "tree" to tree, "data" to mapOf("servings" to 4L))))
        val ready = OuroComposed.read(app, result) as OuroComposed.Read.Ready
        assertEquals(shop, ready.tree.children.single())
        assertEquals(mapOf("servings" to 4L), ready.data)
        val forged = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to app.catalogId, "tree" to card(forge(share) { this["press"] = "run" }))))
        assertTrue((OuroComposed.read(app, forged) as OuroComposed.Read.Refused).problems.any { it.path == "/structuredContent/ouro/tree/children/0/props/press" })
        val a2ui = mapOf("structuredContent" to mapOf("a2ui" to mapOf("version" to "1.0", "messages" to emptyList<Any?>())))
        assertTrue((OuroComposed.read(app, a2ui) as OuroComposed.Read.Refused).problems.single().message.contains("A2UI"))
        val other = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to hx.catalogId, "tree" to tree)))
        assertEquals("This renderer has only the catalog ${app.catalogId}.", (OuroComposed.read(app, other) as OuroComposed.Read.Refused).problems.single().message)
        val later = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "2", "catalogId" to app.catalogId, "tree" to tree)))
        assertTrue(OuroComposed.read(app, later) is OuroComposed.Read.Refused)
    }

    @Test
    fun an_agent_can_t_press_through_the_screen_it_uses_its_own_door() {
        assertEquals("share_recipe is an action, and agents run actions through their own tool (MCP share_recipe), not through the person's screen.", OuroComposed.agentPressProblem(app, "share_recipe").message)
        assertEquals("person-only", (OuroComposed.agentPressProblem(app, "order_ingredients").refusal!!["error"] as Map<*, *>)["code"])
        assertEquals("This catalog has no functions an agent may call, so pay is not one.", OuroComposed.agentPressProblem(app, "pay").message)
    }

    @Test
    fun numbers_read_as_javascript_writes_them() {
        val cases = listOf(4.0 to "4", 2.5 to "2.5", 12345678.5 to "12345678.5", 0.00015 to "0.00015", 1.5e-5 to "0.000015", 1e-7 to "1e-7", 1e20 to "100000000000000000000", 1e21 to "1e+21", 1.5e22 to "1.5e+22", -0.5 to "-0.5", 0.1 to "0.1")
        for ((n, text) in cases) assertEquals(text, ComposedJson.number(n))
        assertEquals("100000000000000000000", ComposedJson.stringify(ComposedJson.parse("100000000000000000000")))
    }

    @Test
    fun json_reads_and_writes_as_javascript_does() {
        val text = """{"s":"a\"b\\c\nd\u00e9","n":[0,-1,2.5,1e3,12345678901],"b":[true,false,null],"o":{}}"""
        val v = ComposedJson.parse(text)
        assertEquals("a\"b\\c\nd\u00e9", (v as Map<*, *>)["s"])
        assertEquals(listOf(0L, -1L, 2.5, 1000.0, 12345678901L), v["n"])
        assertEquals("""{"s":"a\"b\\c\nd${"\u00e9"}","n":[0,-1,2.5,1000,12345678901],"b":[true,false,null],"o":{}}""", ComposedJson.stringify(v))
        assertThrows(IllegalArgumentException::class.java) { ComposedJson.parse("{\"a\":1,}") }
        assertThrows(IllegalArgumentException::class.java) { ComposedJson.parse("[1] 2") }
    }
}
