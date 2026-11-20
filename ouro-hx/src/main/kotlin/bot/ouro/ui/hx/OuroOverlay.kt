// What Dialog, Sheet and ConfirmDialog share: the panel (pane title, heading, description, Close, content, footer), the
// modal window that holds it, and the entrance. Nothing here is public; the three components are.
package bot.ouro.ui.hx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import bot.ouro.ui.hx.tokens.OuroTokens

/** Why a Dialog or a Sheet closed: core's `close` event, with its reason. */
enum class OuroCloseReason { CloseButton, OutsidePress, Escape, Drag }

/** An edge a Sheet comes from. */
enum class OuroSide { Top, Bottom, Left, Right }

/** The panel every overlay draws: its words, a Close button when [closeLabel] is set, its [content] and its [footer]. */
@Composable
internal fun OuroPanel(
    kind: String,
    modifier: Modifier,
    title: String,
    hideTitle: Boolean,
    description: String?,
    closeLabel: String?,
    onClose: () -> Unit,
    surface: Color,
    scroll: ScrollState? = null,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val look = Ouro.look
    val c = look.colors
    val titleColor = OuroColor.readable(c.popoverForeground, surface, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, surface, look.textContrast)
    val large = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    CompositionLocalProvider(LocalOuroSurface provides surface, LocalOuroContentColor provides titleColor) {
        Column(
            modifier
                .semantics {
                    isTraversalGroup = true
                    paneTitle = title
                    dismiss(closeLabel) { onClose(); true }
                    ouroReport = report(
                        "kind" to kind,
                        "title" to OuroColor.hex(titleColor),
                        "description" to OuroColor.hex(descriptionColor),
                        "surface" to OuroColor.hex(surface),
                        "hideTitle" to hideTitle,
                        "pressScale" to pressScale,
                    )
                }
                .then(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val words: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!hideTitle) BasicText(title, Modifier.semantics { heading() }, style = TextStyle(color = titleColor, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                    if (description != null) BasicText(description, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
                }
            }
            val close: @Composable () -> Unit = {
                if (closeLabel != null) OuroButton(closeLabel, onPress = onClose, modifier = Modifier.testTag("$kind.close"), tone = OuroTone.Secondary, variant = OuroVariant.Plain, size = OuroSize.Sm)
            }
            if (large || closeLabel == null) {
                words()
                close()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) { words() }
                    close()
                }
            }
            content?.invoke()
            if (footer != null) {
                if (large) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { footer() }
            }
        }
    }
}

/** 0 to 1 as an overlay arrives; already 1 when motion is reduced, so nothing slides, scales or fades. */
@Composable
internal fun rememberEnter(): Float {
    val reduce = Ouro.look.reduceMotion
    val t = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) { if (reduce) t.snapTo(1f) else t.animateTo(1f, tween(OuroTokens.Duration.slow)) }
    return t.value
}

/**
 * A modal window over the app: a scrim that an outside press reaches ([onOutside], null to ignore it), back and Escape
 * calling [onEscape], and [content] placed at [alignment]. The panel consumes its own presses so only the scrim is outside.
 */
@Composable
internal fun OuroModalWindow(onEscape: () -> Unit, onOutside: (() -> Unit)?, alignment: Alignment, content: @Composable (enter: Float) -> Unit) {
    Dialog(
        onDismissRequest = onEscape,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        val enter = rememberEnter()
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onEscape(); true } else false
            },
        ) {
            Box(Modifier.fillMaxSize().alpha(enter).background(Color.Black.copy(alpha = 0.5f)).pointerInput(onOutside) { detectTapGestures { onOutside?.invoke() } })
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = alignment) { content(enter) }
        }
    }
}

/** Takes the presses on a panel, so they never reach the scrim behind it. */
internal fun Modifier.consumePresses(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Drags a Sheet toward [side] to close it: past a third of its size on release closes it, less settles back. */
@Composable
internal fun Modifier.dragToDismiss(side: OuroSide, onDismiss: () -> Unit): Modifier {
    var offset by remember { mutableFloatStateOf(0f) }
    var extent by remember { mutableFloatStateOf(1f) }
    val vertical = side == OuroSide.Top || side == OuroSide.Bottom
    val sign = if (side == OuroSide.Bottom || side == OuroSide.Right) 1f else -1f
    val state = rememberDraggableState { delta -> offset = (offset + sign * delta).coerceAtLeast(0f) }
    return this
        .onSizeChanged { extent = (if (vertical) it.height else it.width).toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            if (vertical) translationY = sign * offset else translationX = sign * offset
        }
        .draggable(state, if (vertical) Orientation.Vertical else Orientation.Horizontal, onDragStopped = {
            if (offset > extent / 3f) onDismiss()
            offset = 0f
        })
}
