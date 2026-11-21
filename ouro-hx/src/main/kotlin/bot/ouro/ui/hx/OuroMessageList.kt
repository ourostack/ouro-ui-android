// Core's MessageList spec (vendor/core/components/message-list.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** One message for [OuroMessageList]: its [id], [author], [time] as already written, [text], and whether it is the person's [mine]. */
data class OuroChatMessage(val id: String, val author: String, val time: String, val text: String, val mine: Boolean = false)

/** Without a bounded height a list stops growing at 640 dp and scrolls. A list builds every message up to this many, and only the messages in view above it. */
internal const val MessageListVirtualAbove = 20

/**
 * A conversation: [messages] in the order sent, in a scrolling region named [label] that opens at the newest message.
 * Each message is read as [OuroMessage] reads it. A message from someone else that arrives while the list is in use is
 * announced once, politely, as "author: text", and passed to [onNewMessage]; the history the list opens with, older
 * messages loaded above it and the person's own messages are never announced. A new message scrolls into view only
 * when the reader was already at the newest one; once they scroll up, a button named [jumpLabel] appears and takes
 * them back. With [hasOlder], a button named [olderLabel] above the first message calls [onLoadOlder]. With more than
 * 20 messages only those in view are built, and the list still reports its full count. Use [OuroMessage] instead
 * for a single message, and [OuroConversationList] for a list of conversations.
 *
 * ```
 * OuroMessageList("Conversation with Ana", listOf(OuroChatMessage("a", "Ana", "9:41", "See you at noon.")))
 * ```
 */
@Composable
fun OuroMessageList(
    label: String,
    messages: List<OuroChatMessage>,
    modifier: Modifier = Modifier,
    jumpLabel: String = "Jump to latest",
    hasOlder: Boolean = false,
    olderLabel: String = "Load older messages",
    onLoadOlder: () -> Unit = {},
    onNewMessage: (OuroChatMessage) -> Unit = {},
) {
    val look = Ouro.look
    val c = look.colors
    val surface = Ouro.surface
    val line = OuroColor.INCREASED_LINE_CONTRAST
    val textColor = OuroColor.readable(c.foreground, surface, look.textContrast)
    val jumpText = OuroColor.readable(c.primaryForeground, c.primary, look.textContrast)
    val edge = OuroColor.push(c.input, surface, line)
    val virtual = messages.size > MessageListVirtualAbove
    val extra = if (hasOlder) 1 else 0
    val itemCount = messages.size + extra
    val scroll = rememberScrollState(Int.MAX_VALUE)
    val lazy = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(itemCount - 1, 0))
    val scrollable: ScrollableState = if (virtual) lazy else scroll
    val scope = rememberCoroutineScope()
    val toEnd: suspend () -> Unit = {
        if (virtual) lazy.scrollToItem(maxOf(itemCount - 1, 0)) else scroll.scrollTo(scroll.maxValue)
    }
    // Read while composing the new list, before it is laid out, so it says where the reader was.
    val wasAtBottom = !scrollable.canScrollForward
    val shown = remember { arrayOf(messages.firstOrNull()?.id, messages.lastOrNull()?.id) }
    LaunchedEffect(messages.firstOrNull()?.id, messages.lastOrNull()?.id) {
        val first = messages.firstOrNull()
        val last = messages.lastOrNull()
        // Older messages loaded above push a reader who was at the newest message up the list, so bring them back.
        val newer = last != null && shown[1] != last.id
        val older = first != null && shown[0] != first.id && !newer
        shown[0] = first?.id
        shown[1] = last?.id
        if ((newer && (wasAtBottom || last!!.mine)) || (older && wasAtBottom && !virtual)) toEnd()
    }
    val previous = remember { arrayOf(messages) }
    var announcement by remember { mutableStateOf("") }
    var flip by remember { mutableStateOf(false) }
    LaunchedEffect(messages) {
        val before = previous[0]
        previous[0] = messages
        // Only what follows the old newest message is new: older messages loaded above it change nothing.
        val at = before.lastOrNull()?.let { l -> messages.indexOfFirst { it.id == l.id } } ?: -1
        val arrived = if (at >= 0) messages.drop(at + 1).filter { !it.mine } else emptyList()
        if (arrived.isNotEmpty()) {
            // A zero-width mark that alternates, so the same words twice in a row still change the live region.
            flip = !flip
            announcement = arrived.joinToString(". ") { "${it.author}: ${it.text}" } + if (flip) "\u200B" else ""
            arrived.forEach(onNewMessage)
        }
    }
    val root: androidx.compose.ui.semantics.SemanticsPropertyReceiver.() -> Unit = {
        contentDescription = label
        collectionInfo = CollectionInfo(rowCount = itemCount, columnCount = 1)
        ouroReport = report(
            "kind" to "message-list",
            "spoken" to label,
            "messages" to messages.size,
            "virtual" to virtual,
            "text" to OuroColor.hex(textColor),
            "jumpText" to OuroColor.hex(jumpText),
            "jumpFill" to OuroColor.hex(c.primary),
            "edge" to OuroColor.hex(edge),
            "behind" to OuroColor.hex(surface),
            "announce" to "polite",
        )
    }
    @Composable
    fun older() {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onLoadOlder)
                    .semantics(mergeDescendants = true) { contentDescription = olderLabel },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    olderLabel,
                    Modifier
                        .border(if (focused) 2.dp else 1.dp, if (focused) OuroColor.push(c.ring, surface, line) else edge, RoundedCornerShape(look.radius + 8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = TextStyle(color = textColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
    @Composable
    fun message(m: OuroChatMessage) = OuroMessage(m.author, m.text, role = if (m.mine) OuroMessageRole.User else OuroMessageRole.Assistant, time = m.time.ifBlank { null })
    BoxWithConstraints(modifier.semantics { isTraversalGroup = true }) {
        val bounded = constraints.hasBoundedHeight
        val gap = Arrangement.spacedBy(8.dp, Alignment.Bottom)
        if (virtual) {
            LazyColumn(
                Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier.heightIn(max = 640.dp)).semantics(properties = root),
                state = lazy,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = gap,
            ) {
                if (hasOlder) item(key = "older") { older() }
                items(messages, key = { it.id }) { message(it) }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().then(if (bounded) Modifier.fillMaxHeight() else Modifier.heightIn(max = 640.dp)).semantics(properties = root).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = gap,
            ) {
                if (hasOlder) older()
                for (m in messages) message(m)
            }
        }
        if (scrollable.canScrollForward) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { scope.launch { toEnd() } }
                    .semantics(mergeDescendants = true) { contentDescription = jumpLabel },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    jumpLabel,
                    Modifier
                        .background(c.primary, RoundedCornerShape(look.radius + 8.dp))
                        .then(if (focused) Modifier.border(2.dp, OuroColor.push(c.ring, surface, line), RoundedCornerShape(look.radius + 8.dp)) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = TextStyle(color = jumpText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; text = AnnotatedString(announcement) })
    }
}
