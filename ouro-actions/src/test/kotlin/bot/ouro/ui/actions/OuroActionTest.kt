package bot.ouro.ui.actions

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class OuroActionTest {
    private class Act(consent: OuroConsent, refusal: OuroRefusal? = null) :
        OuroAction<String, String>("do_thing", "Do thing", "Do the thing.", "Do the thing for the person.", consent, if (refusal == null) "doThing" else null, refusal?.let { "person-only" }, refusal)

    private val refusal = OuroRefusal("do_thing", "thing do", "You can't. Ask the person, who does it with Do thing", "Do thing: You can't. Ask the person, who does it with Do thing.", "You can't.", "with Do thing")

    @Test
    fun a_press_and_an_agent_call_run_the_same_handler() = runTest {
        val a = Act(OuroConsent.Write)
        a.perform = { "did $it" }
        assertEquals("did x", a.press("x"))
        assertEquals("did x", a.callAsAgent("x"))
    }

    @Test
    fun a_person_only_action_refuses_every_agent_with_cores_refusal_but_runs_for_the_person() = runTest {
        val a = Act(OuroConsent.PersonOnly, refusal)
        var ran = 0
        a.perform = { ran++; "ok" }
        val thrown = runCatching { a.callAsAgent("x") }.exceptionOrNull()
        assertSame(refusal, thrown)
        assertEquals(0, ran)
        assertEquals("person-only", refusal.code)
        assertEquals(2, refusal.exit)
        assertEquals("ok", a.press("x"))
        assertEquals(1, ran)
    }

    @Test
    fun a_consequential_action_fails_closed_until_the_app_says_how_to_ask() = runTest {
        val a = Act(OuroConsent.Consequential)
        var ran = 0
        a.perform = { ran++; "ok" }
        assertThrows(OuroNeedsPerson::class.java) { kotlinx.coroutines.runBlocking { a.callAsAgent("x") } }
        a.confirm = { false }
        assertThrows(OuroNeedsPerson::class.java) { kotlinx.coroutines.runBlocking { a.callAsAgent("x") } }
        assertEquals(0, ran)
        a.confirm = { true }
        assertEquals("ok", a.callAsAgent("x"))
        assertEquals("ok", a.press("x"))
        assertEquals(2, ran)
    }

    @Test
    fun an_unbound_action_says_how_to_fix_it() {
        val e = assertThrows(OuroActionNotBound::class.java) { kotlinx.coroutines.runBlocking { Act(OuroConsent.Read).press("x") } }
        assertEquals("do_thing ran before the app set its handler. Fix: set its perform when the app starts.", e.message)
    }

    @Test
    fun a_set_finds_a_refusal_by_id_or_camel_name() {
        val set = OuroActionSet("s", listOf(Act(OuroConsent.PersonOnly, refusal), Act(OuroConsent.Write)))
        assertSame(refusal, set.refusal("do_thing"))
        assertSame(refusal, set.refusal("doThing"))
        assertNull(set.refusal("nothing"))
    }
}
