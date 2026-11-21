// Core's ConversationList spec (vendor/core/components/conversation-list.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How tall an [OuroConversationList] row is: Comfortable shows the preview under the title, Compact the title and time only. */
enum class OuroConversationDensity { Comfortable, Compact }

/** One conversation: its [id], [title], [time] as already written ("9:41", "Mon"), an optional [preview] and whether it has new messages. */
class OuroConversation(val id: String, val title: String, val time: String, val preview: String? = null, val unread: Boolean = false)

/** A named group of conversations whose header opens and closes it. */
class OuroConversationSection(val id: String, val title: String, val conversations: List<OuroConversation>)

private sealed interface ConversationEntry {
    class Head(val section: OuroConversationSection, val open: Boolean) : ConversationEntry
    class Chat(val conversation: OuroConversation) : ConversationEntry
}

/** A list builds every row up to this many, and only the rows in view above it. */
internal const val ConversationVirtualAbove = 20

/**
 * A list of conversations: each row shows a title, a time, a preview line ([density] Comfortable) and a dot with a bold
 * title when it has new messages, and the one named by [selectedId] is marked as open. A row is one TalkBack stop read as
 * its title, then [unreadLabel] when it has new messages, then its time and preview, then "current" when open; beside it
 * is a button named by [moreLabel] and the title, which calls [onMore]. Rows after a section's header hide when that
 * header is pressed ([onSectionOpenChange] says which and whether it is now open). With more than 20 rows only the rows
 * in view are built, and the list still reports its full count. With no rows, [empty] shows in its place.
 * `OuroConversationList("Conversations", listOf(OuroConversation("a", "Ari", "9:41", "Lunch at noon?", unread = true)))`.
 * Use [OuroItemGroup] instead when the rows are plain items, and [OuroSidebar] for places to go.
 */
@Composable
fun OuroConversationList(
    label: String,
    conversations: List<OuroConversation>,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    density: OuroConversationDensity = OuroConversationDensity.Comfortable,
    sections: List<OuroConversationSection> = emptyList(),
    empty: String = "Nothing here yet",
    moreLabel: String = "More options for",
    unreadLabel: String = "Unread",
    onSelect: (String) -> Unit = {},
    onMore: (String) -> Unit = {},
    onSectionOpenChange: (String, Boolean) -> Unit = { _, _ -> },
) {
    val look = Ouro.look
    val c = look.colors
    val surface = Ouro.surface
    val closed = remember { mutableStateMapOf<String, Boolean>() }
    val entries = buildList<ConversationEntry> {
        conversations.forEach { add(ConversationEntry.Chat(it)) }
        sections.forEach { s ->
            val open = closed[s.id] != true
            add(ConversationEntry.Head(s, open))
            if (open) s.conversations.forEach { add(ConversationEntry.Chat(it)) }
        }
    }
    val titleColor = OuroColor.readable(c.foreground, surface, look.textContrast)
    val quietColor = OuroColor.readable(c.mutedForeground, surface, look.textContrast)
    if (entries.isEmpty()) {
        BasicText(
            empty,
            modifier.fillMaxWidth().padding(24.dp).semantics {
                ouroReport = report(
                    "kind" to "conversation-list",
                    "empty" to true,
                    "text" to OuroColor.hex(quietColor),
                    "behind" to OuroColor.hex(surface),
                )
            },
            style = TextStyle(color = quietColor, fontSize = 14.sp, lineHeight = 20.sp),
        )
        return
    }
    val virtual = entries.size > ConversationVirtualAbove
    val onAccent = OuroColor.readable(c.foreground, c.accent, look.textContrast)
    val root: SemanticsPropertyReceiver.() -> Unit = {
        contentDescription = label
        collectionInfo = CollectionInfo(rowCount = entries.size, columnCount = 1)
        ouroReport = report(
            "kind" to "conversation-list",
            "empty" to false,
            "rows" to entries.count { it is ConversationEntry.Chat },
            "virtual" to virtual,
            "title" to OuroColor.hex(titleColor),
            "text" to OuroColor.hex(quietColor),
            "current" to OuroColor.hex(onAccent),
            "wash" to OuroColor.hex(c.accent),
            "behind" to OuroColor.hex(surface),
        )
    }
    @Composable
    fun entry(index: Int) {
        when (val e = entries[index]) {
            is ConversationEntry.Head -> ConversationHeader(e, index, onToggle = { open ->
                closed[e.section.id] = !open
                onSectionOpenChange(e.section.id, open)
            })
            is ConversationEntry.Chat -> ConversationRow(
                e.conversation, index, e.conversation.id == selectedId, density, moreLabel, unreadLabel, onSelect, onMore,
            )
        }
    }
    if (virtual) {
        LazyColumn(modifier.fillMaxWidth().heightIn(max = 640.dp).semantics(properties = root)) {
            items(entries.size, key = { i -> entries[i].let { if (it is ConversationEntry.Head) "h:${it.section.id}" else "r:${(it as ConversationEntry.Chat).conversation.id}" } }) { i -> entry(i) }
        }
    } else {
        Column(modifier.fillMaxWidth().semantics(properties = root)) {
            for (i in entries.indices) entry(i)
        }
    }
}

@Composable
private fun ConversationHeader(head: ConversationEntry.Head, index: Int, onToggle: (Boolean) -> Unit) {
    val look = Ouro.look
    val c = look.colors
    val color = OuroColor.readable(c.mutedForeground, Ouro.surface, look.textContrast)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val toggle = { onToggle(!head.open) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = OuroMinTarget)
            .then(if (focused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
            .focusable(interactionSource = interaction)
            .pointerInput(head.section.id, head.open, onToggle) { detectTapGestures(onTap = { toggle() }) }
            .onKeyEvent { ev -> (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.Spacebar)).also { if (it) toggle() } }
            .semantics {
                heading()
                role = Role.Button
                contentDescription = head.section.title
                stateDescription = if (head.open) "Expanded" else "Collapsed"
                collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                onClick(null) { toggle(); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            head.section.title,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics {},
            style = TextStyle(color = color, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun ConversationRow(
    chat: OuroConversation,
    index: Int,
    current: Boolean,
    density: OuroConversationDensity,
    moreLabel: String,
    unreadLabel: String,
    onSelect: (String) -> Unit,
    onMore: (String) -> Unit,
) {
    val look = Ouro.look
    val c = look.colors
    val surface = Ouro.surface
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    val fill = when {
        pressed -> lerp(c.accent, c.foreground, 0.12f)
        current -> c.accent
        else -> surface
    }
    val titleColor = OuroColor.readable(c.foreground, if (pressed || current) c.accent else surface, look.textContrast)
    val quietColor = OuroColor.readable(c.mutedForeground, if (pressed || current) c.accent else surface, look.textContrast)
    val comfortable = density == OuroConversationDensity.Comfortable
    val spoken = listOfNotNull(chat.title, if (chat.unread) unreadLabel else null, chat.time.ifBlank { null }, chat.preview?.ifBlank { null }, if (current) "current" else null)
        .joinToString(", ")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = if (comfortable) 72.dp else OuroMinTarget)
                .background(fill)
                .then(if (focused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
                .focusable(interactionSource = interaction)
                .pointerInput(chat.id, onSelect) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onSelect(chat.id) }) }
                .onKeyEvent { ev -> (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.Spacebar)).also { if (it) onSelect(chat.id) } }
                .clearAndSetSemantics {
                    contentDescription = spoken
                    selected = current
                    collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                    onClick(null) { onSelect(chat.id); true }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                if (chat.unread) Box(Modifier.padding(top = 6.dp, end = 8.dp).size(8.dp).background(c.primary, CircleShape))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth()) {
                        BasicText(
                            chat.title,
                            Modifier.weight(1f),
                            style = TextStyle(color = titleColor, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = if (chat.unread) FontWeight.Bold else FontWeight.Normal),
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(chat.time, style = TextStyle(color = quietColor, fontSize = 14.sp, lineHeight = 22.sp))
                    }
                    if (comfortable && !chat.preview.isNullOrBlank()) {
                        BasicText(chat.preview, style = TextStyle(color = quietColor, fontSize = 14.sp, lineHeight = 20.sp))
                    }
                }
            }
        }
        val moreInteraction = remember { MutableInteractionSource() }
        val moreFocused by moreInteraction.collectIsFocusedAsState()
        val dots = OuroColor.readable(c.mutedForeground, surface, look.textContrast)
        val name = "$moreLabel ${chat.title}"
        Box(
            Modifier
                .size(OuroMinTarget)
                .then(if (moreFocused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
                .focusable(interactionSource = moreInteraction)
                .pointerInput(chat.id, onMore) { detectTapGestures(onTap = { onMore(chat.id) }) }
                .onKeyEvent { ev -> (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.Spacebar)).also { if (it) onMore(chat.id) } }
                .semantics {
                    role = Role.Button
                    contentDescription = name
                    onClick(name) { onMore(chat.id); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val r = 1.75.dp.toPx()
                for (i in 0..2) drawCircle(dots, r, Offset(size.width / 2, size.height * (0.2f + 0.3f * i)))
            }
        }
    }
}
