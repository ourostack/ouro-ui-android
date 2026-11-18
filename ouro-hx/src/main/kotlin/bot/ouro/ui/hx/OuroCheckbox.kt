// Core's Checkbox spec (vendor/core/components/checkbox.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.ui.hx.tokens.OuroTokens

/**
 * A box and what checking it means: checked, not checked or mixed. The whole row is the control, at least 48 dp
 * tall. TalkBack names it by its [label] (", required" when [required]), says checked, not checked or mixed, then
 * reads its [error] and [description], which sit under it. [hideLabel] hides the words but keeps them as the name.
 */
@Composable
fun OuroCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    required: Boolean = false,
    indeterminate: Boolean = false,
    enabled: Boolean = true,
    hideLabel: Boolean = false,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val errorColor = OuroPaint.readable(c.destructive, behind, c.foreground, look.textContrast)
    val edge = if (error != null) OuroPaint.readable(c.destructive, behind, c.foreground, OuroColor.INCREASED_LINE_CONTRAST)
    else OuroColor.push(c.mutedForeground, behind, OuroColor.INCREASED_LINE_CONTRAST)
    val fill = OuroColor.push(c.primary, behind, OuroColor.INCREASED_LINE_CONTRAST)
    val tick = OuroColor.readable(c.primaryForeground, fill, OuroColor.INCREASED_LINE_CONTRAST)
    val state = when {
        indeterminate -> ToggleableState.Indeterminate
        checked -> ToggleableState.On
        else -> ToggleableState.Off
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
        label = "press",
    )
    val name = if (required) "$label, required" else label
    // Lines under the box start where its label does: past the 20 dp box and the 12 dp gap.
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .semantics {
                ouroReport = report(
                    "kind" to "checkbox",
                    "label" to OuroColor.hex(labelColor),
                    "description" to OuroColor.hex(descriptionColor),
                    "error" to OuroColor.hex(errorColor),
                    "behind" to OuroColor.hex(behind),
                    "edge" to OuroColor.hex(if (state == ToggleableState.Off) edge else fill),
                    "required" to required,
                    "pressScale" to pressScale,
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                .triStateToggleable(state = state, interactionSource = interaction, indication = null, enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked || indeterminate) }
                .semantics(mergeDescendants = true) { contentDescription = name },
            // The error and description are read next, as their own words: TalkBack doesn't say a toggle's error.
            horizontalArrangement = Arrangement.spacedBy(12.dp, if (hideLabel) Alignment.CenterHorizontally else Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clearAndSetSemantics {}) {
                val r = CornerRadius(4.dp.toPx())
                val w = 1.5.dp.toPx()
                if (state == ToggleableState.Off) {
                    drawRoundRect(edge, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = r, style = Stroke(w))
                } else {
                    drawRoundRect(fill, cornerRadius = r)
                    val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    if (state == ToggleableState.Indeterminate) {
                        drawLine(tick, Offset(size.width * 0.28f, size.height / 2), Offset(size.width * 0.72f, size.height / 2), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    } else {
                        val path = Path().apply {
                            moveTo(size.width * 0.27f, size.height * 0.52f)
                            lineTo(size.width * 0.43f, size.height * 0.68f)
                            lineTo(size.width * 0.74f, size.height * 0.34f)
                        }
                        drawPath(path, tick, style = stroke)
                    }
                }
                if (focused) drawRoundRect(look.colors.ring, topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()), size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
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
        }
        if (error != null) BasicText(error, Modifier.padding(start = 32.dp), style = TextStyle(color = errorColor, fontSize = 14.sp, lineHeight = 20.sp))
        if (description != null) BasicText(description, Modifier.padding(start = 32.dp), style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
    }
}
