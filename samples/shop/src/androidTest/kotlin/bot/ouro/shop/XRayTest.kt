// The X-ray, on a real Android: what agents can call (the AppFunctions Android indexed for this app) and what people
// can press (the buttons on screen) both come from shopping.actions.mts, and they agree with it.
package bot.ouro.shop

import android.os.Build
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import bot.ouro.shop.actions.OuroActions
import bot.ouro.ui.actions.OuroConsent
import bot.ouro.ui.hx.OuroActionKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XRayTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun found(): List<AppFunctionMetadata> = runBlocking {
        assumeTrue("AppFunctions need Android 16", Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
        val m = AppFunctionManager.getInstance(context) ?: error("This device has no AppFunctionManager")
        var fns = emptyList<AppFunctionMetadata>()
        // Android indexes a newly installed app's functions in the background.
        repeat(30) {
            fns = m.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
            if (fns.isNotEmpty()) return@runBlocking fns
            Thread.sleep(1000)
        }
        fns
    }

    @Test
    fun agents_get_exactly_the_declared_actions_an_agent_may_call() {
        val fns = found()
        val names = fns.map { it.id.substringAfter('#') }.toSet()
        val declared = OuroActions.set.actions.filter { it.consent != OuroConsent.PersonOnly }.map { it.appFunction }.toSet()
        assertEquals("AppFunctions Android indexed for ${context.packageName}", declared, names)
        for (a in OuroActions.set.appFunctions) {
            val f = fns.first { it.id.endsWith("#${a.appFunction}") }
            assertEquals("${a.id}: the instruction agents read is the declaration's agent text", a.agent, f.description)
        }
    }

    @Test
    fun a_person_only_action_has_no_appfunction_and_a_typed_refusal() {
        val fns = found()
        assertTrue(fns.none { it.id.endsWith("#placeGroceryOrder") })
        val r = OuroActions.set.refusal("place_grocery_order")
        assertNotNull(r)
        assertEquals("person-only", r!!.code)
        assertEquals(2, r.exit)
        val m = AppFunctionManager.getInstance(context)!!
        val prefix = fns.first().id.substringBefore('#')
        val res = runBlocking { m.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, "$prefix#placeGroceryOrder", AppFunctionData.EMPTY)) }
        assertTrue("calling it anyway is an error, not an order: $res", res is ExecuteAppFunctionResponse.Error)
    }

    @Test
    fun an_agent_adds_through_the_same_handler_people_press() {
        val fns = found()
        val add = fns.first { it.id.endsWith("#addShoppingListItem") }
        val before = Shop.items.size
        val data = AppFunctionData.Builder(add.parameters, add.components).setString("name", "Capers").build()
        val res = runBlocking { AppFunctionManager.getInstance(context)!!.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, add.id, data)) }
        assertTrue("add: $res ${(res as? ExecuteAppFunctionResponse.Error)?.error}", res is ExecuteAppFunctionResponse.Success)
        rule.waitForIdle()
        assertEquals(before + 1, Shop.items.size)
        assertEquals("Capers", Shop.items.last().name)
    }

    @Test
    fun a_consequential_action_is_refused_until_the_person_confirms() {
        val fns = found()
        val share = fns.first { it.id.endsWith("#shareShoppingList") }
        // Other tests in this run share the list as the person; the agent's call must add nobody.
        val before = Shop.sharedWith.toList()
        val data = AppFunctionData.Builder(share.parameters, share.components).setString("with", "sam@example.com").build()
        val res = runBlocking { AppFunctionManager.getInstance(context)!!.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, share.id, data)) }
        assertTrue("share: $res", res is ExecuteAppFunctionResponse.Error && res.error is AppFunctionDeniedException)
        assertEquals(before, Shop.sharedWith.toList())
    }

    @Test
    fun people_get_a_button_for_every_declared_action() {
        val pressed = rule.onAllNodes(SemanticsMatcher.keyIsDefined(OuroActionKey)).fetchSemanticsNodes()
            .map { it.config.getOrNull(OuroActionKey)!!.split(";") }
        assertEquals(OuroActions.set.actions.map { it.id }.toSet(), pressed.map { it[0] }.toSet())
        for ((id, consent) in pressed) assertEquals(id, OuroActions.set[id]!!.consent.wire, consent)
    }
}
