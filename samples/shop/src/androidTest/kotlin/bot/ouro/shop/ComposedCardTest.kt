// The composed card on Android, on CI's emulator: the card an agent composed (assets/agent-card.json) is drawn from the
// app's own catalog, and each of its buttons is the person's door to a declared action. Add runs the same handler the
// shop's own button and its AppFunction run; Share list asks the person first; Place order never runs from the card and
// takes the person to the list instead.
package bot.ouro.shop

import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import bot.ouro.shop.actions.OuroActions
import bot.ouro.ui.hx.OuroActionKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposedCardTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun open() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("screen", "composed").putExtra("dark", false)
        scenario = ActivityScenario.launch(intent)
        rule.waitForIdle()
    }

    @After
    fun close() = scenario.close()

    /** The card scrolls on a phone: bring the button into view before pressing it. */
    private fun button(label: String) = rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performScrollTo()

    @Test
    fun the_card_s_buttons_are_the_app_s_declared_actions_with_their_consent() {
        rule.onNodeWithTag("composed.refused").assertDoesNotExist()
        val doors = rule.onAllNodes(SemanticsMatcher.keyIsDefined(OuroActionKey)).fetchSemanticsNodes().map { it.config[OuroActionKey].split(";") }
        assertEquals(OuroActions.set.actions.map { it.id }.toSet(), doors.map { it[0] }.toSet())
        for ((id, consent) in doors) assertEquals(id, OuroActions.set[id]!!.consent.wire, consent)
        for (n in rule.onAllNodes(SemanticsMatcher.keyIsDefined(OuroActionKey)).fetchSemanticsNodes()) assertEquals(Role.Button, n.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun adding_from_the_card_runs_the_same_handler_with_what_the_person_typed() {
        val before = Shop.items.size
        rule.onNode(hasSetTextAction() and hasContentDescription("Item")).performTextReplacement("Capers")
        rule.onNode(hasSetTextAction() and hasContentDescription("Quantity")).performTextReplacement("2")
        button("Add to list").performClick()
        rule.waitUntil(10_000) { Shop.items.size == before + 1 }
        assertEquals(ListItem(Shop.items.last().id, "Capers", 2.0), Shop.items.last())
    }

    @Test
    fun sharing_from_the_card_shows_who_and_runs_only_when_the_person_confirms() {
        val before = Shop.sharedWith.size
        rule.onNode(hasText("kim@example.com")).performScrollTo().performClick()
        button("Share list").performClick()
        rule.onNodeWithTag("composed.confirm").assertExists()
        rule.onNode(hasTestTag("composed.confirm.row") and hasText("kim@example.com", substring = true)).assertExists()
        assertEquals(before, Shop.sharedWith.size)
        rule.onNodeWithTag("composed.confirm.press").performClick()
        rule.waitUntil(10_000) { Shop.sharedWith.size == before + 1 }
        assertEquals("kim@example.com", Shop.sharedWith.last())
    }

    @Test
    fun the_agent_s_update_changes_the_card_and_keeps_what_the_person_typed() {
        val item = hasSetTextAction() and hasContentDescription("Item")
        rule.onNode(item).performTextReplacement("Capers")
        rule.onNodeWithTag("agent-update").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText("Pasta night for two")).fetchSemanticsNodes().isNotEmpty())
        rule.onNode(item).assert(hasText("Capers"))
        rule.onNode(hasSetTextAction() and hasContentDescription("Quantity")).assert(hasText("2"))
        val before = Shop.items.size
        button("Add to list").performClick()
        rule.waitUntil(10_000) { Shop.items.size == before + 1 }
        assertEquals(ListItem(Shop.items.last().id, "Capers", 2.0), Shop.items.last())
    }

    @Test
    fun place_order_hands_the_person_to_the_list_and_never_orders() {
        button("Place order").performClick()
        rule.waitForIdle()
        assertFalse(Shop.ordered.value)
        assertTrue(rule.onAllNodes(hasText("Shopping list")).fetchSemanticsNodes().isNotEmpty())
    }
}
