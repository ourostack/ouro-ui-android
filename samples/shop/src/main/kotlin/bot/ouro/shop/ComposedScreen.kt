// A card an agent composed for the person, drawn with Ouro HX: the tool result in assets/agent-card.json carries Ouro's
// tree (samples/shop/agent-card.mts builds it with core's helpers), checked here against the app's own catalog,
// assets/shopping.catalog.json, which core's generator writes from shopping.actions.mts. Each button presses the same
// handler the shop's own buttons and AppFunctions run; Share list asks the person first, and Place order hands the
// person to the list, where only they can order.
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
import bot.ouro.ui.hx.composed.PersonPress

/** What the person's press on the card does: the action's own handler, through the person's door. */
suspend fun pressFromCard(press: PersonPress): String = when (press.action) {
    AddShoppingListItem.id -> {
        val name = (press.input["name"] as? String)?.trim().orEmpty()
        require(name.isNotEmpty()) { "Say what to add first." }
        "Added item ${AddShoppingListItem.press(AddShoppingListItemInput(name = name, quantity = (press.input["quantity"] as? Number)?.toDouble())).id}"
    }
    ShareShoppingList.id -> {
        ShareShoppingList.press(ShareShoppingListInput(with = press.input["with"] as? String ?: throw IllegalArgumentException("Choose who to share with first.")))
        "Shared with ${press.input["with"]}"
    }
    else -> throw IllegalArgumentException("${press.action} isn't one of this app's actions.")
}

@Composable
fun ComposedScreen(onHandoff: () -> Unit) {
    val context = LocalContext.current
    fun asset(name: String) = ComposedJson.parse(context.assets.open(name).bufferedReader().use { it.readText() })
    val catalog = remember { ComposedCatalog.parse(context.assets.open("shopping.catalog.json").bufferedReader().use { it.readText() }) }
    val card = rememberOuroComposedState(catalog)
    LaunchedEffect(card) { card.receive(asset("agent-card.json")) }
    var last by remember { mutableStateOf<String?>(null) }
    val colors = Ouro.look.colors
    BasicText("From your assistant", Modifier.semantics { heading() }, style = TextStyle(color = colors.foreground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
    OuroComposedSurface(
        card,
        onPress = { press -> last = runCatching { pressFromCard(press) }.getOrElse { it.message } },
        onHandoff = { _, _ -> onHandoff() },
    )
    // What the agent's next tool result would do: update the card it sent, keeping what the person entered.
    OuroButton("Show the agent's update", onPress = { card.receive(asset("agent-card-update.json")) }, Modifier.testTag("agent-update"), tone = OuroTone.Secondary, variant = OuroVariant.Outline)
    last?.let { BasicText(it, Modifier.testTag("last"), style = TextStyle(color = colors.mutedForeground, fontSize = 14.sp)) }
}
