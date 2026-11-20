// Core's TabBar spec (vendor/core/components/tab-bar.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.ui.hx.tokens.OuroTokens

/** One item of an [OuroTabBar]: the short [label] that names it, and its [icon], which is decorative and hidden from TalkBack. */
data class OuroBarItem(val label: String, val icon: @Composable () -> Unit)

/**
 * The bar of tabs at the bottom of a phone screen: two to five [items], each an icon over a short label, with the one
 * named by [current] on an accent wash. The bar is one group named by [label]; each item is a Tab named by its label,
 * the current one selected. [accessory] sits apart in a circle at the end, such as Search: only its icon shows, and
 * its label names it for TalkBack. [onPress] runs with the pressed item's label; the app navigates itself.
 */
@Composable
fun OuroTabBar(
    label: String,
    current: String?,
    onPress: (String) -> Unit,
    items: List<OuroBarItem>,
    modifier: Modifier = Modifier,
    accessory: OuroBarItem? = null,
) {
    require(items.size in 2..5) { "OuroTabBar: ${items.size} items. Fix: pass two to five items; put anything else in a menu." }
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val wash = c.accent
    val currentColor = OuroColor.readable(c.accentForeground, wash, look.textContrast)
    val labelColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val edge = c.border
    Row(
        modifier
            .fillMaxWidth()
            .background(behind)
            .drawBehind { drawRect(edge, Offset(0f, 0f), Size(size.width, 1.dp.toPx())) }
            .selectableGroup()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = label
                ouroReport = report(
                    "kind" to "tab-bar",
                    "current" to OuroColor.hex(currentColor),
                    "wash" to OuroColor.hex(wash),
                    "label" to OuroColor.hex(labelColor),
                    "behind" to OuroColor.hex(behind),
                    "pressScale" to pressScale,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            BarTab(item, item.label == current, wash, currentColor, labelColor, pressScale, Modifier.weight(1f)) { onPress(item.label) }
        }
        if (accessory != null) BarAccessory(accessory, labelColor, pressScale) { onPress(accessory.label) }
    }
}

@Composable
private fun BarTab(
    item: OuroBarItem,
    selected: Boolean,
    wash: Color,
    currentColor: Color,
    labelColor: Color,
    pressScale: Float,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val look = Ouro.look
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
        label = "press",
    )
    val shape = RoundedCornerShape(look.radius)
    val color = if (selected) currentColor else labelColor
    Column(
        modifier
            .sizeIn(minWidth = OuroMinTarget, minHeight = 64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (selected) wash else Color.Transparent, shape)
            .then(if (focused) Modifier.border(2.dp, look.colors.ring, shape) else Modifier)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalOuroContentColor provides color) { item.icon() }
        }
        BasicText(
            item.label,
            Modifier.fillMaxWidth().padding(top = 2.dp),
            style = TextStyle(color = color, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun BarAccessory(item: OuroBarItem, labelColor: Color, pressScale: Float, onPress: () -> Unit) {
    val look = Ouro.look
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = if (look.reduceMotion) snap() else spring(OuroTokens.Spring.dampingRatio, OuroTokens.Spring.stiffness),
        label = "press",
    )
    Box(
        Modifier
            .size(OuroMinTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(look.colors.muted, CircleShape)
            .then(if (focused) Modifier.border(2.dp, look.colors.ring, CircleShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPress)
            .semantics { text = AnnotatedString(item.label) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalOuroContentColor provides OuroColor.readable(look.colors.mutedForeground, look.colors.muted, look.textContrast)) { item.icon() }
        }
    }
}
