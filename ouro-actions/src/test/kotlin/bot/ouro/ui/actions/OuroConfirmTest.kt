package bot.ouro.ui.actions

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ask-and-wait rules, with no Android: a fake surface stands in for the screen and the notification. */
class OuroConfirmTest {
    private class Surface(var foreground: Boolean, var notifications: Boolean = true, var showWorks: Boolean = true) : OuroConfirmSurface {
        val shown = mutableListOf<String>()
        val notified = mutableListOf<String>()
        val withdrawn = mutableListOf<String>()
        var notifiedTimeout = 0L

        override fun inForeground() = foreground
        override fun show(id: String): Boolean { if (showWorks) shown += id; return showWorks }
        override fun canNotify() = notifications
        override fun notify(id: String, confirmation: OuroConfirmation, timeoutMs: Long): Boolean { notified += id; notifiedTimeout = timeoutMs; return true }
        override fun withdraw(id: String) { withdrawn += id }
    }

    private val share = OuroConfirmation("share_shopping_list", "Share list", "Share your shopping list.", listOf(OuroConfirmRow("With", "sam@example.com")))

    private fun ids(): () -> String { var n = 0; return { "id${++n}" } }

    private fun confirmer(surface: Surface, broker: OuroConfirmBroker = OuroConfirmBroker(ids()), timeoutMs: Long = 120_000) =
        OuroConfirmer(broker, surface, timeoutMs, "Shop")

    @Test
    fun the_app_on_screen_shows_the_confirmation_at_once_and_allow_runs_it() = runTest {
        val surface = Surface(foreground = true)
        val broker = OuroConfirmBroker(ids())
        val answer = async { confirmer(surface, broker).ask(share) }
        runCurrent()
        assertEquals(listOf("id1"), surface.shown)
        assertTrue(surface.notified.isEmpty())
        assertEquals(share, broker.confirmation("id1"))
        assertTrue(broker.answer("id1", true))
        assertTrue(answer.await())
        assertNull(broker.confirmation("id1"))
        assertEquals(listOf("id1"), surface.withdrawn)
    }

    @Test
    fun the_app_in_the_background_posts_a_notification_and_shows_nothing() = runTest {
        val surface = Surface(foreground = false)
        val broker = OuroConfirmBroker(ids())
        val answer = async { confirmer(surface, broker, timeoutMs = 90_000).ask(share) }
        runCurrent()
        assertTrue(surface.shown.isEmpty())
        assertEquals(listOf("id1"), surface.notified)
        assertEquals("the notification goes when the call stops waiting", 90_000L, surface.notifiedTimeout)
        broker.answer("id1", true)
        assertTrue(answer.await())
    }

    @Test
    fun no_is_the_answer_when_the_person_says_no_or_dismisses_it() = runTest {
        val broker = OuroConfirmBroker(ids())
        val surface = Surface(foreground = true)
        val answer = async { confirmer(surface, broker).ask(share) }
        runCurrent()
        broker.answer("id1", false)
        assertFalse(answer.await())
        assertEquals(0, broker.size)
    }

    @Test
    fun no_answer_within_the_timeout_is_a_refusal_that_says_so_and_a_late_yes_does_nothing() = runTest {
        val broker = OuroConfirmBroker(ids())
        val surface = Surface(foreground = false)
        val result = async { runCatching { confirmer(surface, broker).ask(share) } }
        runCurrent()
        advanceTimeBy(119_999)
        runCurrent()
        assertTrue("still waiting just before the timeout", broker.confirmation("id1") != null)
        advanceTimeBy(2)
        runCurrent()
        val e = result.await().exceptionOrNull()
        assertTrue("$e", e is OuroNeedsPerson)
        assertEquals("The person didn't answer Share list within 2 minutes, so nothing happened. Ask again if they still want it.", e!!.message)
        assertFalse("the answer came too late", broker.answer("id1", true))
        assertEquals(listOf("id1"), surface.withdrawn)
    }

    @Test
    fun with_no_screen_and_no_notification_nothing_can_ask_so_it_is_refused_with_the_fix() = runTest {
        val surface = Surface(foreground = false, notifications = false)
        val broker = OuroConfirmBroker(ids())
        val e = runCatching { confirmer(surface, broker).ask(share) }.exceptionOrNull()
        assertTrue("$e", e is OuroNeedsPerson)
        val message = e!!.message!!
        assertTrue(message, message.startsWith("Share list needs the person's yes, but Shop isn't on screen and can't show a notification"))
        assertTrue(message, message.contains("POST_NOTIFICATIONS"))
        assertTrue(surface.shown.isEmpty() && surface.notified.isEmpty())
        assertEquals("the refused call leaves nothing pending", 0, broker.size)
    }

    @Test
    fun a_screen_that_cannot_open_falls_back_to_the_notification() = runTest {
        val surface = Surface(foreground = true, showWorks = false)
        val broker = OuroConfirmBroker(ids())
        val answer = async { confirmer(surface, broker).ask(share) }
        runCurrent()
        assertEquals(listOf("id1"), surface.notified)
        broker.answer("id1", true)
        assertTrue(answer.await())
    }

    @Test
    fun a_call_that_is_cancelled_takes_its_confirmation_down() = runTest {
        val surface = Surface(foreground = true)
        val broker = OuroConfirmBroker(ids())
        val answer = async { confirmer(surface, broker).ask(share) }
        runCurrent()
        answer.cancel()
        runCurrent()
        assertEquals(0, broker.size)
        assertEquals(listOf("id1"), surface.withdrawn)
        assertFalse(broker.answer("id1", true))
    }

    @Test
    fun each_confirmation_has_its_own_random_id_and_a_stale_id_answers_nothing() = runTest {
        val broker = OuroConfirmBroker { OuroConfirm.randomId() }
        val surface = Surface(foreground = true)
        val first = async { confirmer(surface, broker).ask(share) }
        runCurrent()
        val id1 = surface.shown.single()
        assertTrue("128 random bits as hex: $id1", Regex("[0-9a-f]{32}").matches(id1))
        broker.answer(id1, false)
        assertFalse(first.await())

        val second = async { confirmer(surface, broker).ask(share) }
        runCurrent()
        val id2 = surface.shown.last()
        assertNotEquals(id1, id2)
        assertFalse("the first call's id can't answer the second", broker.answer(id1, true))
        assertFalse("nor can a made-up one", broker.answer("0".repeat(32), true))
        assertEquals(share, broker.confirmation(id2))
        assertTrue(broker.answer(id2, true))
        assertTrue(second.await())
        assertFalse("an id answers once", broker.answer(id2, false))
    }

    @Test
    fun an_id_another_confirmation_holds_is_never_reused() {
        val drawn = sequenceOf("a", "a", "b").iterator()
        val broker = OuroConfirmBroker { drawn.next() }
        assertEquals("a", broker.open(share))
        assertEquals("b", broker.open(share))
        assertEquals(2, broker.size)
    }

    @Test
    fun the_rows_are_the_inputs_fields_as_the_person_reads_them() {
        data class Input(val with: String, val shareCount: Double?, val note: String?, val tags: List<String>, val idempotencyKey: String?)
        val rows = describeInput(Input("sam@example.com", 2.0, null, listOf("a", "b"), "k-1"))
        assertEquals(
            listOf(OuroConfirmRow("With", "sam@example.com"), OuroConfirmRow("Share count", "2"), OuroConfirmRow("Tags", "a, b")),
            rows,
        )
        assertEquals("0.5", valueText(0.5))
        assertEquals("1000000", valueText(1e6))
        assertTrue(describeInput(NoInput).isEmpty())
    }
}

private data object NoInput
