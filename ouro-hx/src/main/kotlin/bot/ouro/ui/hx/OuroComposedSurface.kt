// Ouro's composed tree drawn with Ouro HX, as web's ComposedSurface and Apple's draw it: the UI an agent composes for a
// person, every button one of the app's declared actions. The tree comes from bot.ouro.ui.hx.composed (core's
// composed.mts, ported); this file draws it. Android draws Ouro's tree itself and reads no A2UI messages.
package bot.ouro.ui.hx

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import bot.ouro.ui.hx.composed.ComposedAnswers
import bot.ouro.ui.hx.composed.ComposedCatalog
import bot.ouro.ui.hx.composed.ComposedNode
import bot.ouro.ui.hx.composed.LabelContext
import bot.ouro.ui.hx.composed.OuroComposed
import bot.ouro.ui.hx.composed.PersonPress
import bot.ouro.ui.hx.composed.TreeProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Draws a composed Image: the picture at [url], described by [description] for TalkBack, filling [modifier]'s frame. */
typealias OuroComposedImage = @Composable (url: String, description: String, modifier: Modifier) -> Unit

/**
 * Draws Ouro's composed tree with Ouro HX: the UI an agent composed for a person, such as an MCP tool result's
 * `structuredContent.ouro` read with [OuroComposed.read]. Every button is one of the app's declared actions in
 * [catalog], and pressing it follows the action's consent class:
 *
 * - read and write call [onPress] at once, the button busy until it returns;
 * - consequential opens a confirm sheet that shows each prepared value under its label (the id small beside a name),
 *   built from [catalog] and the input, and calls [onPress] only when the person confirms;
 * - person-only calls [onHandoff] with the action and the catalog's `how`, so the app takes the person to its own
 *   screen, and never presses.
 *
 * The fields (TextField, Checkbox, Select) start from [data] and write the person's answers into the surface's own
 * copy; a button bound to a field sends what the person entered. When [tree] or [data] changes, as an agent's update
 * changes them, what the person entered stays, except at a pointer whose value the new [data] changed, or in a list
 * item it changed: there the agent's value wins. To apply an agent's update for you, use the [OuroComposedState]
 * overload.
 *
 * The surface checks [tree] against [catalog] first and draws none of a tree it refuses: it shows a short alert
 * instead ("This card can't be shown"), and [onRefused] gets the problems.
 * Nothing in it lets an agent press: an agent runs an action through its own AppFunction, and every press here is the
 * person's (`PersonPress.by` is "person"). [onPress] handles its own errors: one it throws ends the app as any
 * uncaught exception does. Use HX's components directly when your own code, not an agent, lays out
 * the screen.
 *
 * ```kotlin
 * when (val read = OuroComposed.read(catalog, toolResult)) {
 *     is OuroComposed.Read.Ready -> OuroComposedSurface(read.tree, catalog, data = read.data,
 *         onPress = { press -> callTool(press.action, press.input) },
 *         onHandoff = { action, _ -> openScreenFor(action) })
 *     is OuroComposed.Read.Update, is OuroComposed.Read.Refused -> Unit   // an OuroComposedState applies updates for you
 * }
 * ```
 *
 * [label] is the app's own word for an id field on a confirm sheet (one with `shownAs` in the catalog), such as a
 * recipe's title for `rcp_lemon_orzo`; it wins over the agent's. Every other row shows exactly the value sent. [image] draws an Image; the default fetches https URLs only, which needs the INTERNET permission.
 */
@Composable
fun OuroComposedSurface(
    tree: ComposedNode,
    catalog: ComposedCatalog,
    onPress: suspend (PersonPress) -> Unit,
    onHandoff: (action: String, how: String) -> Unit,
    modifier: Modifier = Modifier,
    data: Map<String, Any?> = emptyMap(),
    label: ((LabelContext) -> String?)? = null,
    image: OuroComposedImage = { url, description, m -> OuroRemoteImage(url, description, m) },
    onRefused: (List<TreeProblem>) -> Unit = {},
) {
    val problems = remember(tree, catalog) { OuroComposed.validateTree(catalog, tree) }
    if (problems.isNotEmpty()) {
        LaunchedEffect(problems) { onRefused(problems) }
        CantShow(modifier)
        return
    }
    var answers by remember { mutableStateOf(ComposedAnswers.of(data)) }
    // New data from the agent: keep what the person entered, except where the agent changed it.
    if (answers.data !== data) answers = answers.carry(data)
    ComposedBody(tree, catalog, answers.current, { pointer, value -> answers = answers.write(pointer, value) }, onPress, onHandoff, modifier, label, image)
}

/**
 * A composed card an agent sends and then updates, for [OuroComposedSurface]: the tree it shows, the data model the
 * agent last sent, what the person entered, and why the last tree, update or tool result couldn't be shown.
 *
 * ```kotlin
 * val card = rememberOuroComposedState(catalog)
 * LaunchedEffect(result) { card.receive(result) }   // a tool result: structuredContent.ouro's tree, or its update
 * OuroComposedSurface(card, onPress = { press -> callTool(press.action, press.input) }, onHandoff = { a, _ -> open(a) })
 * ```
 *
 * An update ([update], or a tool result's `structuredContent.ouro.update`) applies with core's `applyTreeUpdate`: all
 * of it or none. While one can't apply, the surface shows the short alert, and the last tree that applied, with what the
 * person entered, stays for the next update. A tool result that carries a tree, even one refused, takes the card's
 * place, so an update never changes a card the agent has since replaced.
 *
 * [rememberOuroComposedState] lasts as long as the composition. To keep the card and the person's answers when the
 * Activity is recreated (a rotation, a theme change), hold an `OuroComposedState` in your ViewModel instead, or
 * replay the last whole tree, then each update after it.
 */
@Stable
class OuroComposedState(val catalog: ComposedCatalog) {
    /** The tree shown, or null before the first, or after a tree that was refused. */
    var tree by mutableStateOf<ComposedNode?>(null)
        private set

    /** The data model the agent last sent. */
    var data by mutableStateOf<Map<String, Any?>>(emptyMap())
        private set

    /** Why the last tree, update or result couldn't be shown; empty when the tree shows. */
    var problems by mutableStateOf<List<TreeProblem>>(emptyList())
        private set

    internal var answers by mutableStateOf(ComposedAnswers.of(emptyMap<String, Any?>()))

    /** What the person has entered so far, over the agent's data: what a bound button would send. */
    val entered: Any? get() = answers.current

    /** A whole tree and its data, in place of the card: what the person entered stays where the agent left it. */
    fun show(tree: ComposedNode, data: Map<String, Any?> = emptyMap()): List<TreeProblem> {
        val found = OuroComposed.validateTree(catalog, tree)
        if (found.isNotEmpty()) {
            this.tree = null
            problems = found
            return found
        }
        answers = if (this.tree != null) answers.carry(data) else ComposedAnswers.of(data)
        this.tree = tree
        this.data = data
        problems = emptyList()
        return problems
    }

    /** An update to the tree shown (core's `TreeUpdate`, `{ nodes?, data? }`): applied whole, or not at all. */
    fun update(update: Any?): List<TreeProblem> {
        val shown = tree
        if (shown == null) {
            problems = listOf(TreeProblem("/update", "An update changes the composed tree the view shows, and it shows none yet; send the whole tree first, in structuredContent.ouro.tree."))
            return problems
        }
        when (val applied = OuroComposed.applyTreeUpdate(catalog, shown, data, update)) {
            is OuroComposed.Applied.Problems -> problems = applied.problems
            is OuroComposed.Applied.Updated -> {
                val next = applied.data.orEmpty()
                answers = answers.carry(next)
                tree = applied.tree
                data = next
                problems = emptyList()
            }
        }
        return problems
    }

    /** A tool result ([OuroComposed.read]): its tree takes the card's place, or its update applies to it. */
    fun receive(result: Any?): List<TreeProblem> = when (val read = OuroComposed.read(catalog, result)) {
        is OuroComposed.Read.Ready -> show(read.tree, read.data)
        is OuroComposed.Read.Update -> update(read.update)
        is OuroComposed.Read.Refused -> {
            // A refused result that carried a tree still takes the card's place: no later update may change the old one.
            if (carriesTree(result)) {
                tree = null
                answers = ComposedAnswers.of(emptyMap<String, Any?>())
            }
            problems = read.problems
            problems
        }
    }

    private fun carriesTree(result: Any?): Boolean {
        val r = result as? Map<*, *> ?: return false
        val structured = r["structuredContent"] as? Map<*, *> ?: r
        return ((structured["ouro"] as? Map<*, *>) ?: structured).containsKey("tree")
    }
}

/** A card the agent sends and updates, kept across recompositions: see [OuroComposedState]. */
@Composable
fun rememberOuroComposedState(catalog: ComposedCatalog): OuroComposedState = remember(catalog) { OuroComposedState(catalog) }

/**
 * Draws the card [state] holds, as the other overload does, and applies the agent's updates to it with core's
 * `applyTreeUpdate`. While a tree, update or result can't be shown, it shows a short alert ("This card can't be
 * shown") and [onRefused] gets the problems; what the person entered stays for the next update that applies.
 */
@Composable
fun OuroComposedSurface(
    state: OuroComposedState,
    onPress: suspend (PersonPress) -> Unit,
    onHandoff: (action: String, how: String) -> Unit,
    modifier: Modifier = Modifier,
    label: ((LabelContext) -> String?)? = null,
    image: OuroComposedImage = { url, description, m -> OuroRemoteImage(url, description, m) },
    onRefused: (List<TreeProblem>) -> Unit = {},
) {
    val problems = state.problems
    if (problems.isNotEmpty()) {
        LaunchedEffect(problems) { onRefused(problems) }
        CantShow(modifier)
        return
    }
    val tree = state.tree ?: return
    ComposedBody(tree, state.catalog, state.answers.current, { pointer, value -> state.answers = state.answers.write(pointer, value) }, onPress, onHandoff, modifier, label, image)
}

/** The short alert a card that fails the checks shows, as web's: the person learns nothing from the problems. */
@Composable
private fun CantShow(modifier: Modifier) {
    val look = Ouro.look
    val paint = OuroPaint.resolve(look.colors, OuroTone.Destructive, OuroVariant.Soft, look.textContrast, Ouro.surface)
    val body = OuroColor.readable(look.colors.foreground, paint.fill, look.textContrast)
    val shape = RoundedCornerShape(look.radius)
    Column(
        modifier.testTag("composed.refused").fillMaxWidth().background(paint.fill, shape).border(1.dp, paint.edge, shape).padding(16.dp).semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            ouroReport = report("kind" to "composed-alert", "label" to OuroColor.hex(paint.label), "text" to OuroColor.hex(body), "behind" to OuroColor.hex(paint.fill))
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("This card can't be shown", style = TextStyle(color = paint.label, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
        BasicText("It didn't pass the checks for composed UI.", style = TextStyle(color = body, fontSize = 14.sp, lineHeight = 20.sp))
    }
}

@Composable
private fun ComposedBody(
    tree: ComposedNode,
    catalog: ComposedCatalog,
    current: Any?,
    write: (String, Any?) -> Unit,
    onPress: suspend (PersonPress) -> Unit,
    onHandoff: (action: String, how: String) -> Unit,
    modifier: Modifier,
    label: ((LabelContext) -> String?)?,
    image: OuroComposedImage,
) {
    val context = ComposedContext(catalog, current, write, onPress, onHandoff, label, image)
    Box(modifier.semantics { ouroReport = report("kind" to "composed-surface") }) { Node(tree, context) }
}

private class ComposedContext(
    val catalog: ComposedCatalog,
    val data: Any?,
    val write: (String, Any?) -> Unit,
    val onPress: suspend (PersonPress) -> Unit,
    val onHandoff: (String, String) -> Unit,
    val label: ((LabelContext) -> String?)?,
    val image: OuroComposedImage,
)

private fun Map<String, Any?>.text(key: String): String = this[key] as? String ?: ""

private val TONES = mapOf("info" to OuroTone.Primary, "success" to OuroTone.Success, "warning" to OuroTone.Warning, "danger" to OuroTone.Destructive)
private val GAPS = mapOf("none" to 0.dp, "small" to 8.dp, "medium" to 16.dp, "large" to 24.dp)
private val ASPECTS = mapOf("landscape" to 4f / 3f, "square" to 1f, "portrait" to 3f / 4f, "wide" to 16f / 9f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Node(node: ComposedNode, ctx: ComposedContext) {
    val p = node.props
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val tag = Modifier.testTag("composed.${node.id}")
    when (node.component) {
        "Card" -> OuroCard(tag) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { Children(node, ctx) }
        }
        "Stack" -> {
            val gap = GAPS[p["gap"]] ?: 16.dp
            if (p["direction"] == "horizontal") {
                FlowRow(tag, horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) { Children(node, ctx) }
            } else {
                Column(tag, verticalArrangement = Arrangement.spacedBy(gap)) { Children(node, ctx) }
            }
        }
        "Heading" -> {
            val size = when ((p["level"] as? Number)?.toInt()) {
                1 -> 24.sp
                3 -> 17.sp
                else -> 20.sp
            }
            val color = OuroColor.readable(c.foreground, behind, look.textContrast)
            BasicText(
                p.text("text"),
                tag.semantics {
                    heading()
                    ouroReport = report("kind" to "composed-heading", "text" to OuroColor.hex(color), "behind" to OuroColor.hex(behind))
                },
                style = TextStyle(color = color, fontSize = size, lineHeight = size * 1.3f, fontWeight = FontWeight.SemiBold),
            )
        }
        "Text" -> {
            val color = OuroColor.readable(if (p["tone"] == "muted") c.mutedForeground else c.foreground, behind, look.textContrast)
            BasicText(
                p.text("text"),
                tag.semantics { ouroReport = report("kind" to "composed-text", "text" to OuroColor.hex(color), "behind" to OuroColor.hex(behind)) },
                style = TextStyle(color = color, fontSize = 16.sp, lineHeight = 22.sp),
            )
        }
        "Image" -> ctx.image(
            p.text("url"),
            p.text("description"),
            tag.fillMaxWidth().aspectRatio(ASPECTS[p["aspect"]] ?: (4f / 3f)).clip(RoundedCornerShape(look.radius)),
        )
        "Badge" -> OuroBadge(p.text("text"), tag, tone = TONES[p["tone"]] ?: OuroTone.Secondary)
        "List" -> ComposedList(node, tag)
        "DescriptionList" -> OuroDescriptionList(
            (p["items"] as? List<*>).orEmpty().map { val m = it as Map<*, *>; OuroDescriptionItem(m["term"].toString(), m["detail"].toString()) },
            tag,
        )
        "Separator" -> OuroSeparator(tag)
        "Alert" -> ComposedAlert(node, tag)
        "TextField" -> ComposedTextField(node, ctx, tag)
        "Checkbox" -> {
            val pointer = p.text("value")
            OuroCheckbox(
                p.text("label"),
                checked = OuroComposed.getPointer(ctx.data, pointer) == true,
                onCheckedChange = { ctx.write(pointer, it) },
                tag,
                description = p["hint"] as? String,
            )
        }
        "Select" -> ComposedSelect(node, ctx, tag)
        // A button's test tag names its action, as OuroButton(action) does; its id stays in the tree.
        "ActionButton" -> ComposedButton(node, ctx, Modifier)
    }
}

/** A node's children, each keyed by its place and id, so a new tree never hands one button's state to another. */
@Composable
private fun Children(node: ComposedNode, ctx: ComposedContext) {
    node.children.forEachIndexed { i, child -> key(i, child.id, child.component) { Node(child, ctx) } }
}

/** A list of short items, numbered or bulleted, that TalkBack hears as a list of so many items. */
@Composable
private fun ComposedList(node: ComposedNode, modifier: Modifier) {
    val look = Ouro.look
    val items = (node.props["items"] as? List<*>).orEmpty().map { it.toString() }
    val ordered = node.props["ordered"] == true
    val behind = Ouro.surface
    val textColor = OuroColor.readable(look.colors.foreground, behind, look.textContrast)
    val markerColor = OuroColor.readable(look.colors.mutedForeground, behind, look.textContrast)
    Column(
        modifier.semantics {
            collectionInfo = CollectionInfo(rowCount = items.size, columnCount = 1)
            ouroReport = report("kind" to "composed-list", "text" to OuroColor.hex(textColor), "label" to OuroColor.hex(markerColor), "behind" to OuroColor.hex(behind))
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, item ->
            Row(Modifier.semantics(mergeDescendants = true) { collectionItemInfo = CollectionItemInfo(i, 1, 0, 1) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(if (ordered) "${i + 1}." else "•", Modifier.widthIn(min = 20.dp).clearAndSetSemantics {}, style = TextStyle(color = markerColor, fontSize = 16.sp, lineHeight = 22.sp))
                BasicText(item, Modifier.weight(1f), style = TextStyle(color = textColor, fontSize = 16.sp, lineHeight = 22.sp))
            }
        }
    }
}

/** A message the person should notice, tinted by its tone: its title, then what to do. */
@Composable
private fun ComposedAlert(node: ComposedNode, modifier: Modifier) {
    val look = Ouro.look
    val paint = OuroPaint.resolve(look.colors, TONES[node.props["tone"]] ?: OuroTone.Primary, OuroVariant.Soft, look.textContrast, Ouro.surface)
    val body = OuroColor.readable(look.colors.foreground, paint.fill, look.textContrast)
    val shape = RoundedCornerShape(look.radius)
    Column(
        modifier.fillMaxWidth().background(paint.fill, shape).border(1.dp, paint.edge, shape).padding(16.dp).semantics(mergeDescendants = true) {
            ouroReport = report("kind" to "composed-alert", "label" to OuroColor.hex(paint.label), "text" to OuroColor.hex(body), "behind" to OuroColor.hex(paint.fill))
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(node.props.text("title"), style = TextStyle(color = paint.label, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
        (node.props["text"] as? String)?.let { BasicText(it, style = TextStyle(color = body, fontSize = 14.sp, lineHeight = 20.sp)) }
    }
}

/** A field the person types into: words, or a number written as one (or removed when they empty it). */
@Composable
private fun ComposedTextField(node: ComposedNode, ctx: ComposedContext, modifier: Modifier) {
    val p = node.props
    val pointer = p.text("value")
    val number = p["type"] == "number"
    val held = OuroComposed.getPointer(ctx.data, pointer)
    // What the person is typing, kept as they type it ("4." on the way to "4.5"), while the data holds the number.
    var typed by remember(pointer) { mutableStateOf(OuroComposed.asText(held)) }
    if (OuroComposed.asText(held) != typed && (!number || typed.toDoubleOrNull() != (held as? Number)?.toDouble())) typed = OuroComposed.asText(held)
    OuroTextField(
        p.text("label"),
        value = typed,
        onValueChange = { text ->
            typed = text
            ctx.write(pointer, if (!number) text else text.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull() })
        },
        modifier = modifier,
        description = p["hint"] as? String,
        keyboardType = if (number) KeyboardType.Decimal else null,
        minLines = if (p["multiline"] == true) 3 else 1,
    )
}

/** One choice from a short list: a radio button per option, each a 48 dp row, under the question. */
@Composable
private fun ComposedSelect(node: ComposedNode, ctx: ComposedContext, modifier: Modifier) {
    val look = Ouro.look
    val c = look.colors
    val p = node.props
    val pointer = p.text("value")
    val chosen = OuroComposed.getPointer(ctx.data, pointer) as? String
    val behind = Ouro.surface
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val muted = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val edge = OuroColor.push(c.mutedForeground, behind, OuroColor.INCREASED_LINE_CONTRAST)
    val fill = OuroColor.push(c.primary, behind, OuroColor.INCREASED_LINE_CONTRAST)
    Column(
        modifier.semantics {
            ouroReport = report("kind" to "composed-select", "label" to OuroColor.hex(labelColor), "description" to OuroColor.hex(muted), "edge" to OuroColor.hex(edge), "behind" to OuroColor.hex(behind))
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(p.text("label"), Modifier.semantics { heading() }, style = TextStyle(color = labelColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium))
        (p["hint"] as? String)?.let { BasicText(it, style = TextStyle(color = muted, fontSize = 14.sp, lineHeight = 20.sp)) }
        Column(Modifier.selectableGroup()) {
            for (option in (p["options"] as? List<*>).orEmpty().map { it.toString() }) {
                val selected = option == chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = OuroMinTarget)
                        .selectable(selected = selected, role = Role.RadioButton) { ctx.write(pointer, option) }
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).border(1.5.dp, if (selected) fill else edge, CircleShape), contentAlignment = Alignment.Center) {
                        if (selected) Box(Modifier.size(10.dp).background(fill, CircleShape))
                    }
                    BasicText(option, Modifier.weight(1f), style = TextStyle(color = labelColor, fontSize = 16.sp, lineHeight = 22.sp))
                }
            }
        }
    }
}

/**
 * An ActionButton: its label and what pressing it does come from the catalog. Primary emphasis is solid; the rest
 * are soft and secondary, as on web. A person-only button carries an arrow out to the app's own screen.
 */
@Composable
private fun ComposedButton(node: ComposedNode, ctx: ComposedContext, modifier: Modifier) {
    val p = node.props
    val action = p.text("action")
    val press = p["press"]
    val scope = rememberCoroutineScope()
    var busy by remember(node) { mutableStateOf(false) }
    // The sheet and the press it confirms are made together, when the person presses, so confirming sends exactly
    // what the sheet shows. A new tree or data model closes it.
    var pending by remember(node, ctx.data) { mutableStateOf<Pair<OuroComposed.ConfirmSheet, PersonPress>?>(null) }
    val primary = p["emphasis"] == "primary"
    val tone = if (primary) OuroTone.Primary else OuroTone.Secondary
    val variant = if (primary) OuroVariant.Solid else OuroVariant.Soft
    val look = Ouro.look
    val words = OuroPaint.resolve(look.colors, tone, variant, look.textContrast, Ouro.surface).label
    val send: (PersonPress) -> Unit = { pressed ->
        busy = true
        scope.launch {
            try {
                ctx.onPress(pressed)
            } finally {
                busy = false
            }
        }
    }
    OuroButton(
        label = p.text("label"),
        onPress = {
            when (press) {
                "handoff" -> ctx.onHandoff(action, p.text("handoff"))
                "confirm" -> pending = OuroComposed.confirmSheet(ctx.catalog, node, ctx.data, ctx.label)?.let { it to OuroComposed.personPress(node, true, ctx.data) }
                else -> if (!busy) send(OuroComposed.personPress(node, false, ctx.data))
            }
        },
        modifier = modifier.testTag("action.$action").ouroAction(action, p.text("consent")),
        tone = tone,
        variant = variant,
        busy = busy,
        hint = ctx.catalog.actions[action]?.description,
        icon = if (press == "handoff") ({ HandoffArrow(words) }) else null,
    )
    pending?.let { (sheet, confirmed) ->
        ConfirmSheet(sheet, onConfirm = {
            pending = null
            if (!busy) send(confirmed)
        }, onCancel = { pending = null })
    }
}

/** An arrow out to the app's own screen, on a person-only action's button. */
@Composable
private fun HandoffArrow(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = 2.dp.toPx()
        val a = Offset(size.width * 0.25f, size.height * 0.75f)
        val b = Offset(size.width * 0.75f, size.height * 0.25f)
        drawLine(color, a, b, w, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.35f, b.y), b, w, StrokeCap.Round)
        drawLine(color, b, Offset(b.x, size.height * 0.65f), w, StrokeCap.Round)
    }
}

/**
 * The person's last look before a consequential action runs: its label as a question, its description, each value
 * the agent prepared under its label (the id small beside a name that stands in for it), then Cancel and the action.
 */
@Composable
private fun ConfirmSheet(sheet: OuroComposed.ConfirmSheet, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        val look = Ouro.look
        val c = look.colors
        val strong = OuroColor.readable(c.popoverForeground, c.popover, look.textContrast)
        val muted = OuroColor.readable(c.mutedForeground, c.popover, look.textContrast)
        val shape = RoundedCornerShape(look.radius * 1.5f)
        Column(
            Modifier
                .testTag("composed.confirm")
                .semantics { paneTitle = "${sheet.title}?" }
                .fillMaxWidth()
                .background(c.popover, shape)
                .border(1.dp, c.border, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("${sheet.title}?", Modifier.semantics { heading() }, style = TextStyle(color = strong, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold))
            BasicText(sheet.description, style = TextStyle(color = muted, fontSize = 16.sp, lineHeight = 22.sp))
            for (row in sheet.rows) {
                Column(Modifier.testTag("composed.confirm.row").semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(row.label, style = TextStyle(color = muted, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        BasicText(row.value, Modifier.weight(1f, fill = false), style = TextStyle(color = strong, fontSize = 16.sp, lineHeight = 22.sp))
                        row.raw?.let { BasicText(it, style = TextStyle(color = muted, fontSize = 12.sp, lineHeight = 16.sp)) }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OuroButton("Cancel", onPress = onCancel, modifier = Modifier.testTag("composed.confirm.cancel"), tone = OuroTone.Secondary, variant = OuroVariant.Soft)
                OuroButton(sheet.title, onPress = onConfirm, modifier = Modifier.testTag("composed.confirm.press"))
            }
        }
    }
}

/** The most the default Image downloads, and the longest side it decodes to: an agent picks the URL. */
private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
private const val MAX_IMAGE_SIDE = 2048

/**
 * The default Image: fetched from an https URL off the main thread and drawn to fill its frame, described for TalkBack.
 * It downloads at most 10 MB and decodes to at most 2048 px a side. Until it arrives, or when it can't, the frame shows
 * the theme's muted surface. Any other scheme is never fetched.
 */
@Composable
fun OuroRemoteImage(url: String, description: String, modifier: Modifier = Modifier) {
    val picture by produceState<ImageBitmap?>(null, url) {
        value = if (!url.startsWith("https://")) null else withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val bytes = try {
                    connection.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            // An agent picks the URL: a picture past the cap is never drawn.
                            if (out.size() + n > MAX_IMAGE_BYTES) return@runCatching null
                            out.write(chunk, 0, n)
                        }
                        out.toByteArray()
                    }
                } finally {
                    connection.disconnect()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > MAX_IMAGE_SIDE || bounds.outHeight / sample > MAX_IMAGE_SIDE) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val described = Modifier.semantics {
        contentDescription = description
        role = Role.Image
    }
    val shown = picture
    if (shown != null) Image(shown, contentDescription = description, modifier = modifier.then(described), contentScale = ContentScale.Crop)
    else Box(modifier.background(Ouro.look.colors.muted).then(described))
}
