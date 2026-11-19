// The runtime every Android door goes through. ouro-actions-android generates one OuroAction per declared action
// (OuroActions.kt) and an AppFunction for each one an agent may call; this file holds the rules they share, once:
//
// - a person's press and an agent's call run the same handler (`perform`), so they can't drift;
// - a person-only action refuses every agent door with core's typed refusal, and has no AppFunction at all;
// - a consequential action asks the person first (`confirm`) and fails closed when nothing says how to ask, because
//   Android has no system prompt to ask for the app. `OuroConfirm.install` says how, with a built-in confirmation.
package bot.ouro.ui.actions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeniedException

/** What an action needs before it runs: core's consent classes. */
enum class OuroConsent(val wire: String) {
    Read("read"),
    Write("write"),
    Consequential("consequential"),
    PersonOnly("person-only"),
}

/**
 * Core's typed refusal of a person-only action (the `error` of web's refused outcome: code `person-only`, exit 2,
 * thing, problem, message), plus the declaration's reason and how the person does it.
 */
class OuroRefusal(
    /** The action's id, such as `place_grocery_order`. */
    val action: String,
    /** The CLI words core names it by, such as `list order`. */
    val thing: String,
    val problem: String,
    message: String,
    val reason: String,
    val how: String,
) : Exception(message) {
    val code: String = "person-only"
    val exit: Int = 2
}

/** A consequential action an agent asked for, without the person's yes. */
class OuroNeedsPerson(val action: String, message: String) : Exception(message)

/** An action ran before the app set its handler. */
class OuroActionNotBound(val action: String) :
    IllegalStateException("$action ran before the app set its handler. Fix: set its perform when the app starts.")

/**
 * One declared action: its facts, from the declaration, and the handler every door calls. Generated; an app sets
 * [perform] (and [confirm] for a consequential action) when it starts.
 */
abstract class OuroAction<I : Any, O : Any>(
    /** snake_case, the MCP tool name: `add_shopping_list_item`. */
    val id: String,
    /** The person's words for it: the button text. */
    val label: String,
    /** The person's one sentence: the button's hint. */
    val description: String,
    /** What an agent reads: the AppFunction's description. */
    val agent: String,
    val consent: OuroConsent,
    /** The AppFunction's method name, or null when no AppFunction carries the action ([appFunctionWhy] says why). */
    val appFunction: String?,
    val appFunctionWhy: String?,
    /** Core's typed refusal, for a person-only action. */
    val refusal: OuroRefusal?,
) {
    /** The app's handler. Every door calls it. */
    @Volatile
    var perform: (suspend (I) -> O)? = null

    /**
     * Consequential only: how the app asks the person before an agent's call runs. Return true to go ahead. Call
     * [OuroConfirm.install] at startup to set the built-in confirmation on every action that has none; set this
     * yourself to ask another way (it is never replaced). Unset, an agent's call is refused.
     */
    @Volatile
    var confirm: (suspend (I) -> Boolean)? = null

    /** The person's door: their own press in the app. It runs every action, person-only ones included. */
    suspend fun press(input: I): O = run(input)

    /** An agent's door: refuses a person-only action, asks the person before a consequential one, then runs. */
    suspend fun callAsAgent(input: I): O {
        refusal?.let { throw it }
        if (consent == OuroConsent.Consequential) {
            val ask = confirm
                ?: throw OuroNeedsPerson(id, "$label needs the person's yes, and this app hasn't said how to ask. Ask the person to do it in the app.")
            if (!ask(input)) throw OuroNeedsPerson(id, "The person said no to $label.")
        }
        return run(input)
    }

    private suspend fun run(input: I): O = (perform ?: throw OuroActionNotBound(id))(input)
}

/**
 * For generated AppFunctions: the agent door, answered in AppFunctions' own errors. A refusal or a missing yes is
 * [AppFunctionDeniedException] with core's message; a handler the app never set is [AppFunctionAppUnknownException].
 */
suspend fun <I : Any, O : Any> OuroAction<I, O>.callFromAppFunction(input: I): O =
    try {
        callAsAgent(input)
    } catch (e: OuroRefusal) {
        throw AppFunctionDeniedException(e.message)
    } catch (e: OuroNeedsPerson) {
        throw AppFunctionDeniedException(e.message)
    } catch (e: OuroActionNotBound) {
        throw AppFunctionAppUnknownException(e.message)
    }

/** An action set's catalog, as generated. */
class OuroActionSet(val name: String, val actions: List<OuroAction<*, *>>) {
    /** The action with this id, or null. */
    operator fun get(id: String): OuroAction<*, *>? = actions.firstOrNull { it.id == id }

    /** The actions an agent can call, each an AppFunction. */
    val appFunctions: List<OuroAction<*, *>> get() = actions.filter { it.appFunction != null }

    /**
     * Core's typed refusal for a person-only action named by its id or its AppFunction-style camel name; null for any
     * other action. A bridge that relays an agent's call to an action with no AppFunction answers with it.
     */
    fun refusal(name: String): OuroRefusal? =
        actions.firstOrNull { it.id == name || camel(it.id) == name }?.refusal

    private fun camel(id: String): String =
        id.split("_").mapIndexed { i, p -> if (i == 0) p else p.replaceFirstChar { it.uppercase() } }.joinToString("")
}
