// Core's ConfirmDialog spec (vendor/core/components/confirm-dialog.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import bot.ouro.ui.actions.OuroAction
import bot.ouro.ui.actions.OuroConfirm

/**
 * A question that needs a yes before something happens. [confirmLabel] is the action ("Delete"), never "OK". Focus starts
 * on Cancel; Cancel, back and Escape call [onClose], and an outside press never does. While [busy] the confirm button keeps
 * its width and can't be pressed again, and Cancel still closes. Use [OuroDialog] for anything that isn't a yes or no.
 */
@Composable
fun OuroConfirmDialog(
    open: Boolean,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    title: String,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    cancelLabel: String = "Cancel",
    tone: OuroTone = OuroTone.Primary,
    busy: Boolean = false,
) {
    if (!open) return
    OuroModalWindow(onEscape = onClose, onOutside = null, alignment = Alignment.Center) { enter ->
        OuroConfirmPanel(
            title, description, confirmLabel, cancelLabel, tone, busy, onConfirm, onClose,
            modifier.padding(16.dp).widthIn(max = 512.dp).fillMaxWidth().graphicsLayer {
                alpha = enter
                scaleX = 0.95f + 0.05f * enter
                scaleY = 0.95f + 0.05f * enter
            }.consumePresses(),
            scrolls = true,
        )
    }
}

/**
 * A ConfirmDialog for a declared action: the question is the action's confirm text, worded as the built-in confirmation
 * words it ([OuroConfirm.confirmText]: the `confirm` in [confirmTexts], else the action's description), and the confirm
 * button is the action's label. Run the action in [onConfirm] (for example `action.press(input)`), setting [busy] while it runs.
 */
@Composable
fun OuroConfirmDialog(
    action: OuroAction<*, *>,
    open: Boolean,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTexts: Map<String, String> = emptyMap(),
    title: String = action.label,
    cancelLabel: String = "Cancel",
    tone: OuroTone = OuroTone.Primary,
    busy: Boolean = false,
) = OuroConfirmDialog(open, onConfirm, onClose, title, action.label, modifier, OuroConfirm.confirmText(action, confirmTexts), cancelLabel, tone, busy)

/** The ConfirmDialog's panel, drawn in place: what [OuroConfirmDialog] puts in its window. */
@Composable
internal fun OuroConfirmPanel(
    title: String,
    description: String?,
    confirmLabel: String,
    cancelLabel: String,
    tone: OuroTone,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    scrolls: Boolean = false,
) {
    val look = Ouro.look
    val surface = look.colors.popover
    val shape = RoundedCornerShape(look.radius)
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
    OuroPanel(
        "confirm-dialog",
        modifier.background(surface, shape).border(1.dp, look.colors.border, shape).padding(24.dp),
        title, false, description, null, onCancel, surface,
        if (scrolls) rememberScrollState() else null,
        footer = {
            OuroButton(cancelLabel, onPress = onCancel, modifier = Modifier.focusRequester(cancel).testTag("confirm-dialog.cancel"), tone = OuroTone.Secondary, variant = OuroVariant.Outline)
            OuroButton(confirmLabel, onPress = onConfirm, modifier = Modifier.testTag("confirm-dialog.confirm"), tone = tone, busy = busy)
        },
    )
}
