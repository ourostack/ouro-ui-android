// Core's Switch spec (vendor/core/components/switch.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.ui.hx.tokens.OuroTokens

/**
 * An on-off switch with its words. The whole row is the control, at least 48 dp tall. TalkBack names it by its [label]
 * (", required" when [required]) and says On or Off, which an [error] never replaces: the error is the switch's own
 * error semantics. The [description] sits under the row and is read after the state. On is the primary fill and off is
 * a muted track inside an edge at 3:1, so they differ by fill and not only by hue. The thumb stretches while pressed,
 * and not under reduced motion. [hideLabel] hides the words but keeps them as the name. Use [OuroCheckbox] instead
 * when the choice is one of several, or part of a form that is sent later.
 */
@Composable
fun OuroSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    hideLabel: Boolean = false,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val line = OuroColor.INCREASED_LINE_CONTRAST
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val errorColor = OuroPaint.readable(c.destructive, behind, c.foreground, look.textContrast)
    val edge = if (error != null) OuroPaint.readable(c.destructive, behind, c.foreground, line) else OuroColor.push(c.mutedForeground, behind, line)
    val fill = OuroColor.push(c.primary, behind, line)
    val thumbOn = OuroColor.readable(c.primaryForeground, fill, line)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val thumbWidth by animateDpAsState(
        targetValue = if (pressed && !look.reduceMotion) 24.dp else 20.dp,
        animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
        label = "thumb",
    )
    val name = if (required) "$label, required" else label
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .semantics {
                ouroReport = report(
                    "kind" to "switch",
                    "label" to OuroColor.hex(labelColor),
                    "description" to OuroColor.hex(descriptionColor),
                    "error" to OuroColor.hex(errorColor),
                    "behind" to OuroColor.hex(behind),
                    "edge" to OuroColor.hex(if (checked) fill else edge),
                    "required" to required,
                    "hasError" to (error != null),
                    "pressScale" to pressScale,
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                .toggleable(value = checked, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    if (error != null) this.error(error)
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp, if (hideLabel) Alignment.CenterHorizontally else Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hideLabel) {
                BasicText(
                    buildAnnotatedString {
                        append(label)
                        // The marker is drawn, not spoken: the name already says required.
                        if (required) withStyle(SpanStyle(color = errorColor)) { append(" *") }
                    },
                    Modifier.weight(1f),
                    style = TextStyle(color = labelColor, fontSize = 16.sp, lineHeight = 22.sp),
                )
            }
            Box(
                Modifier
                    .clearAndSetSemantics {}
                    .size(width = 44.dp, height = 26.dp)
                    .background(if (checked) fill else c.muted, CircleShape)
                    .border(if (focused) 2.dp else 1.5.dp, if (focused) OuroColor.push(c.ring, behind, line) else if (checked) fill else edge, CircleShape)
                    .padding(2.dp),
            ) {
                Box(
                    Modifier
                        .offset(x = if (checked) 40.dp - thumbWidth else 0.dp)
                        .size(width = thumbWidth, height = 22.dp)
                        .background(if (checked) thumbOn else edge, CircleShape),
                )
            }
        }
        if (error != null) BasicText(error, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = errorColor, fontSize = 14.sp, lineHeight = 20.sp))
        if (description != null) BasicText(description, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
    }
}
