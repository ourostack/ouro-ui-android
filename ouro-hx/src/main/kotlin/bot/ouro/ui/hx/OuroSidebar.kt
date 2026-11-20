// Core's Sidebar spec (vendor/core/components/sidebar.spec.json), in Compose.
package bot.ouro.ui.hx

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One row of an [OuroSidebar]: a [label], an optional [icon] (decorative, hidden from TalkBack) and [badge]. A row with
 * [items] is a group that shows or hides them, one level deep; a row with [action] set is a button that does something
 * rather than go somewhere, so it is never the current page.
 */
data class OuroSidebarItem(
    val label: String,
    val icon: (@Composable () -> Unit)? = null,
    val badge: String? = null,
    val action: Boolean = false,
    val items: List<OuroSidebarItem> = emptyList(),
)

/** A run of items in an [OuroSidebar], under an optional [heading]. */
data class OuroSidebarSection(val heading: String?, val items: List<OuroSidebarItem>)

/**
 * The column of links down the side of a tablet or foldable app: [sections] of items, the one named by [current] on an
 * accent wash with a bar at its start edge. [collapsed] narrows it to a rail of icons, where each item keeps its label
 * as its name; the app owns that state and its toggle. The column is a list named by [label]; each item is a button
 * named by its label and badge ("Inbox 12"), the current one selected, and a group's button can expand and collapse.
 * [onPress] runs with the pressed item's label, as the app navigates itself. [header] and [footer] are clipped to the
 * rail when collapsed. A phone uses [OuroTabBar] instead.
 */
@Composable
fun OuroSidebar(
    label: String,
    current: String?,
    onPress: (String) -> Unit,
    sections: List<OuroSidebarSection>,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val wash = c.accent
    val currentColor = OuroColor.readable(c.accentForeground, wash, look.textContrast)
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val sectionColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val badgeColor = OuroColor.readable(c.mutedForeground, c.muted, look.textContrast)
    val colors = SidebarColors(wash, currentColor, labelColor, sectionColor, c.muted, badgeColor)
    val edge = c.border
    Column(
        modifier
            .width(if (collapsed) 64.dp else 256.dp)
            .background(behind)
            .drawBehind { drawRect(edge, Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) }
            .padding(8.dp)
            .semantics {
                contentDescription = label
                collectionInfo = CollectionInfo(rowCount = sections.sumOf { it.items.size }, columnCount = 1)
                ouroReport = report(
                    "kind" to "sidebar",
                    "current" to OuroColor.hex(currentColor),
                    "wash" to OuroColor.hex(wash),
                    "label" to OuroColor.hex(labelColor),
                    "section" to OuroColor.hex(sectionColor),
                    "badge" to OuroColor.hex(badgeColor),
                    "badgeWash" to OuroColor.hex(c.muted),
                    "behind" to OuroColor.hex(behind),
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (header != null) Box(Modifier.fillMaxWidth().clipToBounds()) { header() }
        for (section in sections) {
            if (section.heading != null && !collapsed) {
                BasicText(
                    section.heading,
                    Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                    style = TextStyle(color = sectionColor, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                )
            }
            for (item in section.items) SidebarRow(item, current, collapsed, colors, 0, onPress)
        }
        if (footer != null) Box(Modifier.fillMaxWidth().clipToBounds()) { footer() }
    }
}

private class SidebarColors(val wash: Color, val current: Color, val label: Color, val section: Color, val badgeWash: Color, val badge: Color)

@Composable
private fun SidebarRow(item: OuroSidebarItem, current: String?, collapsed: Boolean, colors: SidebarColors, depth: Int, onPress: (String) -> Unit) {
    val look = Ouro.look
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val isGroup = item.items.isNotEmpty()
    val selected = !item.action && !isGroup && item.label == current
    var expanded by rememberSaveable { mutableStateOf(item.items.any { it.label == current }) }
    val shape = RoundedCornerShape(look.radius)
    val color = if (selected) colors.current else colors.label
    val name = if (collapsed || item.badge == null) item.label else "${item.label} ${item.badge}"
    val press: () -> Unit = {
        if (isGroup) {
            expanded = !expanded
        } else {
            onPress(item.label)
        }
    }
    val base = Modifier
        .fillMaxWidth()
        .padding(start = if (collapsed) 0.dp else (depth * 16).dp)
        .heightIn(min = OuroMinTarget)
        .background(if (selected) colors.wash else if (pressed) look.colors.muted else Color.Transparent, shape)
        .then(if (selected) Modifier.drawBehind { drawRect(colors.current, Offset(0f, 0f), Size(3.dp.toPx(), size.height)) } else Modifier)
        .then(if (focused) Modifier.border(2.dp, look.colors.ring, shape) else Modifier)
    val modifier = if (!item.action && !isGroup) {
        base.selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Button, onClick = press)
    } else {
        base.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = press)
    }
    Row(
        modifier.semantics {
            text = AnnotatedString(name)
            if (isGroup) { if (expanded) collapse { press(); true } else expand { press(); true } }
        }.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) {
            Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalOuroContentColor provides color) { item.icon.invoke() }
            }
        }
        if (!collapsed) {
            BasicText(
                item.label,
                Modifier.weight(1f).clearAndSetSemantics {},
                style = TextStyle(color = color, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
            if (item.badge != null) {
                BasicText(
                    item.badge,
                    Modifier.background(colors.badgeWash, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp).clearAndSetSemantics {},
                    style = TextStyle(color = colors.badge, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
    if (isGroup && expanded && !collapsed) {
        for (child in item.items) SidebarRow(child.copy(items = emptyList()), current, collapsed, colors, depth + 1, onPress)
    }
}
