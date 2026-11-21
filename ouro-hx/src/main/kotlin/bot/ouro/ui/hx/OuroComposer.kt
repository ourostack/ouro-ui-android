// Core's Composer spec (vendor/core/components/composer.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which key sends from a hardware keyboard (core's `sendOn`): Enter sends and Shift+Enter starts a line, or Ctrl+Enter sends and Enter starts a line. */
enum class OuroComposerSendOn { Enter, ModEnter }

/**
 * The box a person writes a message in, with a Send button at its end, in one frame. The [label] names the box for
 * TalkBack ("Message Mia"), and the [placeholder] is only an example. The host owns the [value] and clears it after
 * [onSend], which carries the text. [sendOn] picks the key that sends from a hardware keyboard; a word an input method
 * is still composing is never cut off by it. While [sending] the Send button shows a still ring in place of its words,
 * keeps its name and sends nothing more, and "Sending" is announced once when it turns on. A [sendDisabledReason] turns
 * Send off but leaves the box writable; it is drawn under the box and read with it. The box grows to [maxRows] lines,
 * then scrolls. [disabled] turns off both. Use [OuroTextField] instead for a labelled field that is not a message.
 *
 * ```
 * OuroComposer("Message Mia", text, { text = it }, onSend = { send(it); text = "" })
 * ```
 */
@Composable
fun OuroComposer(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    sendLabel: String = "Send",
    sendOn: OuroComposerSendOn = OuroComposerSendOn.Enter,
    sending: Boolean = false,
    sendDisabledReason: String? = null,
    maxRows: Int = 10,
    disabled: Boolean = false,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val line = OuroColor.INCREASED_LINE_CONTRAST
    val textColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val mutedColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val sendText = OuroColor.readable(c.primaryForeground, c.primary, look.textContrast)
    val edge = OuroColor.push(c.input, behind, line)
    val ring = OuroColor.push(c.ring, behind, line)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(look.radius + 8.dp)
    val canSend = !disabled && !sending && sendDisabledReason == null
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // The host's text wins; the selection and the word being composed stay while it agrees.
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val submit = { if (value.isNotBlank()) onSend(value) }
    Column(
        modifier
            .alpha(if (disabled) 0.5f else 1f)
            .semantics {
                contentDescription = label
                ouroReport = report(
                    "kind" to "composer",
                    "spoken" to label,
                    "text" to OuroColor.hex(textColor),
                    "placeholder" to OuroColor.hex(mutedColor),
                    "reason" to OuroColor.hex(mutedColor),
                    "sendText" to OuroColor.hex(sendText),
                    "sendFill" to OuroColor.hex(c.primary),
                    "edge" to OuroColor.hex(edge),
                    "behind" to OuroColor.hex(behind),
                    "hasReason" to (sendDisabledReason != null),
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(behind, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) ring else edge, shape)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = field,
                onValueChange = { field = it; if (it.text != value) onValueChange(it.text) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = OuroMinTarget)
                    .onPreviewKeyEvent { e ->
                        val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
                        val sends = if (sendOn == OuroComposerSendOn.Enter) !e.isShiftPressed else e.isCtrlPressed || e.isMetaPressed
                        if (e.type == KeyEventType.KeyDown && enter && sends && field.composition == null) {
                            if (canSend) submit()
                            true
                        } else false
                    }
                    .semantics {
                        contentDescription = label
                        if (sendDisabledReason != null) stateDescription = sendDisabledReason
                    },
                enabled = !disabled,
                textStyle = TextStyle(color = textColor, fontSize = 16.sp, lineHeight = 22.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                minLines = 1,
                maxLines = maxOf(maxRows, 1),
                interactionSource = interaction,
                cursorBrush = SolidColor(textColor),
                decorationBox = { inner ->
                    Box(Modifier.padding(end = 8.dp).padding(vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            BasicText(placeholder, Modifier.clearAndSetSemantics {}, style = TextStyle(color = mutedColor, fontSize = 16.sp, lineHeight = 22.sp))
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                    .alpha(if (canSend) 1f else 0.5f)
                    .clickable(enabled = canSend, role = Role.Button, onClick = submit)
                    .semantics(mergeDescendants = true) { contentDescription = sendLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.heightIn(min = 40.dp).background(c.primary, shape).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) {
                        // Still on purpose: nothing here moves, so it needs no reduced-motion variant.
                        Canvas(Modifier.size(20.dp)) {
                            val w = 2.dp.toPx()
                            drawArc(sendText, startAngle = -90f, sweepAngle = 270f, useCenter = false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                        }
                    } else {
                        BasicText(sendLabel, style = TextStyle(color = sendText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
        if (sendDisabledReason != null) {
            BasicText(sendDisabledReason, Modifier.padding(horizontal = 12.dp), style = TextStyle(color = mutedColor, fontSize = 14.sp, lineHeight = 20.sp))
        }
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; text = AnnotatedString(if (sending) "Sending" else "") })
    }
}
