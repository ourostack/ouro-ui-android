package bot.ouro.ui.actions

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The built-in confirmation on Android's own classes: the install, the notification, the screen and its answers. */
@RunWith(RobolectricTestRunner::class)
class OuroConfirmAndroidTest {
    private data class ShareInput(val with: String, val idempotencyKey: String? = null)

    private class Act(consent: OuroConsent) :
        OuroAction<ShareInput, String>("share_list", "Share list", "Share your list.", "Share the list.", consent, "shareList", null, null)

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @After
    fun nothing_is_left_pending() {
        assertEquals(0, OuroConfirm.broker.size)
    }

    private fun installed(timeoutMs: Long = OuroConfirm.DEFAULT_TIMEOUT_MS): Act {
        val action = Act(OuroConsent.Consequential)
        action.perform = { "shared with ${it.with}" }
        OuroConfirm.install(app, OuroActionSet("test", listOf(action)), timeoutMs)
        return action
    }

    private fun texts(view: View): List<String> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else if (view is TextView) listOf(view.text.toString()) else emptyList()

    private fun open(intent: Intent): Activity = Robolectric.buildActivity(OuroConfirmActivity::class.java, intent).setup().get()

    private fun Activity.press(key: String) = window.decorView.findViewWithTag<Button>(key).performClick()

    private fun startForeground() {
        val screen = Robolectric.buildActivity(Activity::class.java).get()
        AndroidConfirmSurface.of(app).screens.onActivityStarted(screen)
    }

    private fun allowNotifications(allowed: Boolean) {
        if (allowed) shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) else shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun install_gives_the_built_in_confirmation_to_consequential_actions_that_have_none_and_keeps_the_apps_own() {
        val none = Act(OuroConsent.Consequential)
        val own = Act(OuroConsent.Consequential)
        val ownConfirm: suspend (ShareInput) -> Boolean = { true }
        own.confirm = ownConfirm
        val write = Act(OuroConsent.Write)
        val personOnly = Act(OuroConsent.PersonOnly)
        OuroConfirm.install(app, OuroActionSet("test", listOf(none, own, write, personOnly)))
        assertNotNull(none.confirm)
        assertTrue("the app's own confirm is never replaced", own.confirm === ownConfirm)
        assertNull(write.confirm)
        assertNull(personOnly.confirm)
    }

    @Test
    fun in_the_foreground_the_confirmation_opens_at_once_and_allow_runs_the_call() = runTest {
        allowNotifications(true)
        startForeground()
        val action = installed()
        val call = async { action.callAsAgent(ShareInput("sam@example.com")) }
        runCurrent()
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(ComponentName(app, OuroConfirmActivity::class.java), intent.component)
        assertTrue("no notification when the person is looking at the app", manager.activeNotifications.isEmpty())
        val screen = open(intent)
        val expected = listOf("Share list?", "Share your list.", "With", "sam@example.com", "Don't", "Allow")
        assertEquals(expected, texts(screen.window.decorView).filter { it in expected })
        assertFalse(call.isCompleted)
        screen.press("allow")
        assertEquals("shared with sam@example.com", call.await())
        assertTrue(screen.isFinishing)
    }

    @Test
    fun dont_refuses_the_call_and_runs_nothing() = runTest {
        startForeground()
        val action = installed()
        var ran = 0
        action.perform = { ran++; "done" }
        val call = async { runCatching { action.callAsAgent(ShareInput("sam@example.com")) } }
        runCurrent()
        val screen = open(shadowOf(app).nextStartedActivity)
        screen.press("dont")
        val e = call.await().exceptionOrNull()
        assertTrue("$e", e is OuroNeedsPerson)
        assertEquals("The person said no to Share list.", e!!.message)
        assertEquals(0, ran)
    }

    @Test
    fun leaving_the_confirmation_without_answering_is_a_no() = runTest {
        startForeground()
        val action = installed()
        val call = async { runCatching { action.callAsAgent(ShareInput("sam@example.com")) } }
        runCurrent()
        val controller = Robolectric.buildActivity(OuroConfirmActivity::class.java, shadowOf(app).nextStartedActivity).setup()
        controller.pause().stop().destroy()
        assertEquals("The person said no to Share list.", call.await().exceptionOrNull()!!.message)
    }

    @Test
    fun in_the_background_a_notification_opens_the_confirmation_and_carries_no_allow_and_no_values() = runTest {
        allowNotifications(true)
        val action = installed()
        val call = async { action.callAsAgent(ShareInput("sam@example.com")) }
        runCurrent()
        assertNull("nothing opens over whatever the person is doing", shadowOf(app).nextStartedActivity)
        val posted = manager.activeNotifications.single()
        val notification = posted.notification
        assertEquals("Share list?", notification.extras.getString(Notification.EXTRA_TITLE))
        assertFalse("the values are read in the confirmation, not on the lock screen", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("sam@example.com"))
        assertNull("no Allow on the notification", notification.actions)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(AndroidConfirmSurface.CHANNEL).importance)
        val opener = shadowOf(notification.contentIntent)
        assertTrue("immutable, so what opens it can't be changed", (opener.flags and PendingIntent.FLAG_IMMUTABLE) != 0)
        assertEquals(posted.tag, opener.savedIntent.data!!.authority)
        val screen = open(opener.savedIntent)
        screen.press("allow")
        assertEquals("shared with sam@example.com", call.await())
        assertTrue("answered, so the notification is taken down", manager.activeNotifications.isEmpty())
    }

    @Test
    fun without_notification_permission_and_off_screen_the_call_is_refused_with_why_and_the_fix() = runTest {
        allowNotifications(false)
        val action = installed()
        val e = runCatching { action.callAsAgent(ShareInput("sam@example.com")) }.exceptionOrNull()
        assertTrue("$e", e is OuroNeedsPerson)
        assertTrue(e!!.message!!, e.message!!.contains("can't show a notification") && e.message!!.contains("POST_NOTIFICATIONS"))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun a_channel_the_person_turned_off_is_no_way_to_ask() = runTest {
        allowNotifications(true)
        manager.createNotificationChannel(android.app.NotificationChannel(AndroidConfirmSurface.CHANNEL, "Agent requests", NotificationManager.IMPORTANCE_NONE))
        val action = installed()
        val e = runCatching { action.callAsAgent(ShareInput("sam@example.com")) }.exceptionOrNull()
        assertTrue("$e", e is OuroNeedsPerson)
    }

    @Test
    fun the_screen_is_not_exported_and_shows_nothing_for_an_id_that_is_spent_or_made_up() = runTest {
        val info = app.packageManager.getActivityInfo(ComponentName(app, OuroConfirmActivity::class.java), 0)
        assertFalse(info.exported)

        startForeground()
        val action = installed()
        val call = async { runCatching { action.callAsAgent(ShareInput("sam@example.com")) } }
        runCurrent()
        val real = shadowOf(app).nextStartedActivity
        val forged = Intent(real).setData(Uri.Builder().scheme("ouro-confirm").authority("f".repeat(32)).build())
        val stale = open(forged)
        assertTrue("a made-up id shows nothing", stale.isFinishing)
        assertFalse("and answers nothing", call.isCompleted)
        // A forged extra can't change what is asked either: the values come from the pending confirmation.
        val shown = open(Intent(real).putExtra("title", "Delete everything?"))
        assertFalse(texts(shown.window.decorView).any { it.contains("Delete everything") })
        shown.press("dont")
        call.await()
        assertTrue("a spent id shows nothing", open(real).isFinishing)
    }

    @Test
    fun a_timeout_is_a_refusal_even_with_the_confirmation_on_screen() = runTest {
        startForeground()
        val action = installed(timeoutMs = 30_000)
        val e = runCatching { action.callAsAgent(ShareInput("sam@example.com")) }.exceptionOrNull()
        assertTrue("$e", e is OuroNeedsPerson)
        assertEquals("The person didn't answer Share list within 30 seconds, so nothing happened. Ask again if they still want it.", e!!.message)
    }
}
