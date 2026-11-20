// Core's Sheet spec (vendor/core/components/sheet.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A panel from an edge of the screen, named by its [title] like [OuroDialog]: a pane title, a heading unless
 * [hideTitle], and a Close button. A [modal] sheet sits on a scrim, keeps focus inside and closes on back, Escape, a press
 * outside ([closeOnOutsidePress], default true) or a drag toward its [side]; a sheet that isn't modal has no scrim, leaves
 * focus where it was and doesn't close on an outside press unless asked. Put a non-modal sheet at the root of the screen.
 * Under Remove animations it arrives with no slide.
 */
@Composable
fun OuroSheet(
    open: Boolean,
    onClose: (OuroCloseReason) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    hideTitle: Boolean = false,
    description: String? = null,
    closeLabel: String = "Close",
    closeOnOutsidePress: Boolean? = null,
    side: OuroSide = OuroSide.Bottom,
    modal: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    if (!open) return
    val outside = closeOnOutsidePress ?: modal
    val alignment = when (side) {
        OuroSide.Top -> Alignment.TopCenter
        OuroSide.Bottom -> Alignment.BottomCenter
        OuroSide.Left -> Alignment.CenterStart
        OuroSide.Right -> Alignment.CenterEnd
    }
    val vertical = side == OuroSide.Top || side == OuroSide.Bottom
    val shaped = if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxHeight().widthIn(max = 360.dp)
    if (modal) {
        OuroModalWindow(
            onEscape = { onClose(OuroCloseReason.Escape) },
            onOutside = if (outside) ({ onClose(OuroCloseReason.OutsidePress) }) else null,
            alignment = alignment,
        ) { enter ->
            val scroll = rememberScrollState()
            OuroSheetPanel(
                title, hideTitle, description, closeLabel, { onClose(OuroCloseReason.CloseButton) }, side,
                modifier.then(shaped)
                    .graphicsLayer {
                        val away = (1f - enter) * (if (vertical) size.height else size.width)
                        val sign = if (side == OuroSide.Bottom || side == OuroSide.Right) 1f else -1f
                        if (vertical) translationY = sign * away else translationX = sign * away
                    }
                    .dragToDismiss(side) { onClose(OuroCloseReason.Drag) }
                    .consumePresses(),
                footer, content, scroll,
            )
        }
    } else {
        Popup(
            alignment = alignment,
            onDismissRequest = { onClose(OuroCloseReason.OutsidePress) },
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = outside),
        ) {
            val enter = rememberEnter()
            OuroSheetPanel(
                title, hideTitle, description, closeLabel, { onClose(OuroCloseReason.CloseButton) }, side,
                modifier.then(shaped).graphicsLayer {
                    val away = (1f - enter) * (if (vertical) size.height else size.width)
                    val sign = if (side == OuroSide.Bottom || side == OuroSide.Right) 1f else -1f
                    if (vertical) translationY = sign * away else translationX = sign * away
                },
                footer, content, rememberScrollState(),
            )
        }
    }
}

/** The Sheet's panel, drawn in place: what [OuroSheet] puts in its window. */
@Composable
internal fun OuroSheetPanel(
    title: String,
    hideTitle: Boolean,
    description: String?,
    closeLabel: String,
    onClose: () -> Unit,
    side: OuroSide,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    scroll: ScrollState? = null,
) {
    val look = Ouro.look
    val c = look.colors
    val surface = c.popover
    val r = look.radius
    val shape = when (side) {
        OuroSide.Bottom -> RoundedCornerShape(topStart = r, topEnd = r)
        OuroSide.Top -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
        OuroSide.Left -> RoundedCornerShape(topEnd = r, bottomEnd = r)
        OuroSide.Right -> RoundedCornerShape(topStart = r, bottomStart = r)
    }
    val handle = side == OuroSide.Bottom
    val handleColor = c.mutedForeground.copy(alpha = 0.5f)
    OuroPanel(
        "sheet",
        modifier.background(surface, shape).border(1.dp, c.border, shape)
            // The drag handle: a 36 dp bar, only a picture; the Close button is how a screen reader closes the sheet.
            .drawBehind {
                if (handle) drawRoundRect(handleColor, Offset((size.width - 36.dp.toPx()) / 2f, 10.dp.toPx()), Size(36.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = if (handle) 28.dp else 24.dp),
        title, hideTitle, description, closeLabel, onClose, surface, scroll, footer, content,
    )
}
