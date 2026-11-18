// Tree updates (core 0.19.0) against core's own update cases, vendored by SHA-256: each gives a catalog, a tree, its
// data and an update, and expect is the tree and data it gives or each problem. Then the rules core's tests check, and
// how the person's answers carry across an update, as web's ComposedSurface carries them.
package bot.ouro.ui.hx.composed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComposedUpdateTest {
    private val dir = File("../vendor/core/composed")
    private fun read(name: String): Any? = ComposedJson.parse(File(dir, name).readText())
    private val app = ComposedCatalog.fromJson(read("recipe.catalog.json"))
    private val hx = ComposedCatalog.fromJson(read("catalog.json"))
    private val catalogs = mapOf("catalog.json" to hx, "recipe.catalog.json" to app)
    private val updates = File(dir, "updates").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { it.name to (ComposedJson.parse(it.readText()) as Map<*, *>) }
    private val plan = updates.first { it.first == "replace-and-set.json" }.second["tree"]
    private val buttons = ComposedNode.fromJson((((ComposedJson.parse(File(dir, "cases/action-buttons.json").readText()) as Map<*, *>)["expect"] as Map<*, *>)["surfaces"] as Map<*, *>)["buttons"])
    private val shop = buttons.children[1]
    private val share = buttons.children[2]

    private fun heading(id: String, text: String): Map<String, Any?> = linkedMapOf("id" to id, "component" to "Heading", "props" to linkedMapOf("text" to text, "level" to 2L), "children" to emptyList<Any?>())
    private fun card(vararg children: Any?): Map<String, Any?> = linkedMapOf("id" to "root", "component" to "Card", "props" to emptyMap<String, Any?>(), "children" to children.toList())
    private fun problems(r: OuroComposed.Applied): List<TreeProblem> = (r as? OuroComposed.Applied.Problems)?.problems.orEmpty()
    private fun paths(r: OuroComposed.Applied) = problems(r).map { it.path }
    private fun updated(r: OuroComposed.Applied) = r as OuroComposed.Applied.Updated
    private fun json(v: Any?) = ComposedJson.stringify(v)
    private fun props(n: ComposedNode, change: MutableMap<String, Any?>.() -> Unit) = n.copy(props = LinkedHashMap(n.props).apply(change)).toJson()

    @Test
    fun every_update_case_core_gives_ends_as_its_expect_says_and_an_invalid_one_gives_only_problems() {
        assertTrue("core has five update cases", updates.size >= 5)
        for ((name, c) in updates) {
            val cat = catalogs[c["catalog"]] ?: error("$name names an unknown catalog")
            val before = json(c)
            val result = OuroComposed.applyTreeUpdate(cat, c["tree"], c["data"], c["update"])
            val expect = c["expect"] as Map<*, *>
            when (result) {
                is OuroComposed.Applied.Problems -> {
                    assertTrue("$name gave problems, but it isn't an invalid case", name.startsWith("invalid-"))
                    assertEquals(name, json(expect["problems"]), json(result.problems.map { p -> linkedMapOf("path" to p.path, "message" to p.message) }))
                }
                is OuroComposed.Applied.Updated -> {
                    assertTrue("$name updated, but it is an invalid case", !name.startsWith("invalid-"))
                    assertEquals(name, json(expect["tree"]), json(result.tree.toJson()))
                    assertEquals(name, json(expect["data"]), json(result.data))
                    assertEquals(emptyList<TreeProblem>(), OuroComposed.validateTree(cat, result.tree))
                }
            }
            assertEquals("$name left its inputs as they were", before, json(c))
        }
    }

    @Test
    fun an_update_replaces_the_one_node_with_its_id_in_order_and_refuses_an_id_the_tree_lacks_or_instances_share() {
        val tree = card(linkedMapOf("id" to "box", "component" to "Card", "props" to emptyMap<String, Any?>(), "children" to listOf(heading("a", "A"))))
        val nested = OuroComposed.applyTreeUpdate(hx, tree, null, mapOf("nodes" to listOf(linkedMapOf("id" to "box", "component" to "Card", "props" to emptyMap<String, Any?>(), "children" to listOf(heading("b", "B"))), heading("b", "B2"))))
        assertEquals("each node applies to the tree the ones before it left", json(card(linkedMapOf("id" to "box", "component" to "Card", "props" to emptyMap<String, Any?>(), "children" to listOf(heading("b", "B2"))))), json(updated(nested).tree.toJson()))
        assertEquals(null, updated(nested).data)
        assertEquals("a node the update replaced away is no longer in the tree", listOf("/nodes/1/id"), paths(OuroComposed.applyTreeUpdate(hx, tree, null, mapOf("nodes" to listOf(heading("box", "Box"), heading("a", "A2"))))))
        assertEquals("the root is a node like any other", json(card()), json(updated(OuroComposed.applyTreeUpdate(hx, tree, null, mapOf("nodes" to listOf(card())))).tree.toJson()))
        val shared = card(heading("day", "Monday"), heading("day", "Tuesday"))
        assertEquals("\"day\" names 2 nodes, a template's instances; replace the node that holds them, by its own id.", problems(OuroComposed.applyTreeUpdate(hx, shared, null, mapOf("nodes" to listOf(heading("day", "Wednesday"))))).single().message)
        assertEquals("\"c\" is not in the tree; replace a node it has, or the node that should hold this one.", problems(OuroComposed.applyTreeUpdate(hx, tree, null, mapOf("nodes" to listOf(heading("c", "C"))))).single().message)
        for (name in listOf("__proto__", "constructor", "toString")) assertTrue(name, problems(OuroComposed.applyTreeUpdate(hx, tree, null, mapOf("nodes" to listOf(heading(name, "X"))))).single().message.contains("is not in the tree"))
    }

    @Test
    fun an_update_can_t_relabel_a_button_loosen_its_consent_or_forge_its_confirm_sheet_and_a_refused_one_applies_nothing() {
        val tree = card(shop.toJson(), share.toJson())
        assertTrue(paths(OuroComposed.applyTreeUpdate(app, tree, emptyMap<String, Any?>(), mapOf("nodes" to listOf(props(share) { this["press"] = "run" })))).contains("/nodes/0/props/press"))
        val forged = props(share) {
            @Suppress("UNCHECKED_CAST")
            val fields = ((this["confirm"] as Map<*, *>)["fields"] as List<*>).map { LinkedHashMap(it as Map<String, Any?>) }
            fields[1]["value"] = "friend@example.com"
            this["confirm"] = LinkedHashMap(this["confirm"] as Map<*, *>).apply { put("fields", fields) }
        }
        assertTrue(problems(OuroComposed.applyTreeUpdate(app, tree, emptyMap<String, Any?>(), mapOf("nodes" to listOf(forged)))).first().message.contains("confirm sheet shows a row"))
        val relabeled = props(shop) { this["label"] = "Buy now" }
        val mixed = OuroComposed.applyTreeUpdate(app, tree, mapOf("servings" to 4L), mapOf("nodes" to listOf(relabeled), "data" to listOf(mapOf("path" to "/servings", "value" to 6L))))
        assertTrue("the valid data in a refused update doesn't apply either", mixed is OuroComposed.Applied.Problems)
        assertTrue(problems(OuroComposed.applyTreeUpdate(app, tree, emptyMap<String, Any?>(), mapOf("nodes" to listOf(linkedMapOf("id" to "shop", "component" to "Button", "props" to emptyMap<String, Any?>(), "children" to emptyList<Any?>()))))).first().message.contains("not in this catalog"))
        assertEquals("a tree that was already invalid gives no tree", listOf("/children/0/props/label"), paths(OuroComposed.applyTreeUpdate(app, card(relabeled), emptyMap<String, Any?>(), mapOf("data" to listOf(mapOf("path" to "/x", "value" to 1L))))))
    }

    @Test
    fun an_update_s_data_path_stays_in_the_data_a_list_only_at_an_index_and_the_data_model_an_object() {
        for (path in listOf("/__proto__/polluted", "/constructor/prototype/polluted", "/a/__proto__", "servings", "")) {
            assertEquals(path, listOf("/data/0/path"), paths(OuroComposed.applyTreeUpdate(app, plan, emptyMap<String, Any?>(), mapOf("data" to listOf(mapOf("path" to path, "value" to true))))))
        }
        val list = mapOf("steps" to listOf("a", "b"))
        val grown = updated(OuroComposed.applyTreeUpdate(app, plan, list, mapOf("data" to listOf(mapOf("path" to "/steps/2", "value" to "c"), mapOf("path" to "/steps/0", "value" to null)))))
        assertEquals("a list grows at its length, and null takes an item out", """{"steps":["b","c"]}""", json(grown.data))
        assertEquals(json(plan), json(grown.tree.toJson()))
        assertTrue(problems(OuroComposed.applyTreeUpdate(app, plan, list, mapOf("data" to listOf(mapOf("path" to "/steps/5", "value" to "c"))))).single().message.contains("an index from 0 to 2"))
        assertEquals("/steps/first names \"first\" in a list of 2; in a list, a segment is an index from 0 to 2.", problems(OuroComposed.applyTreeUpdate(app, plan, list, mapOf("data" to listOf(mapOf("path" to "/steps/first", "value" to "c"))))).single().message)
        assertEquals("/ is the whole data model", """{"servings":2}""", json(updated(OuroComposed.applyTreeUpdate(app, plan, list, mapOf("data" to listOf(mapOf("path" to "/", "value" to mapOf("servings" to 2L)))))).data))
        assertEquals("The data model is an object; set / only to an object.", problems(OuroComposed.applyTreeUpdate(app, plan, list, mapOf("data" to listOf(mapOf("path" to "/", "value" to 5L))))).single().message)
        assertEquals("a tree with no data model starts one", """{"servings":6}""", json(updated(OuroComposed.applyTreeUpdate(app, plan, null, mapOf("data" to listOf(mapOf("path" to "/servings", "value" to 6L))))).data))
        assertEquals(listOf("/data/0"), paths(OuroComposed.applyTreeUpdate(app, plan, emptyMap<String, Any?>(), mapOf("data" to listOf(mapOf("path" to "/x")))))) 
    }

    @Test
    fun an_update_is_nodes_and_data_json_all_the_way_down_and_shares_nothing_with_its_inputs() {
        assertEquals(listOf("/tree"), paths(OuroComposed.applyTreeUpdate(app, plan, emptyMap<String, Any?>(), mapOf("tree" to plan))))
        assertEquals(listOf(""), paths(OuroComposed.applyTreeUpdate(app, plan, emptyMap<String, Any?>(), null)))
        assertEquals(listOf("/nodes", "/data"), paths(OuroComposed.applyTreeUpdate(app, plan, emptyMap<String, Any?>(), linkedMapOf("nodes" to emptyMap<String, Any?>(), "data" to "x"))))
        val cyclic = LinkedHashMap<String, Any?>().also { it["self"] = it }
        for ((value, path) in listOf(Double.NaN to "/data/0/value", Double.POSITIVE_INFINITY to "/data/0/value", { 1 } to "/data/0/value", mapOf(1 to "x") to "/data/0/value", cyclic to "/data/0/value/self", mapOf("a/b" to Float.NaN) to "/data/0/value/a~1b")) {
            assertEquals(path, listOf(path), paths(OuroComposed.applyTreeUpdate(app, plan, emptyMap<String, Any?>(), mapOf("data" to listOf(mapOf("path" to "/x", "value" to value), mapOf("path" to "/y", "value" to 1L))))))
        }
        val data = linkedMapOf<String, Any?>("servings" to linkedMapOf<String, Any?>("count" to 4L))
        val before = json(data) + json(plan)
        val value = linkedMapOf<String, Any?>("count" to 6L)
        val set = updated(OuroComposed.applyTreeUpdate(app, plan, data, mapOf("nodes" to listOf(heading("title", "New")), "data" to listOf(mapOf("path" to "/servings", "value" to value)))))
        assertEquals("the inputs stay as they were", before, json(data) + json(plan))
        assertTrue("a value the update sets is copied, not shared", (set.data!!["servings"]) !== value)
        assertEquals(emptyList<TreeProblem>(), OuroComposed.treeUpdateProblems(app, mapOf("nodes" to listOf(heading("title", "New")), "data" to listOf(mapOf("path" to "/servings", "value" to null)))))
    }

    @Test
    fun a_tool_result_carries_an_update_for_the_tree_the_view_draws_checked_before_it_applies() {
        val update = mapOf("nodes" to listOf(heading("title", "Lemon orzo for six")), "data" to listOf(mapOf("path" to "/servings", "value" to 6L)))
        val result = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to app.catalogId, "update" to update)))
        assertEquals(OuroComposed.Read.Update(update), OuroComposed.read(app, result))
        val unsafe = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to app.catalogId, "update" to mapOf("data" to listOf(mapOf("path" to "/__proto__/x", "value" to 1L))))))
        assertEquals(listOf("/structuredContent/ouro/update/data/0/path"), (OuroComposed.read(app, unsafe) as OuroComposed.Read.Refused).problems.map { it.path })
        val other = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to hx.catalogId, "update" to update)))
        assertTrue(OuroComposed.read(app, other) is OuroComposed.Read.Refused)
    }

    @Test
    fun what_the_person_entered_stays_across_new_data_except_where_the_agent_changed_it() {
        val start = linkedMapOf<String, Any?>("servings" to 4L, "share" to linkedMapOf("with" to ""), "oven" to "Fan", "note" to null)
        var answers = ComposedAnswers.of(start).write("/servings", 6L).write("/share/with", "sam@example.com").write("/oven", "Grill").write("/note", "no nuts")
        val next = linkedMapOf<String, Any?>("servings" to 4L, "share" to linkedMapOf("with" to "kim@example.com"), "oven" to "Fan", "extra" to true)
        answers = answers.carry(next)
        assertEquals("kept where the agent left the value, and dropped where it changed it or took it away",
            """{"servings":6,"share":{"with":"kim@example.com"},"oven":"Grill","extra":true}""", json(answers.current))
        assertEquals(listOf("/servings", "/oven"), answers.edited.toList())
        assertEquals("a pointer whose value the data doesn't hold differs from null", true, ComposedAnswers.of(mapOf("a" to null)).write("/a", 1L).carry(emptyMap<String, Any?>()).current.let { json(it) == "{}" })
    }

    @Test
    fun an_answer_in_a_list_item_the_agent_changed_is_dropped_even_where_its_own_value_stays_the_same() {
        val start = mapOf("items" to listOf(mapOf("name" to "Basil", "qty" to 1L), mapOf("name" to "Orzo", "qty" to 1L)))
        val answers = ComposedAnswers.of(start).write("/items/1/qty", 3L)
        assertEquals(listOf("/items/1", "/items/1/qty"), ComposedAnswers.anchors(start, "/items/1/qty"))
        val dropped = answers.carry(mapOf("items" to listOf(mapOf("name" to "Orzo", "qty" to 1L))))
        assertEquals("the item at /items/1 is gone, so the answer goes too", """{"items":[{"name":"Orzo","qty":1}]}""", json(dropped.current))
        val moved = answers.carry(mapOf("items" to listOf(mapOf("name" to "Basil", "qty" to 1L), mapOf("name" to "Capers", "qty" to 1L))))
        assertEquals("another item took its place: its qty is the agent's", """{"items":[{"name":"Basil","qty":1},{"name":"Capers","qty":1}]}""", json(moved.current))
        val kept = answers.carry(mapOf("items" to listOf(mapOf("name" to "Lemon", "qty" to 1L), mapOf("name" to "Orzo", "qty" to 1L))))
        assertEquals("only the first item changed", """{"items":[{"name":"Lemon","qty":1},{"name":"Orzo","qty":3}]}""", json(kept.current))
    }
}
