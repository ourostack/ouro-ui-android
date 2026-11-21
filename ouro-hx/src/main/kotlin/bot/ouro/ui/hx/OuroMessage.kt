// Core's Message spec (vendor/core/components/message.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Who a message is from (core's `role`): the person using the app, the other side, or a note from the app itself. */
enum class OuroMessageRole { User, Assistant, System }

/**
 * One message in a chat. [name] is its author and [time] when it was sent. A [OuroMessageRole.User] message sits at
 * the end in the primary colour, [OuroMessageRole.Assistant] (the default) at the start on a muted fill, and
 * [OuroMessageRole.System] is a centred muted line with no bubble. [continued] leaves the name and time out of the
 * picture, after an earlier message from the same author, and keeps them in what TalkBack reads. [failed] draws the
 * bubble destructive with "Not sent" under it, and [pending] says "Sending" there; both are part of the spoken name.
 * [streaming] marks a reply still being written. TalkBack reads one node: the author, time, text and state, in that
 * order, with nothing to press. Use [OuroItem] instead for a row that opens something.
 *
 * ```
 * OuroMessage("Ana", "See you at noon.", time = "9:41")
 * ```
 */
@Composable
fun OuroMessage(
    name: String,
    text: String,
    modifier: Modifier = Modifier,
    role: OuroMessageRole = OuroMessageRole.Assistant,
    time: String? = null,
    continued: Boolean = false,
    streaming: Boolean = false,
    failed: Boolean = false,
    pending: Boolean = false,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val system = role == OuroMessageRole.System
    val own = role == OuroMessageRole.User
    val fill = when {
        failed -> c.destructive
        own -> c.primary
        else -> c.muted
    }
    val onFill = when {
        failed -> c.destructiveForeground
        own -> c.primaryForeground
        else -> c.foreground
    }
    val textColor = OuroColor.readable(onFill, fill, look.textContrast)
    val metaColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val stateColor = if (failed) OuroPaint.readable(c.destructive, behind, c.foreground, look.textContrast) else metaColor
    val state = when {
        failed -> "Not sent"
        pending -> "Sending"
        else -> null
    }
    val shown = if (streaming && text.isEmpty()) "…" else text
    val spoken = listOfNotNull(name, time, text.ifEmpty { null }, state).joinToString(", ")
    val header = if (continued) null else listOfNotNull(name, time).joinToString("  ")
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = spoken
                ouroReport = report(
                    "kind" to "message",
                    "text" to OuroColor.hex(if (system) metaColor else textColor),
                    "behind" to OuroColor.hex(if (system) behind else fill),
                    "meta" to OuroColor.hex(metaColor),
                    "state" to OuroColor.hex(stateColor),
                    "surface" to OuroColor.hex(behind),
                    "spoken" to spoken,
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = when {
            system -> Alignment.CenterHorizontally
            own -> Alignment.End
            else -> Alignment.Start
        },
    ) {
        if (system) {
            BasicText(shown, style = TextStyle(color = metaColor, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center))
        } else {
            if (header != null) BasicText(header, style = TextStyle(color = metaColor, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val bubbleMax = maxWidth * 0.8f
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                    BasicText(
                        shown,
                        Modifier
                            .widthIn(max = bubbleMax)
                            .background(fill, RoundedCornerShape(look.radius + 4.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        style = TextStyle(color = textColor, fontSize = 16.sp, lineHeight = 22.sp),
                    )
                }
            }
        }
        if (state != null) BasicText(state, style = TextStyle(color = stateColor, fontSize = 12.sp, lineHeight = 16.sp))
    }
}
