// Core's SwipeActions spec (vendor/core/components/swipe-actions.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import bot.ouro.ui.hx.tokens.OuroTokens
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One action a swipe reveals: its [label] and what it does. */
class OuroSwipeAction(val label: String, val onClick: () -> Unit)

private val ActionWidth = 80.dp
private const val CommitFraction = 0.6f

/**
 * A row whose actions a swipe reveals, with the same actions always one press away in a menu, so a swipe is never the
 * only way. A swipe toward the end reveals [leading]; toward the start, [trailing]; the first action on each side
 * takes [tone] and a swipe past 60% of the row's width commits it. A long press on the row opens the menu. For
 * TalkBack the row is named by [label] and has a custom action for each action, and the menu button, named
 * [actionsLabel] ("Actions for {label}"), is reachable on any device; it is drawn only for a pointer or keyboard focus.
 * `OuroSwipeActions("Lunch plans", trailing = listOf(OuroSwipeAction("Delete") { }), tone = OuroTone.Destructive) { OuroItem("Lunch plans") }`.
 */
@Composable
fun OuroSwipeActions(
    label: String,
    modifier: Modifier = Modifier,
    leading: List<OuroSwipeAction> = emptyList(),
    trailing: List<OuroSwipeAction> = emptyList(),
    actionsLabel: String = "Actions for $label",
    tone: OuroTone = OuroTone.Secondary,
    onMenu: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val look = Ouro.look
    val c = look.colors
    val under = Ouro.surface
    val first = remember(look, tone, under) { OuroPaint.resolve(c, tone, OuroVariant.Solid, look.textContrast, under) }
    val rest = remember(look, under) { OuroPaint.resolve(c, OuroTone.Secondary, OuroVariant.Solid, look.textContrast, under) }
    val density = LocalDensity.current
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val scope = rememberCoroutineScope()
    val actionPx = with(density) { ActionWidth.toPx() }
    var width by remember { mutableIntStateOf(0) }
    // Positive moves the row toward the end, which shows the leading actions.
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    val shown = if (dragging) dragged else settle.value
    var menuOpen by remember { mutableStateOf(false) }
    val spec: AnimationSpec<Float> = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness)

    fun openMenu() {
        menuOpen = true
        onMenu()
    }

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
        label = "press",
    )
    val drawn = focused || hovered
    val iconColor = OuroColor.readable(c.mutedForeground, under, look.textContrast)
    val actions = leading + trailing

    Box(
        modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .semantics {
                contentDescription = label
                customActions = actions.map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
                ouroReport = report(
                    "kind" to "swipe-actions",
                    "tone" to tone.name.lowercase(),
                    "actions" to actions.size,
                    "label" to OuroColor.hex(first.label),
                    "behind" to OuroColor.hex(first.behind(under)),
                    "pressScale" to pressScale,
                )
            },
    ) {
        if (shown != 0f) {
            // The swipe is a touch gesture: what it reveals is read by TalkBack as the row's custom actions instead.
            Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
                val side = if (shown > 0f) leading else trailing
                Row(Modifier.align(if ((shown > 0f) == (sign > 0f)) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight()) {
                    side.forEachIndexed { i, a ->
                        val paint = if (i == 0) first else rest
                        Box(
                            Modifier
                                .width(ActionWidth)
                                .fillMaxHeight()
                                .background(paint.fill)
                                .clickable(role = Role.Button) {
                                    a.onClick()
                                    scope.launch { settle.snapTo(shown); dragging = false; settle.animateTo(0f, spec) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(a.label, style = TextStyle(color = paint.label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset((shown * sign).roundToInt(), 0) }
                .background(under)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d -> dragged = (dragged + d * sign).coerceIn(if (trailing.isEmpty()) 0f else -width.toFloat(), if (leading.isEmpty()) 0f else width.toFloat()) },
                    onDragStarted = {
                        settle.stop()
                        dragged = settle.value
                        dragging = true
                    },
                    onDragStopped = {
                        val side = if (dragged > 0f) leading else trailing
                        val distance = abs(dragged)
                        val target = when {
                            side.isEmpty() -> 0f
                            distance >= width * CommitFraction -> 0f
                            distance > actionPx / 2 -> side.size * actionPx * (if (dragged > 0f) 1f else -1f)
                            else -> 0f
                        }
                        if (side.isNotEmpty() && distance >= width * CommitFraction) side.first().onClick()
                        settle.snapTo(dragged)
                        dragging = false
                        settle.animateTo(target, spec)
                    },
                )
                .pointerInput(Unit) { detectTapGestures(onLongPress = { openMenu() }) },
        ) {
            content()
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(OuroMinTarget)
                .graphicsLayer {
                    alpha = if (drawn) 1f else 0f
                    scaleX = scale
                    scaleY = scale
                }
                .hoverable(interaction)
                .focusable(interactionSource = interaction)
                .then(if (drawn) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { openMenu() }) } else Modifier)
                .then(if (focused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
                .semantics {
                    role = Role.Button
                    contentDescription = actionsLabel
                    onClick(actionsLabel) { openMenu(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val r = 1.75.dp.toPx()
                for (i in 0..2) drawCircle(iconColor, r, Offset(size.width / 2, size.height * (0.2f + 0.3f * i)))
            }
        }
        if (menuOpen) {
            Popup(
                alignment = Alignment.BottomEnd,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                val shape = RoundedCornerShape(look.radius)
                Column(
                    Modifier
                        .sizeIn(minWidth = 160.dp)
                        .background(c.popover, shape)
                        .border(1.dp, c.border, shape)
                        .padding(vertical = 4.dp),
                ) {
                    for (a in actions) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = OuroMinTarget)
                                .clickable(role = Role.Button) { menuOpen = false; a.onClick() }
                                .semantics(mergeDescendants = true) { contentDescription = a.label }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicText(
                                a.label,
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
