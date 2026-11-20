// Core's Dialog spec (vendor/core/components/dialog.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How wide a Dialog is (core's `size`): the widest it grows, in dp. Auto fits its content; Full fills the window. */
enum class OuroDialogSize(internal val max: Dp?) { Sm(384.dp), Md(512.dp), Lg(640.dp), Xl(768.dp), Auto(null), Full(null) }

/** Where a Dialog appears (core's `presentation`): Auto is a bottom sheet below 640 dp wide and a centred dialog from there. */
enum class OuroPresentation { Auto, Dialog, Sheet }

/** The width from which an Auto Dialog is centred rather than a sheet (core's `sm` breakpoint). */
internal const val DIALOG_BREAKPOINT_DP = 640

/**
 * A panel over the app that asks for one thing. Its [title] names it (a pane title for TalkBack, and a heading unless
 * [hideTitle]); a Close button is always there, named [closeLabel], and the dismiss action closes it too. Back, Escape
 * and (when [closeOnOutsidePress]) a press outside call [onClose] with the reason; the app sets [open] to false.
 * Under Remove animations it arrives with no scale or fade. Use [OuroConfirmDialog] to ask yes or no, and [OuroSheet]
 * for a panel from an edge.
 */
@Composable
fun OuroDialog(
    open: Boolean,
    onClose: (OuroCloseReason) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    hideTitle: Boolean = false,
    description: String? = null,
    closeLabel: String = "Close",
    closeOnOutsidePress: Boolean = true,
    presentation: OuroPresentation = OuroPresentation.Auto,
    size: OuroDialogSize = OuroDialogSize.Md,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    if (!open) return
    val asSheet = when (presentation) {
        OuroPresentation.Sheet -> true
        OuroPresentation.Dialog -> false
        OuroPresentation.Auto -> LocalConfiguration.current.screenWidthDp < DIALOG_BREAKPOINT_DP
    }
    if (asSheet) {
        OuroSheet(open = true, onClose = onClose, title = title, modifier = modifier, hideTitle = hideTitle, description = description, closeLabel = closeLabel, closeOnOutsidePress = closeOnOutsidePress, footer = footer, content = content)
        return
    }
    OuroModalWindow(
        onEscape = { onClose(OuroCloseReason.Escape) },
        onOutside = if (closeOnOutsidePress) ({ onClose(OuroCloseReason.OutsidePress) }) else null,
        alignment = Alignment.Center,
    ) { enter ->
        val scroll = rememberScrollState()
        val width = when {
            size == OuroDialogSize.Full -> Modifier.fillMaxSize()
            size.max != null -> Modifier.widthIn(max = size.max).fillMaxWidth()
            else -> Modifier.widthIn(min = 280.dp, max = 512.dp)
        }
        OuroDialogPanel(
            title, hideTitle, description, closeLabel, { onClose(OuroCloseReason.CloseButton) },
            modifier.padding(16.dp).then(width).graphicsLayer {
                alpha = enter
                scaleX = 0.95f + 0.05f * enter
                scaleY = 0.95f + 0.05f * enter
            }.consumePresses(),
            footer, content, scroll,
        )
    }
}

/** The Dialog's panel, drawn in place: what [OuroDialog] puts in its window. */
@Composable
internal fun OuroDialogPanel(
    title: String,
    hideTitle: Boolean,
    description: String?,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    scroll: androidx.compose.foundation.ScrollState? = null,
) {
    val look = Ouro.look
    val surface = look.colors.popover
    val shape = RoundedCornerShape(look.radius)
    OuroPanel(
        "dialog",
        modifier.background(surface, shape).border(1.dp, look.colors.border, shape).padding(24.dp),
        title, hideTitle, description, closeLabel, onClose, surface, scroll, footer, content,
    )
}
