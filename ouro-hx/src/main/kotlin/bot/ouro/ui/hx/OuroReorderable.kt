// Core's Reorderable spec (vendor/core/components/reorderable.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import bot.ouro.ui.hx.tokens.OuroTokens

/** One row of an [OuroReorderable]: a stable [id], the [label] its handle and announcements use, and what it shows. */
class OuroReorderRow(val id: String, val label: String, val content: @Composable () -> Unit)

/** A row that moved: its [id], and where it was ([from]) and is now ([to]), counting from 0. */
class OuroReorderMove(val id: String, val from: Int, val to: Int)

private enum class Step(val label: String) { Up("Move up"), Down("Move down"), Top("Move to top"), Bottom("Move to bottom") }

private fun Step.target(from: Int, count: Int) = when (this) {
    Step.Up -> from - 1
    Step.Down -> from + 1
    Step.Top -> 0
    Step.Bottom -> count - 1
}

/**
 * A list the person puts in order, with a handle on each row, so dragging is never the only way. Pass [rows] in their
 * current order and update them from [onReorder], which gets the new order of ids and the move. A long press on a row
 * picks it up and the others make room; with reduce motion they jump aside. Each handle is a button named "Reorder"
 * and its row that opens a menu of Move up, Move down, Move to top and Move to bottom, and each row carries the same
 * moves as TalkBack custom actions. A handle is a tab stop: Space picks the row up, the arrow keys move it, Space
 * drops it and Escape puts it back. A move is announced with its position ("Position 2 of 5"). [disabled] locks the
 * order; [disabledReason] ("Sorted by date") locks it too and is read with each handle.
 * `OuroReorderable("Playlist", rows, onReorder = { ids, _ -> order = ids })`.
 */
@Composable
fun OuroReorderable(
    label: String,
    rows: List<OuroReorderRow>,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
    disabledReason: String? = null,
    onReorder: (ids: List<String>, move: OuroReorderMove) -> Unit = { _, _ -> },
) {
    val look = Ouro.look
    val c = look.colors
    val under = Ouro.surface
    val locked = disabled || !disabledReason.isNullOrBlank()
    val handleColor = OuroColor.readable(c.mutedForeground, under, look.textContrast)
    val foreground = OuroColor.readable(c.foreground, under, look.textContrast)
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val current by rememberUpdatedState(rows)
    val heights = remember { mutableStateMapOf<String, Int>() }
    var lifted by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var announcement by remember { mutableStateOf<String?>(null) }

    fun move(id: String, to: Int) {
        val from = current.indexOfFirst { it.id == id }
        if (from < 0 || to < 0 || to >= current.size || to == from) return
        val ids = current.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        announcement = "Position ${to + 1} of ${current.size}"
        onReorder(ids, OuroReorderMove(id, from, to))
    }

    // Where the lifted row would land: the number of other rows whose middle lies above its own.
    fun landing(): Int {
        val id = lifted ?: return -1
        val from = current.indexOfFirst { it.id == id }
        if (from < 0) return -1
        var top = 0f
        val middles = current.map { r -> val h = (heights[r.id] ?: 0).toFloat(); (top + h / 2).also { top += h } }
        val mine = middles[from] + offset
        return current.indices.filter { it != from }.count { middles[it] < mine }
    }
    val landed = landing()

    Column(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                announcement?.let {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = it
                }
                ouroReport = report(
                    "kind" to "reorderable",
                    "rows" to rows.size,
                    "locked" to locked,
                    "label" to OuroColor.hex(foreground),
                    "text" to OuroColor.hex(handleColor),
                    "behind" to OuroColor.hex(under),
                    "pressScale" to pressScale,
                )
            },
    ) {
        rows.forEachIndexed { index, row ->
            key(row.id) {
                val name = "Reorder ${row.label}" + if (!disabledReason.isNullOrBlank()) ", $disabledReason" else ""
                val steps = if (locked) emptyList() else Step.values().filter { s -> s.target(index, rows.size).let { it in rows.indices && it != index } }
                val isLifted = lifted == row.id
                val shift = if (lifted == null || isLifted || landed < 0) 0f else {
                    val from = rows.indexOfFirst { it.id == lifted }
                    val h = (heights[lifted] ?: 0).toFloat()
                    when {
                        from < landed && index in (from + 1)..landed -> -h
                        from > landed && index in landed until from -> h
                        else -> 0f
                    }
                }
                val slide by animateFloatAsState(
                    targetValue = shift,
                    animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
                    label = "slide",
                )
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                var pressed by remember { mutableStateOf(false) }
                var held by remember { mutableStateOf<Int?>(null) }
                val scale by animateFloatAsState(
                    targetValue = if (pressed) pressScale else 1f,
                    animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
                    label = "press",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heights[row.id] = it.height }
                        .zIndex(if (isLifted) 1f else 0f)
                        .graphicsLayer { translationY = if (isLifted) offset else slide }
                        .background(if (isLifted) c.accent else under)
                        .pointerInput(row.id, locked) {
                            if (!locked) detectDragGesturesAfterLongPress(
                                onDragStart = { lifted = row.id; offset = 0f },
                                onDrag = { change, delta -> change.consume(); offset += delta.y },
                                onDragEnd = {
                                    val to = landing()
                                    val id = lifted
                                    lifted = null
                                    offset = 0f
                                    if (id != null && to >= 0) move(id, to)
                                },
                                onDragCancel = { lifted = null; offset = 0f },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .sizeIn(minHeight = OuroMinTarget)
                            .semantics {
                                customActions = steps.map { s -> CustomAccessibilityAction(s.label) { move(row.id, s.target(index, rows.size)); true } }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) { row.content() }
                    Box(
                        Modifier
                            .size(OuroMinTarget)
                            .graphicsLayer {
                                alpha = if (locked) 0.5f else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                            .onKeyEvent { e ->
                                if (locked || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (e.key) {
                                    Key.Spacebar -> {
                                        held = if (held == null) index else null
                                        true
                                    }
                                    Key.DirectionUp, Key.DirectionDown -> held?.let { move(row.id, if (e.key == Key.DirectionUp) index - 1 else index + 1); true } ?: false
                                    Key.Escape -> held?.let { move(row.id, it); held = null; true } ?: false
                                    Key.Enter -> { menuFor = row.id; true }
                                    else -> false
                                }
                            }
                            .focusable(interactionSource = interaction)
                            .pointerInput(locked) { detectTapGestures(onPress = { pressed = !locked; tryAwaitRelease(); pressed = false }, onTap = { if (!locked) menuFor = row.id }) }
                            .then(if (focused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
                            .semantics {
                                role = Role.Button
                                contentDescription = name
                                if (locked) disabled()
                                onClick(name) { if (!locked) menuFor = row.id; !locked }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(20.dp)) {
                            val r = 1.5.dp.toPx()
                            for (col in 0..1) for (i in 0..2) drawCircle(handleColor, r, Offset(size.width * (0.35f + 0.3f * col), size.height * (0.2f + 0.3f * i)))
                        }
                        if (menuFor == row.id && steps.isNotEmpty()) {
                            Popup(
                                alignment = Alignment.BottomEnd,
                                onDismissRequest = { menuFor = null },
                                properties = PopupProperties(focusable = true),
                            ) {
                                val shape = RoundedCornerShape(look.radius)
                                Column(
                                    Modifier
                                        .sizeIn(minWidth = 180.dp)
                                        .background(c.popover, shape)
                                        .border(1.dp, c.border, shape)
                                        .padding(vertical = 4.dp),
                                ) {
                                    for (s in steps) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .sizeIn(minHeight = OuroMinTarget)
                                                .clickable(role = Role.Button) { menuFor = null; move(row.id, s.target(index, rows.size)) }
                                                .semantics(mergeDescendants = true) { contentDescription = s.label }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            BasicText(
                                                s.label,
                                                Modifier.clearAndSetSemantics {},
                                                style = TextStyle(color = OuroColor.readable(c.popoverForeground, c.popover, look.textContrast), fontSize = 16.sp, lineHeight = 22.sp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
