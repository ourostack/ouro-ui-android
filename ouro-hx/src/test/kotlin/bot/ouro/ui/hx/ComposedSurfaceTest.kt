// OuroComposedSurface draws every tree core's valid cases expect, and presses as core's consent classes say: read and
// write at once, consequential only after the person confirms the rows the catalog builds, person-only never (it hands
// the person to the app). Fields write the data model, and a bound button sends what the person entered.
package bot.ouro.ui.hx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import bot.ouro.ui.actions.OuroConsent
import bot.ouro.ui.hx.composed.ComposedCatalog
import bot.ouro.ui.hx.composed.ComposedJson
import bot.ouro.ui.hx.composed.ComposedNode
import bot.ouro.ui.hx.composed.OuroComposed
import bot.ouro.ui.hx.composed.PersonPress
import bot.ouro.ui.hx.composed.TreeProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h4000dp-xxhdpi")
class ComposedSurfaceTest {
    @get:Rule
    val rule = createComposeRule()

    private val dir = File("../vendor/core/composed")
    private fun read(name: String): Any? = ComposedJson.parse(File(dir, name).readText())
    private val app = ComposedCatalog.fromJson(read("recipe.catalog.json"))
    private val hx = ComposedCatalog.fromJson(read("catalog.json"))
    private val cases = File(dir, "cases").listFiles { f -> f.name.endsWith(".json") && !f.name.startsWith("invalid-") }!!.sortedBy { it.name }
        .map { it.name to (ComposedJson.parse(it.readText()) as Map<*, *>) }
    private fun case(name: String) = cases.first { it.first == name }.second
    private fun surfaces(c: Map<*, *>) = (c["expect"] as Map<*, *>)["surfaces"] as Map<*, *>

    /** A case's data model, as its messages leave it: the surface's dataModel, then each update. */
    @Suppress("UNCHECKED_CAST")
    private fun data(c: Map<*, *>, surface: String): Map<String, Any?> {
        var data: Any? = emptyMap<String, Any?>()
        for (m in c["messages"] as List<*>) {
            val create = (m as Map<*, *>)["createSurface"] as? Map<*, *>
            val update = m["updateDataModel"] as? Map<*, *>
            if (create?.get("surfaceId") == surface) data = create["dataModel"] ?: emptyMap<String, Any?>()
            if (update?.get("surfaceId") == surface) data = OuroComposed.setPointer(data, update["path"] as? String ?: "/", update["value"])
        }
        return data as Map<String, Any?>
    }

    private val presses = mutableListOf<PersonPress>()
    private val handoffs = mutableListOf<Pair<String, String>>()
    private val refused = mutableListOf<TreeProblem>()

    /** A picture that is only its description, so no test reaches the network. */
    private val picture: OuroComposedImage = { _, description, m -> Box(m.semantics { contentDescription = description }) }

    private var shown by mutableStateOf<Pair<ComposedNode, Map<String, Any?>>?>(null)
    private var catalog by mutableStateOf<ComposedCatalog?>(null)

    private fun draw(tree: ComposedNode, cat: ComposedCatalog, data: Map<String, Any?> = emptyMap()) {
        if (shown == null) {
            rule.setContent {
                val s = shown
                val k = catalog
                if (s != null && k != null) OuroTheme(dark = false, increasedContrast = false, reduceMotion = false) {
                    OuroComposedSurface(s.first, k, onPress = { presses += it }, onHandoff = { a, how -> handoffs += a to how }, data = s.second, image = picture, onRefused = { refused += it })
                }
            }
        }
        catalog = cat
        shown = tree to data
        rule.waitForIdle()
    }

    private fun exists(words: String) = rule.onAllNodes(hasText(words, substring = true) or hasContentDescription(words, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun button(label: String) = rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

    /** The words a node must put on screen, by component. */
    private fun words(n: ComposedNode): List<String> = when (n.component) {
        "Heading", "Text", "Badge" -> listOf(n.props["text"] as String)
        "Alert" -> listOf(n.props["title"] as String)
        "List" -> (n.props["items"] as List<*>).map { it.toString() }
        "DescriptionList" -> (n.props["items"] as List<*>).flatMap { val m = it as Map<*, *>; listOf(m["term"].toString(), m["detail"].toString()) }
        "Image" -> listOf(n.props["description"] as String)
        "TextField", "Checkbox", "Select", "ActionButton" -> listOf(n.props["label"] as String)
        else -> emptyList()
    }.filter { it.isNotBlank() }

    @Test
    fun draws_every_tree_core_s_valid_cases_expect() {
        var drawn = 0
        for ((name, c) in cases) for ((surface, tree) in surfaces(c)) {
            if (tree == null) continue
            val cat = if (c["catalog"] == "catalog.json") hx else app
            val node = ComposedNode.fromJson(tree)
            draw(node, cat, data(c, surface.toString()))
            assertEquals("$name refused", emptyList<TreeProblem>(), refused)
            fun walk(n: ComposedNode) {
                for (w in words(n)) assertTrue("$name $surface: ${n.component} ${n.id} shows \"$w\"", exists(w))
                n.children.forEach(::walk)
            }
            walk(node)
            drawn++
        }
        assertTrue(drawn >= 6)
    }

    @Test
    fun headings_are_headings_images_are_described_lists_say_their_size_and_controls_are_48_dp() {
        val c = case("recipe-card-agent.json")
        draw(ComposedNode.fromJson(surfaces(c).values.first()), app, data(c, surfaces(c).keys.first().toString()))
        assertTrue(rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val inputs = case("inputs.json")
        draw(ComposedNode.fromJson(surfaces(inputs)["plan"]), app, data(inputs, "plan"))
        val d = rule.density.density
        val controls = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertTrue(controls.size >= 8)
        for (n in controls) assertTrue("${n.config} is ${n.size.width / d}x${n.size.height / d} dp", n.size.width / d >= 48f && n.size.height / d >= 48f)
        assertEquals(3, rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes().size)
        val buttons = ComposedJson.parse("""{"id":"root","component":"Card","props":{},"children":[
            {"id":"pic","component":"Image","props":{"url":"https://example.com/orzo.jpg","description":"A bowl of lemon orzo","aspect":"square"},"children":[]},
            {"id":"steps","component":"List","props":{"items":["Boil","Stir","Serve"],"ordered":true},"children":[]}]}""")
        draw(ComposedNode.fromJson(buttons), hx)
        assertTrue(exists("A bowl of lemon orzo"))
        val list = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNodes().single()
        assertEquals(3, list.config[SemanticsProperties.CollectionInfo].rowCount)
    }

    @Test
    fun a_write_button_presses_at_once_with_the_prepared_input_pressed_by_the_person() {
        draw(ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"]), app)
        button("Add ingredients to list").performClick()
        rule.waitForIdle()
        assertEquals(listOf(PersonPress("add_recipe_to_shopping_list", mapOf("recipe" to "rcp_123", "servings" to 4L), OuroConsent.Write, "Add ingredients to list")), presses)
        assertEquals("person", presses.single().by)
    }

    @Test
    fun a_consequential_button_shows_what_the_agent_prepared_and_presses_only_on_confirm() {
        draw(ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"]), app)
        button("Share recipe").performClick()
        rule.waitForIdle()
        assertEquals(emptyList<PersonPress>(), presses)
        rule.onNodeWithTag("composed.confirm").assertExists()
        val rows = rule.onAllNodes(SemanticsMatcher("row") { it.config.getOrNull(SemanticsProperties.TestTag) == "composed.confirm.row" }).fetchSemanticsNodes()
            .map { n -> n.config[SemanticsProperties.Text].joinToString(" ") { it.text } }
        assertEquals(listOf("Recipe rcp_123", "Send to sam@example.com"), rows)
        rule.onNodeWithTag("composed.confirm.cancel").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("composed.confirm").assertDoesNotExist()
        assertEquals(emptyList<PersonPress>(), presses)
        button("Share recipe").performClick()
        rule.onNodeWithTag("composed.confirm.press").performClick()
        rule.waitForIdle()
        assertEquals(listOf(PersonPress("share_recipe", mapOf("recipe" to "rcp_123", "with" to "sam@example.com"), OuroConsent.Consequential, "Share recipe", confirmed = true)), presses)
    }

    @Test
    fun a_new_tree_closes_an_open_confirm_sheet_so_nothing_the_person_didn_t_see_is_sent() {
        val tree = ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"])
        draw(tree, app)
        button("Share recipe").performClick()
        rule.onNodeWithTag("composed.confirm").assertExists()
        // The agent sends the card again for someone else: a valid button, its rows what the catalog gives for the new input.
        val kim = OuroComposed.actionButtonProps(app, "share_recipe", mapOf("recipe" to "rcp_123", "with" to "kim@example.com"))
        val resent = tree.copy(children = tree.children.map { if (it.id == "share") it.copy(props = it.props + kim) else it })
        draw(resent, app)
        assertEquals(emptyList<TreeProblem>(), refused)
        rule.onNodeWithTag("composed.confirm").assertDoesNotExist()
        assertEquals(emptyList<PersonPress>(), presses)
        button("Share recipe").performClick()
        rule.onNodeWithTag("composed.confirm.press").performClick()
        rule.waitForIdle()
        assertEquals("kim@example.com", presses.single().input["with"])
    }

    @Test
    fun a_tree_whose_confirm_sheet_shows_other_values_than_it_sends_is_refused() {
        val tree = surfaces(case("action-buttons.json"))["buttons"] as Map<*, *>
        val share = ComposedNode.fromJson((tree["children"] as List<*>)[2])
        val forgedConfirm = (share.props["confirm"] as Map<*, *>).toMutableMap().apply { put("fields", listOf(mapOf("label" to "Recipe", "value" to "rcp_123"), mapOf("label" to "Send to", "value" to "your partner"))) }
        val forged = share.copy(props = share.props + ("confirm" to forgedConfirm))
        draw(ComposedNode("root", "Card", emptyMap(), listOf(forged)), app)
        assertTrue(refused.any { it.path == "/children/0/props/confirm/fields" })
        assertTrue(!exists("Share recipe"))
        assertTrue(!exists("your partner"))
    }

    @Test
    fun a_person_only_button_hands_the_person_to_the_app_and_never_presses() {
        draw(ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"]), app)
        button("Order ingredients").performClick()
        rule.waitForIdle()
        assertEquals(listOf("order_ingredients" to "with Order ingredients on the recipe in the app"), handoffs)
        assertEquals(emptyList<PersonPress>(), presses)
        rule.onNodeWithTag("composed.confirm").assertDoesNotExist()
    }

    @Test
    fun fields_write_the_data_model_and_a_bound_button_sends_what_the_person_entered() {
        val c = case("inputs.json")
        draw(ComposedNode.fromJson(surfaces(c)["plan"]), app, data(c, "plan"))
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).performTextReplacement("6")
        rule.onNode(hasSetTextAction() and hasContentDescription("Send to")).performTextReplacement("sam@example.com")
        rule.onNode(isToggleable() and hasContentDescription("Make it vegetarian")).performClick().assertIsOff()
        rule.onNode(isSelectable() and hasText("Grill")).performClick().assertIsSelected()
        rule.waitForIdle()
        button("Add ingredients to list").performClick()
        rule.waitForIdle()
        assertEquals(mapOf("recipe" to "rcp_lemon_orzo", "servings" to 6L), presses.single().input)
        button("Share recipe").performClick()
        rule.waitForIdle()
        assertTrue("the sheet shows what the person typed", exists("sam@example.com"))
        rule.onNodeWithTag("composed.confirm.press").performClick()
        rule.waitForIdle()
        assertEquals(mapOf("recipe" to "rcp_lemon_orzo", "recipeTitle" to "Lemon orzo", "with" to "sam@example.com"), presses.last().input)
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).performTextReplacement("")
        button("Add ingredients to list").performClick()
        rule.waitForIdle()
        assertTrue("an emptied number is sent as null", presses.last().input.containsKey("servings") && presses.last().input["servings"] == null)
    }

    @Test
    fun every_word_and_edge_keeps_its_contrast_on_the_surface_it_sits_on_in_every_theme() {
        var setting by mutableStateOf(LIGHT)
        val drawn = cases.flatMap { (_, c) -> surfaces(c).entries.filter { it.value != null }.map { (k, t) -> Triple(ComposedNode.fromJson(t), if (c["catalog"] == "catalog.json") hx else app, data(c, k.toString())) } }
        rule.setContent {
            OuroTheme(setting.theme, dark = setting.dark, increasedContrast = setting.increasedContrast, reduceMotion = false) {
                androidx.compose.foundation.layout.Column {
                    for ((tree, cat, data) in drawn) OuroComposedSurface(tree, cat, onPress = {}, onHandoff = { _, _ -> }, data = data, image = picture)
                }
            }
        }
        val failures = mutableListOf<String>()
        var checked = 0
        val kinds = mutableSetOf<String>()
        for (theme in bot.ouro.ui.hx.tokens.OuroThemeName.entries) for (base in listOf(LIGHT, DARK, CONTRAST, CONTRAST_DARK)) {
            setting = base.copy(id = "${theme.id}-${base.id}", theme = theme)
            rule.waitForIdle()
            val min = if (setting.increasedContrast) OuroColor.INCREASED_TEXT_CONTRAST else OuroColor.TEXT_CONTRAST
            for (n in rule.onAllNodes(SemanticsMatcher.keyIsDefined(OuroReport), useUnmergedTree = true).fetchSemanticsNodes()) {
                val r = parseOuroReport(n.config[OuroReport])
                val behind = r["behind"] ?: r["surface"] ?: continue
                kinds += r["kind"]!!
                for (k in listOf("label", "title", "text", "description", "error", "term", "details")) r[k]?.let { fg ->
                    val ratio = OuroColor.contrast(hex(fg), hex(behind))
                    checked++
                    if (ratio < min - 0.01) failures += "${setting.id} ${r["kind"]} $k ${"%.2f".format(ratio)}:1"
                }
                r["edge"]?.let { e ->
                    val ratio = OuroColor.contrast(hex(e), hex(behind))
                    checked++
                    if (ratio < OuroColor.INCREASED_LINE_CONTRAST - 0.01) failures += "${setting.id} ${r["kind"]} edge ${"%.2f".format(ratio)}:1"
                }
            }
        }
        assertTrue("every kind the cases draw is checked: $kinds", kinds.containsAll(listOf("button", "badge", "card", "checkbox", "text-field", "composed-heading", "composed-text", "composed-select")))
        assertTrue("checked $checked pairs; failing: ${failures.take(12)}", checked > 1000 && failures.isEmpty())
    }

    @Test
    fun what_a_card_holds_measures_its_contrast_against_the_card_not_the_page() {
        rule.setContent {
            OuroTheme(dark = true, increasedContrast = false, reduceMotion = false) {
                OuroCard {
                    OuroButton("Add", onPress = {}, tone = OuroTone.Secondary, variant = OuroVariant.Soft)
                    OuroCheckbox("Vegetarian", checked = false, onCheckedChange = {})
                    OuroTextField("Servings", value = "", onValueChange = {})
                    OuroDescriptionList(listOf(OuroDescriptionItem("Serves", "4")))
                }
            }
        }
        val card = OuroColor.hex(OuroLook.of(bot.ouro.ui.hx.tokens.OuroTokens.defaultTheme, dark = true).colors.card)
        val reports = rule.onAllNodes(SemanticsMatcher.keyIsDefined(OuroReport), useUnmergedTree = true).fetchSemanticsNodes().map { parseOuroReport(it.config[OuroReport]) }
        for (kind in listOf("checkbox", "text-field", "description-list")) assertEquals(kind, card, reports.single { it["kind"] == kind }.let { it["behind"] ?: it["surface"] })
        assertTrue("a soft button's fill is mixed over the card", reports.single { it["kind"] == "button" }["behind"] != OuroColor.hex(OuroPaint.resolve(OuroLook.of(bot.ouro.ui.hx.tokens.OuroTokens.defaultTheme, dark = true).colors, OuroTone.Secondary, OuroVariant.Soft).fill))
    }

    private fun hex(h: String) = androidx.compose.ui.graphics.Color(h.removePrefix("#").toLong(16).toInt())

    @Test
    fun an_agent_s_update_changes_the_card_and_keeps_what_the_person_entered_except_where_the_agent_changed_it() {
        val c = case("inputs.json")
        val state = OuroComposedState(app)
        rule.setContent {
            OuroTheme(dark = false, increasedContrast = false, reduceMotion = false) {
                OuroComposedSurface(state, onPress = { presses += it }, onHandoff = { a, how -> handoffs += a to how }, image = picture, onRefused = { refused += it })
            }
        }
        fun result(key: String, value: Any?) = mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to app.catalogId, key to value)))
        rule.runOnIdle { state.receive(mapOf("structuredContent" to mapOf("ouro" to mapOf("version" to "1", "catalogId" to app.catalogId, "tree" to surfaces(c)["plan"], "data" to data(c, "plan"))))) }
        val servings = hasSetTextAction() and hasContentDescription("Servings")
        val sendTo = hasSetTextAction() and hasContentDescription("Send to")
        rule.onNode(servings).performTextReplacement("6")
        rule.onNode(sendTo).performTextReplacement("sam@example.com")
        val title = mapOf("id" to "title", "component" to "Heading", "props" to mapOf("text" to "Lemon orzo for six", "level" to 1L), "children" to emptyList<Any?>())
        rule.runOnIdle { assertEquals(emptyList<TreeProblem>(), state.receive(result("update", mapOf("nodes" to listOf(title), "data" to listOf(mapOf("path" to "/share/with", "value" to "kim@example.com")))))) }
        assertTrue(exists("Lemon orzo for six"))
        rule.onNode(servings).assert(hasText("6"))
        rule.onNode(sendTo).assert(hasText("kim@example.com"))
        button("Add ingredients to list").performClick()
        rule.waitForIdle()
        assertEquals("the person's 6 servings survive the agent's update", 6L, presses.single().input["servings"])

        // An update that can't apply: the short alert, nothing of the card, and the agent's problems.
        val missing = mapOf("id" to "subtitle", "component" to "Text", "props" to mapOf("text" to "Serves six", "tone" to "muted"), "children" to emptyList<Any?>())
        rule.runOnIdle { assertEquals(listOf("/nodes/0/id"), state.receive(result("update", mapOf("nodes" to listOf(missing)))).map { it.path }) }
        rule.onNodeWithTag("composed.refused").assertExists()
        assertTrue(!exists("Add ingredients to list"))
        assertTrue(refused.any { it.path == "/nodes/0/id" })
        // A corrected update applies to the last card that did, and what the person typed is still there.
        rule.runOnIdle { assertEquals(emptyList<TreeProblem>(), state.update(mapOf("data" to listOf(mapOf("path" to "/oven", "value" to "Grill"))))) }
        rule.onNodeWithTag("composed.refused").assertDoesNotExist()
        rule.onNode(servings).assert(hasText("6"))
        rule.onNode(isSelectable() and hasText("Grill")).assertIsSelected()
    }

    @Test
    fun an_a2ui_card_is_drawn_as_its_tree_and_a_press_sends_a2ui_s_action_message() {
        val c = case("inputs.json")
        val state = OuroComposedState(app)
        val sent = mutableListOf<Map<String, Any?>>()
        rule.setContent {
            OuroTheme(dark = false, increasedContrast = false, reduceMotion = false) {
                OuroComposedSurface(state, onPress = { presses += it }, onHandoff = { a, how -> handoffs += a to how }, image = picture, onRefused = { refused += it }, onA2UIAction = { sent += it })
            }
        }
        val result = mapOf("structuredContent" to mapOf("a2ui" to mapOf("version" to "1.0", "catalogId" to app.catalogId, "messages" to c["messages"])))
        rule.runOnIdle { assertEquals(emptyList<TreeProblem>(), state.receive(result)) }
        assertEquals("plan", state.a2uiSurfaceId)
        assertTrue(exists("Lemon orzo"))
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).performTextReplacement("6")
        button("Add ingredients to list").performClick()
        rule.waitForIdle()
        val action = sent.single()["action"] as Map<*, *>
        assertEquals("add_recipe_to_shopping_list", action["name"])
        assertEquals("plan", action["surfaceId"])
        assertEquals("shop", action["sourceComponentId"])
        assertEquals(mapOf("recipe" to "rcp_lemon_orzo", "servings" to 6L), action["context"])
        assertTrue("an A2UI card's press goes to onA2UIAction, not onPress", presses.isEmpty())

        // The same stream again keeps what the person typed; a stream that fails the checks shows the short alert.
        rule.runOnIdle { assertEquals(emptyList<TreeProblem>(), state.receive(result)) }
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).assert(hasText("6"))
        val forged = listOf(mapOf("version" to "1.0", "createSurface" to mapOf("surfaceId" to "s", "catalogId" to app.catalogId, "components" to listOf(mapOf("id" to "root", "component" to "Button", "label" to "Pay")))))
        rule.runOnIdle { assertEquals("/0/createSurface/components/0/component", state.receiveA2UI(forged).single().path) }
        rule.onNodeWithTag("composed.refused").assertExists()
        assertTrue(!exists("Pay"))
        assertEquals(null, state.a2uiSurfaceId)
    }

    @Test
    fun new_data_for_the_same_card_keeps_the_person_s_answers_where_the_agent_left_the_value() {
        val c = case("inputs.json")
        val tree = ComposedNode.fromJson(surfaces(c)["plan"])
        draw(tree, app, data(c, "plan"))
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).performTextReplacement("6")
        rule.onNode(isToggleable() and hasContentDescription("Make it vegetarian")).performClick()
        draw(tree, app, data(c, "plan") + ("vegetarian" to true) + ("oven" to "Grill"))
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).assert(hasText("6"))
        rule.onNode(isToggleable() and hasContentDescription("Make it vegetarian")).assertIsOff()
        draw(tree, app, data(c, "plan") + ("servings" to 8L))
        rule.onNode(hasSetTextAction() and hasContentDescription("Servings")).assert(hasText("8"))
    }

    @Test
    fun a_forged_tree_is_refused_and_nothing_of_it_is_drawn() {
        val tree = ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"])
        val shop = tree.children[1]
        val relabeled = tree.copy(children = listOf(shop.copy(props = shop.props + ("label" to "Get a free gift"))))
        draw(relabeled, app)
        assertTrue(refused.any { it.path == "/children/0/props/label" })
        assertTrue(!exists("Get a free gift"))
        rule.onNodeWithTag("composed.refused").assertExists()
        assertTrue("the alert says only that it can't be shown", exists("This card can't be shown"))
    }

    @Test
    fun nothing_in_the_surface_lets_an_agent_press_every_door_is_the_person_s_click() {
        draw(ComposedNode.fromJson(surfaces(case("action-buttons.json"))["buttons"]), app)
        val doors = rule.onAllNodes(SemanticsMatcher.keyIsDefined(OuroActionKey)).fetchSemanticsNodes()
        assertEquals(setOf("get_recipe;read", "add_recipe_to_shopping_list;write", "share_recipe;consequential", "order_ingredients;person-only"), doors.map { it.config[OuroActionKey] }.toSet())
        for (n in doors) {
            assertEquals(Role.Button, n.config.getOrNull(SemanticsProperties.Role))
            assertTrue("no custom accessibility action for ${n.config[OuroActionKey]}", n.config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty())
        }
        for (label in listOf("Open recipe", "Add ingredients to list")) button(label).performClick()
        rule.waitForIdle()
        assertTrue(presses.size == 2 && presses.all { it.by == "person" })
        assertTrue("an agent's own door is its tool, not the screen", OuroComposed.agentPressProblem(app, "share_recipe").message.contains("through their own tool"))
    }
}
