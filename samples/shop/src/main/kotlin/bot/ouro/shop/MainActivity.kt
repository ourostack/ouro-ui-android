// Five screens, picked by the launch intent so CI can photograph each: the list (people's door), the components in
// every tone and variant, the agent console (agents' door), a card an agent composed for the person (composed), and the
// same card arriving as A2UI messages (a2ui).
//   adb shell am start -n bot.ouro.shop/.MainActivity --es screen agent --ez dark true --ez contrast true --es theme pearl
// A debug build started with --ez photo true also draws its window into files/photo.png: on CI's emulator neither the
// host nor screencap can take a picture.
package bot.ouro.shop

import android.Manifest
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.drawToBitmap
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.shop.actions.AddShoppingListItem
import bot.ouro.shop.actions.AddShoppingListItemInput
import bot.ouro.shop.actions.PlaceGroceryOrder
import bot.ouro.shop.actions.PlaceGroceryOrderInput
import bot.ouro.shop.actions.ShareShoppingList
import bot.ouro.shop.actions.ShareShoppingListInput
import bot.ouro.ui.hx.Ouro
import bot.ouro.ui.hx.OuroBadge
import bot.ouro.ui.hx.OuroButton
import bot.ouro.ui.hx.OuroCard
import bot.ouro.ui.hx.OuroConfirmDialog
import bot.ouro.ui.hx.OuroDialog
import bot.ouro.ui.hx.OuroPresentation
import bot.ouro.ui.hx.OuroSheet
import bot.ouro.ui.hx.OuroFrame
import bot.ouro.ui.hx.OuroItem
import bot.ouro.ui.hx.OuroSortDirection
import bot.ouro.ui.hx.OuroTable
import bot.ouro.ui.hx.OuroTheme
import bot.ouro.ui.hx.OuroTone
import bot.ouro.ui.hx.OuroVariant
import bot.ouro.ui.hx.systemIncreasedContrast
import bot.ouro.ui.hx.tokens.OuroThemeName
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val first = intent.getStringExtra("screen") ?: "shop"
        val theme = OuroThemeName.entries.firstOrNull { it.id == intent.getStringExtra("theme") } ?: OuroThemeName.Pearl
        val dark = if (intent.hasExtra("dark")) intent.getBooleanExtra("dark", false) else null
        val contrast = if (intent.hasExtra("contrast")) intent.getBooleanExtra("contrast", false) else null
        setContent {
            var screen by remember { mutableStateOf(first) }
            OuroTheme(theme, dark = dark ?: androidx.compose.foundation.isSystemInDarkTheme(), increasedContrast = contrast ?: systemIncreasedContrast()) {
                Column(
                    Modifier.fillMaxSize().background(Ouro.look.colors.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (screen) {
                        "gallery" -> Gallery()
                        "agent" -> AgentScreen()
                        "table" -> TableScreen()
                        "overlays" -> OverlaysScreen()
                        // A person-only button hands the person to the app's own screen for it: the list, with Place order.
                        "composed" -> ComposedScreen(onHandoff = { screen = "shop" })
                        "a2ui" -> ComposedScreen(onHandoff = { screen = "shop" }, a2ui = true)
                        else -> ShopScreen()
                    }
                }
            }
        }
        if (intent.getBooleanExtra("photo", false) && (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            window.decorView.postDelayed({
                val picture = window.decorView.drawToBitmap()
                File(filesDir, "photo.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }, 3000)
        }
        // CI moves TalkBack's focus the way a person's swipe does, by asking the view for accessibility focus on a node.
        if (intent.getStringExtra("screen") == "table" && (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, received: Intent) {
                    received.getStringExtra("name")?.let { focusNode(window.decorView, it) }
                }
            }
            registerReceiver(receiver, IntentFilter("bot.ouro.shop.FOCUS"), Context.RECEIVER_EXPORTED)
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private fun focusNode(decor: View, prefix: String) {
    fun root(view: View): RootForTest? =
        view as? RootForTest ?: (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { root(group.getChildAt(it)) } }
    fun find(node: SemanticsNode): SemanticsNode? =
        if (node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.let { it == prefix || it.startsWith("$prefix,") } == true) node
        else node.children.firstNotNullOfOrNull { find(it) }
    val host = root(decor) ?: return android.util.Log.i("OuroFocus", "no root")
    val target = find(host.semanticsOwner.rootSemanticsNode) ?: return android.util.Log.i("OuroFocus", "no node for $prefix")
    val done = (host as View).accessibilityNodeProvider?.performAction(target.id, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
    android.util.Log.i("OuroFocus", "$prefix -> ${target.config.getOrNull(SemanticsProperties.ContentDescription)} id=${target.id} done=$done")
}

/** A sortable grid and a sorted one, for TalkBack to read on CI. */
@Composable
private fun TableScreen() {
    val cols = listOf("Recipe", "Time", "Serves")
    val data = listOf(listOf("Braised short ribs", "4 h", "6"), listOf("Herb salad", "10 min", "2"), listOf("Rye bread", "18 h", "8"))
    OuroTable("Recipes", cols, data.reversed(), sortable = listOf("Recipe", "Serves"), sortedBy = "Serves", sortDirection = OuroSortDirection.Descending)
}

@Composable
private fun Heading(text: String) {
    BasicText(text, style = TextStyle(color = Ouro.look.colors.foreground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
}

/** Dialog, Sheet and ConfirmDialog in their own windows: each opens from a button and closes from Close, Cancel or Back. */
@Composable
private fun OverlaysScreen() {
    var open by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf("Nothing yet") }
    Heading("Overlays")
    OuroButton("Edit recipe", onPress = { open = "dialog" }, Modifier.testTag("open-dialog"))
    OuroButton("Save to cookbook", onPress = { open = "sheet" }, Modifier.testTag("open-sheet"))
    OuroButton("Delete recipe", onPress = { open = "confirm" }, Modifier.testTag("open-confirm"), tone = OuroTone.Destructive)
    BasicText(last, Modifier.testTag("overlay-last"), style = TextStyle(color = Ouro.look.colors.mutedForeground, fontSize = 14.sp))
    OuroDialog(open == "dialog", onClose = { open = null; last = "Dialog closed: $it" }, title = "Edit recipe", description = "Change the title and the notes.", presentation = OuroPresentation.Dialog)
    OuroSheet(open == "sheet", onClose = { open = null; last = "Sheet closed: $it" }, title = "Save to cookbook")
    OuroConfirmDialog(open == "confirm", onConfirm = { open = null; last = "Deleted" }, onClose = { open = null; last = "Kept" }, title = "Delete recipe?", confirmLabel = "Delete", description = "This can't be undone.", tone = OuroTone.Destructive)
}

@Composable
private fun ShopScreen() {
    var last by remember { mutableStateOf<String?>(null) }
    Heading("Shopping list")
    OuroCard(title = "This week", description = "${Shop.items.size} things" + if (Shop.sharedWith.isNotEmpty()) ", shared with ${Shop.sharedWith.joinToString()}" else "") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (item in Shop.items) OuroItem(item.name, Modifier.testTag("item-${item.id}"), description = item.quantity?.let { "x ${it.toInt()}" })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OuroButton(AddShoppingListItem, input = { AddShoppingListItemInput(name = "Basil") }, onResult = { last = it.fold({ "Added item ${it.id}" }, { it.message }) })
        OuroButton(ShareShoppingList, input = { ShareShoppingListInput(with = "sam@example.com") }, variant = OuroVariant.Outline, tone = OuroTone.Secondary, onResult = { last = it.fold({ "Shared" }, { it.message }) })
    }
    OuroButton(PlaceGroceryOrder, input = { PlaceGroceryOrderInput }, tone = OuroTone.Success, onResult = { last = it.fold({ "Order placed" }, { it.message }) })
    if (Shop.ordered.value) OuroBadge("Ordered", tone = OuroTone.Success, variant = OuroVariant.Soft)
    last?.let { BasicText(it, Modifier.testTag("last"), style = TextStyle(color = Ouro.look.colors.mutedForeground, fontSize = 14.sp)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gallery() {
    val look = Ouro.look
    Heading("Ouro HX: ${look.name.id}${if (look.dark) ", dark" else ""}${if (look.increasedContrast) ", increased contrast" else ""}")
    for (v in OuroVariant.entries) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in OuroTone.entries) OuroButton(t.name, onPress = {}, tone = t, variant = v)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in listOf(OuroVariant.Solid, OuroVariant.Soft, OuroVariant.Outline)) for (t in OuroTone.entries) OuroBadge(t.name, tone = t, variant = v)
    }
    OuroCard(title = "Pantry", description = "What you have on hand", action = { OuroButton("Edit", onPress = {}, tone = OuroTone.Secondary, variant = OuroVariant.Soft) }) {
        BasicText("Flour, rice, black beans.", style = TextStyle(color = look.colors.cardForeground, fontSize = 16.sp))
    }
    OuroItem("Braised short ribs", description = "3 hours, serves six", actions = { OuroBadge("New", tone = OuroTone.Primary, variant = OuroVariant.Soft) }, onPress = {})
    OuroItem("Pantry list", description = "12 things", variant = OuroFrame.Outline, onPress = {})
}

@Composable
private fun AgentScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<AgentLine>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val run: () -> Unit = {
        running = true
        scope.launch {
            lines = runCatching { askAsAgent(context) { lines = it } }.getOrElse { listOf(AgentLine("AppFunctions", "${it.javaClass.simpleName}: ${it.message}", OuroTone.Destructive)) }
            running = false
        }
    }
    LaunchedEffect(Unit) { run() }
    Heading("Agent console")
    BasicText(
        "This app calls its own AppFunctions, as an assistant would. Add runs; share asks the person first (Allow runs it, Don't or no answer refuses it); place order is person-only, so agents have no door to it.",
        style = TextStyle(color = Ouro.look.colors.mutedForeground, fontSize = 14.sp),
    )
    OuroButton("Ask again", onPress = run, busy = running, variant = OuroVariant.Outline, tone = OuroTone.Secondary)
    // On Android 13 and later an app in the background can ask the person only with a notification, which needs this permission.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        BasicText("When this app isn't on screen, share asks with a notification.", style = TextStyle(color = Ouro.look.colors.mutedForeground, fontSize = 14.sp))
        OuroButton("Allow notifications", onPress = { (context as Activity).requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }, variant = OuroVariant.Outline, tone = OuroTone.Secondary)
    }
    for ((i, line) in lines.withIndex()) {
        OuroCard(Modifier.testTag("agent-$i"), title = line.call, description = line.answer, action = {
            OuroBadge(when { line.asking -> "asking"; line.tone == OuroTone.Success -> "ran"; line.tone == OuroTone.Destructive -> "refused"; line.tone == OuroTone.Warning -> "person-only"; else -> "found" }, tone = line.tone, variant = OuroVariant.Soft)
        })
    }
}
