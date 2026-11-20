// Core's Tabs spec (vendor/core/components/tabs.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.ui.hx.tokens.OuroTokens

/** One tab: its [value] (what [OuroTabs] reports when it is chosen), the words on it, and whether it can be pressed. */
data class OuroTab(val value: String, val label: String, val disabled: Boolean = false)

/**
 * A row of tabs (or a column, with [orientation] Vertical) that picks which [panel] shows. The tabs are one group named
 * by [label]; each is a Tab named by its words, and the chosen one reads as selected. A disabled tab is faded and can't
 * be pressed. [onValueChange] runs with the pressed tab's value; [panel] gets the chosen value and shows under the
 * tabs (beside them when vertical).
 */
@Composable
fun OuroTabs(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tabs: List<OuroTab>,
    modifier: Modifier = Modifier,
    orientation: OuroOrientation = OuroOrientation.Horizontal,
    panel: (@Composable (String) -> Unit)? = null,
) {
    require(tabs.isNotEmpty()) { "OuroTabs: tabs is empty. Fix: pass at least one OuroTab." }
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val selectedColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val unselectedColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val vertical = orientation == OuroOrientation.Vertical
    val pressScale = if (look.reduceMotion) 1f else OuroTokens.Press.scale
    val list = Modifier.selectableGroup().semantics {
        contentDescription = label
        ouroReport = report(
            "kind" to "tabs",
            "orientation" to orientation.name.lowercase(),
            "selected" to OuroColor.hex(selectedColor),
            "unselected" to OuroColor.hex(unselectedColor),
            "behind" to OuroColor.hex(behind),
            "pressScale" to pressScale,
        )
    }
    val item: @Composable (OuroTab, Modifier) -> Unit = { tab, m ->
        OuroTabItem(tab, tab.value == value, vertical, selectedColor, unselectedColor, pressScale, m) { onValueChange(tab.value) }
    }
    val body: @Composable () -> Unit = {
        if (panel != null) CompositionLocalProvider(LocalOuroContentColor provides selectedColor) { panel(value) }
    }
    if (vertical) {
        val divider = c.border
        Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Column(
                list.width(IntrinsicSize.Max).wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true).drawBehind { drawRect(divider, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) },
            ) { for (t in tabs) item(t, Modifier.fillMaxWidth()) }
            if (panel != null) Box(Modifier.weight(1f)) { body() }
        }
    } else {
        Column(modifier) {
            val divider = c.border
            Row(list.fillMaxWidth().drawBehind { drawRect(divider, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }) {
                for (t in tabs) item(t, Modifier.weight(1f, fill = false))
            }
            if (panel != null) Box(Modifier.padding(top = 12.dp)) { body() }
        }
    }
}

@Composable
private fun OuroTabItem(
    tab: OuroTab,
    selected: Boolean,
    vertical: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    unselectedColor: androidx.compose.ui.graphics.Color,
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
    val shape = RoundedCornerShape(look.radius / 2)
    Box(
        modifier
            .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (tab.disabled) 0.5f else 1f
            }
            .then(if (focused) Modifier.border(2.dp, look.colors.ring, shape) else Modifier)
            .selectable(selected = selected, interactionSource = interaction, indication = null, enabled = !tab.disabled, role = Role.Tab, onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            tab.label,
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = TextStyle(color = if (selected) selectedColor else unselectedColor, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
        if (selected) {
            val line = Modifier.background(look.colors.primary)
            if (vertical) Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp).then(line))
            else Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).then(line))
        }
    }
}
