// A card an agent composed for the person, drawn with Ouro HX: the tool result in assets/agent-card.json carries Ouro's
// tree (samples/shop/agent-card.mts builds it with core's helpers), checked here against the app's own catalog,
// assets/shopping.catalog.json, which core's generator writes from shopping.actions.mts. Each button presses the same
// handler the shop's own buttons and AppFunctions run; Share list asks the person first, and Place order hands the
// person to the list, where only they can order. With a2ui, the same card arrives as A2UI messages
// (assets/agent-card-a2ui.json): core's A2UI adapter resolves them into the same tree, and each press arrives as A2UI's
// action message, which runs the same handler.
package bot.ouro.shop

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bot.ouro.shop.actions.AddShoppingListItem
import bot.ouro.shop.actions.AddShoppingListItemInput
import bot.ouro.shop.actions.ShareShoppingList
import bot.ouro.shop.actions.ShareShoppingListInput
import bot.ouro.ui.hx.Ouro
import bot.ouro.ui.hx.OuroButton
import bot.ouro.ui.hx.OuroComposedSurface
import bot.ouro.ui.hx.OuroTone
import bot.ouro.ui.hx.OuroVariant
import bot.ouro.ui.hx.rememberOuroComposedState
import bot.ouro.ui.hx.composed.ComposedCatalog
import bot.ouro.ui.hx.composed.ComposedJson
import bot.ouro.ui.hx.composed.OuroA2UI
import bot.ouro.ui.hx.composed.PersonPress

/** What the person's press on the card does: the action's own handler, through the person's door. */
suspend fun pressFromCard(press: PersonPress): String = runAction(press.action, press.input)

/**
 * A press that arrived as A2UI's action message: the same handler, by the action's name, with the message's context.
 * A person-only action never sends one; its button hands the person to the app.
 */
suspend fun pressFromA2UI(message: Map<String, Any?>): String {
    val action = message["action"] as Map<*, *>
    val by = ((action["metadata"] as? Map<*, *>)?.get("extensions") as? Map<*, *>)?.get(OuroA2UI.ACTION_EXTENSION) as? Map<*, *>
    @Suppress("UNCHECKED_CAST")
    val done = runAction(action["name"] as String, (action["context"] as? Map<String, Any?>).orEmpty())
    return "A2UI action from ${action["sourceComponentId"]}, by ${by?.get("by")}: $done"
}

private suspend fun runAction(action: String, input: Map<String, Any?>): String = when (action) {
    AddShoppingListItem.id -> {
        val name = (input["name"] as? String)?.trim().orEmpty()
        require(name.isNotEmpty()) { "Say what to add first." }
        "Added item ${AddShoppingListItem.press(AddShoppingListItemInput(name = name, quantity = (input["quantity"] as? Number)?.toDouble())).id}"
    }
    ShareShoppingList.id -> {
        ShareShoppingList.press(ShareShoppingListInput(with = input["with"] as? String ?: throw IllegalArgumentException("Choose who to share with first.")))
        "Shared with ${input["with"]}"
    }
    else -> throw IllegalArgumentException("$action isn't one of this app's actions.")
}

@Composable
fun ComposedScreen(onHandoff: () -> Unit, a2ui: Boolean = false) {
    val context = LocalContext.current
    fun asset(name: String) = ComposedJson.parse(context.assets.open(name).bufferedReader().use { it.readText() })
    val catalog = remember { ComposedCatalog.parse(context.assets.open("shopping.catalog.json").bufferedReader().use { it.readText() }) }
    val card = rememberOuroComposedState(catalog)
    LaunchedEffect(card) { card.receive(asset(if (a2ui) "agent-card-a2ui.json" else "agent-card.json")) }
    var last by remember { mutableStateOf<String?>(null) }
    val colors = Ouro.look.colors
    BasicText("From your assistant", Modifier.semantics { heading() }, style = TextStyle(color = colors.foreground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
    OuroComposedSurface(
        card,
        onPress = { press -> last = runCatching { pressFromCard(press) }.getOrElse { it.message } },
        onHandoff = { _, _ -> onHandoff() },
        onA2UIAction = { message -> last = runCatching { pressFromA2UI(message) }.getOrElse { it.message } },
    )
    // What the agent's next tool result would do: update the card it sent, keeping what the person entered.
    if (!a2ui) OuroButton("Show the agent's update", onPress = { card.receive(asset("agent-card-update.json")) }, Modifier.testTag("agent-update"), tone = OuroTone.Secondary, variant = OuroVariant.Outline)
    last?.let { BasicText(it, Modifier.testTag("last"), style = TextStyle(color = colors.mutedForeground, fontSize = 14.sp)) }
}
