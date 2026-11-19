// The built-in confirmation, on a real Android: an agent's share asks the person instead of being refused. Allow runs
// it; Don't and Back refuse it. With the app in the background the person is asked with a notification, which has no
// Allow on it and none of the values, and opens the same confirmation. The timeout and the permission rules are proved
// in ouro-actions' unit tests; the person-only refusal stays in XRayTest.
package bot.ouro.shop

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class ConfirmTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

    @Before
    fun the_app_may_post_notifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun shareFunction(): AppFunctionMetadata = runBlocking {
        assumeTrue("AppFunctions need Android 16", Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
        val m = AppFunctionManager.getInstance(context) ?: error("This device has no AppFunctionManager")
        // Android indexes a newly installed app's functions in the background.
        repeat(30) {
            val fns = m.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
            fns.firstOrNull { it.id.endsWith("#shareShoppingList") }?.let { return@runBlocking it }
            Thread.sleep(1000)
        }
        error("Android never indexed shareShoppingList")
    }

    /** An agent's call, as AppFunctionManager runs it; it returns when the person has answered, or the call ended. */
    private fun share(f: AppFunctionMetadata, with: String): ExecuteAppFunctionResponse = runBlocking {
        val data = AppFunctionData.Builder(f.parameters, f.components).setString("with", with).build()
        AppFunctionManager.getInstance(context)!!.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, f.id, data))
    }

    /** Runs the agent's call on its own thread while [person] answers on this one, and returns what the agent got. */
    private fun whileAsking(call: () -> ExecuteAppFunctionResponse, person: () -> Unit): ExecuteAppFunctionResponse {
        val result = AtomicReference<Result<ExecuteAppFunctionResponse>>()
        val agent = thread { result.set(runCatching(call)) }
        try {
            person()
        } finally {
            // A person step that failed leaves the call waiting two minutes: Back is no, so the call ends now.
            if (agent.isAlive) device.pressBack()
            agent.join(60_000)
        }
        return result.get()?.getOrThrow() ?: error("The agent's call never returned")
    }

    private fun pattern(text: String): Pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)

    private fun see(text: String, ms: Long = 30_000): UiObject2 =
        device.wait(Until.findObject(By.text(pattern(text))), ms) ?: error("\"$text\" never appeared on screen")

    private fun <T : Any> poll(ms: Long, f: () -> T?): T? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            f()?.let { return it }
            Thread.sleep(250)
        }
        return null
    }

    private fun readsTheValues() {
        see("Share list?")
        see("Share your shopping list with someone in your household.")
        see("With")
        see("sam@example.com")
    }

    @Test
    fun allow_runs_the_share_an_agent_asked_for_with_the_app_on_screen() {
        val f = shareFunction()
        ActivityScenario.launch(MainActivity::class.java).use {
            val before = Shop.sharedWith.toList()
            val res = whileAsking({ share(f, "sam@example.com") }) {
                readsTheValues()
                assertEquals("nothing is shared before the person answers", before, Shop.sharedWith.toList())
                see("Allow").click()
            }
            assertTrue("share: $res ${(res as? ExecuteAppFunctionResponse.Error)?.error}", res is ExecuteAppFunctionResponse.Success)
            assertEquals(before + "sam@example.com", Shop.sharedWith.toList())
        }
    }

    @Test
    fun dont_refuses_the_share_and_adds_nobody() {
        val f = shareFunction()
        ActivityScenario.launch(MainActivity::class.java).use {
            val before = Shop.sharedWith.toList()
            val res = whileAsking({ share(f, "sam@example.com") }) {
                readsTheValues()
                see("Don't").click()
            }
            assertTrue("share: $res", res is ExecuteAppFunctionResponse.Error && res.error is AppFunctionDeniedException)
            assertEquals("The person said no to Share list.", (res as ExecuteAppFunctionResponse.Error).error.errorMessage)
            assertEquals(before, Shop.sharedWith.toList())
        }
    }

    @Test
    fun back_is_a_no_too() {
        val f = shareFunction()
        ActivityScenario.launch(MainActivity::class.java).use {
            val before = Shop.sharedWith.toList()
            val res = whileAsking({ share(f, "sam@example.com") }) {
                readsTheValues()
                device.pressBack()
            }
            assertTrue("share: $res", res is ExecuteAppFunctionResponse.Error && res.error is AppFunctionDeniedException)
            assertEquals(before, Shop.sharedWith.toList())
        }
    }

    @Test
    fun with_the_app_in_the_background_a_notification_asks_and_opens_the_same_confirmation() {
        val f = shareFunction()
        ActivityScenario.launch(MainActivity::class.java).use {
            // A trip to the launcher stops the app's activity with nothing of the app on screen, so the confirmation can't
            // open by itself. (Scenario's moveToState(CREATED) parks an empty activity of the app in front instead.)
            device.pressHome()
            assertTrue("the app left the screen", device.wait(Until.gone(By.pkg(context.packageName)), 15_000))
            // Android reports the stop a moment after the window is gone.
            Thread.sleep(1500)
            val manager = context.getSystemService(NotificationManager::class.java)
            val before = Shop.sharedWith.toList()
            val res = whileAsking({ share(f, "sam@example.com") }) {
                val posted: StatusBarNotification = poll(30_000) {
                    manager.activeNotifications.firstOrNull { it.packageName == context.packageName && it.notification.extras.getString(Notification.EXTRA_TITLE) == "Share list?" }
                } ?: error("No notification asked the person")
                assertNull("no Allow on the notification: the person reads the values first", posted.notification.actions)
                assertFalse("the values are not on the notification", posted.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("sam@example.com"))
                assertNotNull(posted.notification.contentIntent)
                assertEquals("nothing is shared before the person answers", before, Shop.sharedWith.toList())
                open(posted)
                readsTheValues()
                see("Allow").click()
            }
            assertTrue("share: $res ${(res as? ExecuteAppFunctionResponse.Error)?.error}", res is ExecuteAppFunctionResponse.Success)
            assertEquals(before + "sam@example.com", Shop.sharedWith.toList())
        }
    }

    /** What the person does: pull the notification shade down and tap it, or, where the image has no shade, its content intent. */
    private fun open(posted: StatusBarNotification) {
        device.openNotification()
        val tapped = device.wait(Until.findObject(By.text(pattern("Share list?"))), 10_000)
        if (tapped != null) tapped.click() else posted.notification.contentIntent.send()
    }
}
