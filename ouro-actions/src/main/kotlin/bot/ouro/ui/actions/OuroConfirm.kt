// The built-in confirmation: what the person sees before an agent's consequential call runs, and the rules around it.
//
// Android has no system prompt to ask for an app, so the kit asks itself, with platform views only (no Compose, so an
// app that doesn't use ouro-hx gets it too):
//
// - app on screen: the confirmation opens at once;
// - app in the background (an AppFunction call's usual case): a high-priority notification, whose tap opens the
//   confirmation. The notification carries no Allow, so the person always reads the values first;
// - the call waits until the person answers or [OuroConfirm.DEFAULT_TIMEOUT_MS] passes. No answer, Back, tapping
//   outside and timing out all mean no, and no is the default button;
// - with no notification permission and the app in the background, nothing can ask, so the call is refused with the
//   reason and the fix;
// - every pending confirmation has a fresh random id, used once; the activity isn't exported and the notification's
//   PendingIntent is immutable, so a stale or forged intent can't answer for another call.
package bot.ouro.ui.actions

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Modifier
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** One value an agent prepared, as the person reads it: the field's label and the value. */
data class OuroConfirmRow(val label: String, val value: String)

/**
 * What the person is asked, in the words of the composed card's confirm sheet (ouro-hx): the action's label as a
 * question, its description, and each prepared value under its label.
 */
data class OuroConfirmation(
    /** The action's id, such as `share_shopping_list`. */
    val action: String,
    /** The action's label, such as "Share list". */
    val title: String,
    /** The action's one sentence, or the question its declaration gives as `confirm`. */
    val description: String,
    val rows: List<OuroConfirmRow>,
) {
    /** The heading: the label as a question, "Share list?". */
    val question: String get() = "$title?"
}

/** How a pending confirmation ended. */
internal enum class OuroAnswer { Allowed, Declined, TimedOut }

/**
 * The confirmations waiting for the person, each under its own random id. The id is the only handle: whoever holds it
 * can answer once, and nothing else can answer or read the values.
 */
internal class OuroConfirmBroker(private val newId: () -> String) {
    private class Pending(val confirmation: OuroConfirmation) {
        val answer = CompletableDeferred<Boolean>()
    }

    private val pending = ConcurrentHashMap<String, Pending>()

    /** Registers a confirmation under an id nothing else holds. */
    fun open(confirmation: OuroConfirmation): String {
        val entry = Pending(confirmation)
        return generateSequence { newId() }.first { pending.putIfAbsent(it, entry) == null }
    }

    /** What the person is asked for this id, or null when it has ended or never existed. */
    fun confirmation(id: String): OuroConfirmation? = pending[id]?.confirmation

    /** The person's answer. True only for the first answer to a pending id; a stale or made-up id answers nothing. */
    fun answer(id: String, allow: Boolean): Boolean {
        val entry = pending.remove(id) ?: return false
        return entry.answer.complete(allow)
    }

    /** Runs [block] when this id ends, however it ends; null when it already has. */
    fun whenEnded(id: String, block: () -> Unit): DisposableHandle? =
        pending[id]?.answer?.invokeOnCompletion { block() }

    /** Waits for the answer for [timeoutMs]; the id is gone when this returns, however it returns. */
    suspend fun await(id: String, timeoutMs: Long): OuroAnswer {
        val entry = pending[id] ?: return OuroAnswer.Declined
        try {
            return when (withTimeoutOrNull(timeoutMs) { entry.answer.await() }) {
                true -> OuroAnswer.Allowed
                false -> OuroAnswer.Declined
                null -> OuroAnswer.TimedOut
            }
        } finally {
            close(id)
        }
    }

    /** Ends an id with no, if it is still pending. */
    fun close(id: String) {
        pending.remove(id)?.answer?.complete(false)
    }

    /** How many confirmations are waiting. */
    val size: Int get() = pending.size
}

/** Where a confirmation reaches the person. Android's implementation is [AndroidConfirmSurface]. */
internal interface OuroConfirmSurface {
    /** Whether the app has a screen the person can see right now. */
    fun inForeground(): Boolean

    /** Opens the confirmation for this id on screen. False when it couldn't. */
    fun show(id: String): Boolean

    /** Whether a notification would reach the person. */
    fun canNotify(): Boolean

    /** Posts the high-priority notification whose tap opens the confirmation. False when it couldn't. */
    fun notify(id: String, confirmation: OuroConfirmation, timeoutMs: Long): Boolean

    /** Takes down whatever [show] or [notify] put up for this id. */
    fun withdraw(id: String)
}

/** Asks the person and waits. One per app; every consequential action shares it. */
internal class OuroConfirmer(
    private val broker: OuroConfirmBroker,
    private val surface: OuroConfirmSurface,
    private val timeoutMs: Long,
    private val appName: String,
) {
    /** True when the person allowed it. False when they said no. Throws [OuroNeedsPerson] when nothing could ask, or no one answered in time. */
    suspend fun ask(confirmation: OuroConfirmation): Boolean {
        val id = broker.open(confirmation)
        try {
            val shown = surface.inForeground() && surface.show(id)
            if (!shown && !(surface.canNotify() && surface.notify(id, confirmation, timeoutMs))) {
                throw OuroNeedsPerson(
                    confirmation.action,
                    "${confirmation.title} needs the person's yes, but $appName isn't on screen and can't show a notification, so no one could be asked and nothing happened. " +
                        "Ask the person to open $appName and do it there, or to allow its notifications (on Android 13 and later, the app must hold POST_NOTIFICATIONS).",
                )
            }
            return when (broker.await(id, timeoutMs)) {
                OuroAnswer.Allowed -> true
                OuroAnswer.Declined -> false
                OuroAnswer.TimedOut -> throw OuroNeedsPerson(
                    confirmation.action,
                    "The person didn't answer ${confirmation.title} within ${duration(timeoutMs)}, so nothing happened. Ask again if they still want it.",
                )
            }
        } finally {
            broker.close(id)
            surface.withdraw(id)
        }
    }

    private fun duration(ms: Long): String = when {
        ms % 60_000 == 0L -> if (ms == 60_000L) "1 minute" else "${ms / 60_000} minutes"
        ms % 1_000 == 0L -> if (ms == 1_000L) "1 second" else "${ms / 1_000} seconds"
        else -> "$ms ms"
    }
}

/**
 * The built-in confirmation for consequential actions an agent calls, so the call asks the person instead of being
 * refused. Install it once when the app starts:
 *
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         MyActions.bind()
 *         OuroConfirm.install(this, OuroActions.set)
 *     }
 * }
 * ```
 *
 * It sets [OuroAction.confirm] on each consequential action that has none yet, so an app that sets its own `confirm`
 * keeps it. Call it from `Application.onCreate`, so the app's screens are counted from the first one.
 */
object OuroConfirm {
    /** How long a confirmation waits for the person: two minutes. */
    const val DEFAULT_TIMEOUT_MS: Long = 120_000

    internal val broker = OuroConfirmBroker { randomId() }

    /**
     * Gives every consequential action in [actions] that has no `confirm` the built-in confirmation.
     *
     * @param timeoutMs how long the call waits for the person before it is refused. A timeout is a no.
     * @param confirmTexts the question a declaration gives an action as `confirm`, by action id (core's `doors.json` lists it
     * under each action's `text`). An action without an entry is asked with its description, which is what core uses when
     * none is declared, so the person reads the same words the other doors show.
     * @param describe the rows the person reads for an action's input, each a label and a value. Without it, each
     * non-null field of the input is a row, its name as words ("shareWith" is "Share with") and its value as text,
     * except `idempotencyKey`. A release build that renames fields (R8) needs `describe`, or a keep rule for the input classes.
     */
    @JvmOverloads
    fun install(
        context: Context,
        actions: OuroActionSet,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        confirmTexts: Map<String, String> = emptyMap(),
        describe: ((OuroAction<*, *>, Any) -> List<OuroConfirmRow>)? = null,
    ) {
        require(timeoutMs > 0) { "[ouro-ui] OuroConfirm: timeoutMs must be positive. Fix: pass OuroConfirm.DEFAULT_TIMEOUT_MS or a number of milliseconds above 0." }
        val app = context.applicationContext as Application
        val confirmer = OuroConfirmer(broker, AndroidConfirmSurface.of(app), timeoutMs, appName(app))
        for (action in actions.actions) {
            if (action.consent != OuroConsent.Consequential || action.confirm != null) continue
            @Suppress("UNCHECKED_CAST")
            val typed = action as OuroAction<Any, Any>
            typed.confirm = { input ->
                val rows = describe?.invoke(typed, input) ?: describeInput(input)
                confirmer.ask(OuroConfirmation(typed.id, typed.label, confirmTexts[typed.id] ?: typed.description, rows))
            }
        }
    }

    private fun appName(app: Application): String = app.applicationInfo.loadLabel(app.packageManager).toString()

    private val random = SecureRandom()

    /** 128 random bits as 32 hex digits: unguessable, and fresh for each confirmation. */
    internal fun randomId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/** The rows for an input without a `describe`: each non-null field, labelled with its name as words. */
internal fun describeInput(input: Any): List<OuroConfirmRow> =
    input.javaClass.declaredFields
        .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.name != "idempotencyKey" }
        .mapNotNull { field ->
            field.setAccessible(true)
            val value = field.get(input) ?: return@mapNotNull null
            OuroConfirmRow(words(field.name), valueText(value))
        }

/** "shareWith" as "Share with". */
internal fun words(name: String): String =
    name.replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2").lowercase().replaceFirstChar { it.uppercase() }

/** A value as the person reads it: a whole number without ".0", a list joined with commas. */
internal fun valueText(value: Any): String = when (value) {
    is String -> value
    is Double -> number(value)
    is Float -> number(value.toDouble())
    is Iterable<*> -> value.joinToString(", ") { if (it == null) "" else valueText(it) }
    is Array<*> -> value.joinToString(", ") { if (it == null) "" else valueText(it) }
    else -> value.toString()
}

private fun number(d: Double): String =
    if (d.isNaN() || d.isInfinite()) d.toString() else BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
