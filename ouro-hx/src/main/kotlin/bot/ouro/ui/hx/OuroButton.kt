// Core's Button spec (vendor/core/components/button.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.ui.actions.OuroAction
import bot.ouro.ui.hx.tokens.OuroTokens
import kotlinx.coroutines.launch

/** Height: every size keeps a 48 dp touch target, Android's minimum. */
enum class OuroSize { Sm, Md, Lg }

/** The smallest touch target a control draws: Android's 48 dp, above core's 44. */
val OuroMinTarget = 48.dp

/**
 * A control a person presses to do one thing. [hint] is what TalkBack says the press does ("double-tap to ...").
 * While [busy] it keeps its width, shows a spinner in place of the words, can't be pressed again, and says it is busy.
 */
@Composable
fun OuroButton(
    label: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    tone: OuroTone = OuroTone.Primary,
    variant: OuroVariant = OuroVariant.Solid,
    size: OuroSize = OuroSize.Md,
    enabled: Boolean = true,
    busy: Boolean = false,
    hint: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val look = Ouro.look
    val under = Ouro.surface
    val paint = remember(look, tone, variant, under) { OuroPaint.resolve(look.colors, tone, variant, look.textContrast, under) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
        label = "press",
    )
    val fill = when {
        pressed -> paint.pressed
        hovered -> paint.hover
        else -> paint.fill
    }
    val shape = RoundedCornerShape(look.radius)
    val behind = paint.behind(under)
    val padding = when (size) {
        OuroSize.Sm -> PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        OuroSize.Md -> PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        OuroSize.Lg -> PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    }
    val edge = when {
        focused -> Pair(2.dp, look.colors.ring)
        paint.edge != Color.Unspecified -> Pair(1.dp, paint.edge)
        else -> null
    }
    Box(
        modifier
            .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled || busy) 1f else 0.5f
            }
            .clip(shape)
            .then(if (fill != Color.Unspecified) Modifier.background(fill, shape) else Modifier)
            .then(if (edge != null) Modifier.border(edge.first, edge.second, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !busy, role = Role.Button, onClickLabel = hint, onClick = onPress)
            .semantics {
                ouroReport = report(
                    "kind" to "button",
                    "tone" to tone.name.lowercase(),
                    "variant" to variant.name.lowercase(),
                    "label" to OuroColor.hex(paint.label),
                    "behind" to OuroColor.hex(behind),
                    "pressScale" to pressScale,
                )
                if (busy) stateDescription = "Busy"
            }
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.alpha(if (busy) 0f else 1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Box(Modifier.clearAndSetSemantics {}) { icon() }
            BasicText(label, style = TextStyle(color = paint.label, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
        }
        if (busy) OuroSpinner(paint.label)
    }
}

/**
 * A Button that runs a declared action through the person's door ([OuroAction.press]): its words are the action's
 * label, its hint the action's description, and it carries the action for the X-ray. It is busy while the action runs.
 */
@Composable
fun <I : Any, O : Any> OuroButton(
    action: OuroAction<I, O>,
    input: () -> I,
    modifier: Modifier = Modifier,
    tone: OuroTone = OuroTone.Primary,
    variant: OuroVariant = OuroVariant.Solid,
    size: OuroSize = OuroSize.Md,
    enabled: Boolean = true,
    label: String = action.label,
    onResult: (Result<O>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    OuroButton(
        label = label,
        onPress = {
            busy = true
            scope.launch {
                val result = runCatching { action.press(input()) }
                busy = false
                onResult(result)
            }
        },
        modifier = modifier.testTag("action.${action.id}").ouroAction(action.id, action.consent.wire),
        tone = tone,
        variant = variant,
        size = size,
        enabled = enabled,
        busy = busy,
        hint = action.description,
    )
}

/** A ring a quarter open, standing in for the words while a Button is busy. It doesn't spin, so it never moves. */
@Composable
internal fun OuroSpinner(color: Color) {
    Canvas(Modifier.size(18.dp).clearAndSetSemantics {}) {
        drawArc(color, startAngle = -90f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
