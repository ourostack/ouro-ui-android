// What an agent sees: the app calls its own AppFunctions through AppFunctionManager, as an assistant would, and
// shows each answer. Calling your own package's functions needs no permission.
package bot.ouro.shop

import android.content.Context
import android.os.Build
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionMetadata
import bot.ouro.shop.actions.OuroActions
import bot.ouro.ui.hx.OuroTone

/** One call an agent made and what came back. [asking] is a call still waiting for the person's yes. */
data class AgentLine(val call: String, val answer: String, val tone: OuroTone, val asking: Boolean = false)

private fun AppFunctionMetadata.short() = id.substringAfter('#')

/**
 * Every door an agent has: search the app's functions, call add and share, and try place order. Share asks the person
 * (OuroConfirm's built-in confirmation), so [progress] gets the lines so far, with share as asking, until they answer.
 */
suspend fun askAsAgent(context: Context, progress: (List<AgentLine>) -> Unit = {}): List<AgentLine> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return listOf(AgentLine("AppFunctions", "Android ${Build.VERSION.RELEASE} has no AppFunctions; they need Android 16.", OuroTone.Warning))
    val manager = AppFunctionManager.getInstance(context)
        ?: return listOf(AgentLine("AppFunctions", "This device doesn't offer AppFunctions.", OuroTone.Warning))
    val pkg = context.packageName
    val found = manager.searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(pkg)))
    val lines = mutableListOf(AgentLine("search $pkg", found.map { it.short() }.sorted().joinToString(", ").ifEmpty { "nothing found" }, if (found.isEmpty()) OuroTone.Destructive else OuroTone.Secondary))
    progress(lines.toList())

    suspend fun call(name: String, args: Map<String, String>, asksPerson: Boolean = false): AgentLine {
        val meta = found.firstOrNull { it.short() == name }
        val id = meta?.id ?: (found.firstOrNull()?.id?.substringBefore('#')?.let { "$it#$name" } ?: name)
        val data = if (meta != null) AppFunctionData.Builder(meta.parameters, meta.components).apply { args.forEach { (k, v) -> setString(k, v) } }.build() else AppFunctionData.EMPTY
        val shown = "$name(${args.entries.joinToString { "${it.key}=\"${it.value}\"" }})"
        if (asksPerson) progress(lines + AgentLine(shown, "Waiting for the person to answer.", OuroTone.Secondary, asking = true))
        return when (val r = manager.executeAppFunction(ExecuteAppFunctionRequest(pkg, id, data))) {
            is ExecuteAppFunctionResponse.Success -> {
                val back = r.returnValue.getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)?.getString("id")
                AgentLine(shown, if (back != null) "Done: item $back" else "Done", OuroTone.Success)
            }
            is ExecuteAppFunctionResponse.Error -> AgentLine(shown, "${r.error.javaClass.simpleName.removeSuffix("Exception")}: ${r.error.errorMessage}", OuroTone.Destructive)
        }
    }

    lines += call("addShoppingListItem", mapOf("name" to "Basil"))
    progress(lines.toList())
    lines += call("shareShoppingList", mapOf("with" to "sam@example.com"), asksPerson = true)
    progress(lines.toList())
    lines += call("placeGroceryOrder", emptyMap())
    val refusal = OuroActions.set.refusal("place_grocery_order")!!
    lines += AgentLine("refusal for place_grocery_order", "${refusal.code}, exit ${refusal.exit}: ${refusal.message}", OuroTone.Warning)
    return lines
}
