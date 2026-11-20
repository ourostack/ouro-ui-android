// Dialog, Sheet and ConfirmDialog in real windows on CI's emulator: each opens, its Close, Cancel or Back closes it, and
// an outside press never closes the ConfirmDialog.
package bot.ouro.shop

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun open() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("screen", "overlays").putExtra("dark", false)
        scenario = ActivityScenario.launch(intent)
        rule.waitForIdle()
    }

    @After
    fun close() = scenario.close()

    private fun last(): String = rule.onNodeWithTag("overlay-last").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun the_dialog_and_the_sheet_close_from_their_close_button() {
        for (kind in listOf("dialog", "sheet")) {
            rule.onNodeWithTag("open-$kind").performClick()
            rule.onNodeWithTag("$kind.close").assertIsDisplayed()
            rule.onNodeWithTag("$kind.close").performClick()
            rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("$kind.close")).fetchSemanticsNodes().isEmpty() }
            assertTrue(last(), last().endsWith("CloseButton"))
        }
    }

    @Test
    fun the_confirm_dialog_is_cancelled_by_back_and_by_cancel_and_confirmed_by_its_action() {
        rule.onNodeWithTag("open-confirm").performClick()
        rule.onNodeWithTag("confirm-dialog.cancel").assertIsDisplayed()
        device.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("confirm-dialog.cancel")).fetchSemanticsNodes().isEmpty() }
        assertTrue(last(), last() == "Kept")
        rule.onNodeWithTag("open-confirm").performClick()
        rule.onNodeWithTag("confirm-dialog.cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("confirm-dialog.cancel")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("open-confirm").performClick()
        rule.onNodeWithTag("confirm-dialog.confirm").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("confirm-dialog.confirm")).fetchSemanticsNodes().isEmpty() }
        assertTrue(last(), last() == "Deleted")
    }

    @Test
    fun a_press_outside_never_closes_the_confirm_dialog() {
        rule.onNodeWithTag("open-confirm").performClick()
        rule.onNodeWithTag("confirm-dialog.cancel").assertIsDisplayed()
        device.click(device.displayWidth / 2, device.displayHeight * 9 / 10)
        rule.waitForIdle()
        rule.onNodeWithTag("confirm-dialog.cancel").assertIsDisplayed()
    }
}
