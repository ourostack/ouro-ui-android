// Core's ReactionBar spec (vendor/core/components/reaction-bar.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One reaction on a message: its [key] (what [OuroReactionBar]'s `onReact` carries), [emoji], [name], [count] and whether it is [mine]. */
data class OuroReaction(val key: String, val emoji: String, val name: String, val count: Int, val mine: Boolean = false)

/**
 * The reactions on a message: one pill per reaction with its emoji and count, that a person presses to add theirs or
 * take it back. [onReact] carries the pressed reaction's key and the host adds or removes the person's own; the
 * person's own pill is tinted with the accent. TalkBack reads each pill as "Like, 3" with its on or off state, inside
 * a group named [label]. Each pill is drawn 28 dp tall and has a 48 dp hit area. [disabled] keeps the counts but turns
 * off reacting. [showMore] adds a button named [moreLabel] after the pills, which calls [onMore]. The pills wrap onto
 * more lines when the width runs out. Use [OuroBadge] instead for a count that nobody presses.
 *
 * ```
 * OuroReactionBar(listOf(OuroReaction("like", "👍", "Like", 3, mine = true)), onReact = { toggle(it) })
 * ```
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OuroReactionBar(
    reactions: List<OuroReaction>,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Reactions",
    disabled: Boolean = false,
    moreLabel: String = "Add reaction",
    showMore: Boolean = false,
    onMore: () -> Unit = {},
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val line = OuroColor.INCREASED_LINE_CONTRAST
    val edge = OuroColor.push(c.input, behind, line)
    val countColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val mineText = OuroColor.readable(c.foreground, c.accent, look.textContrast)
    val shape = RoundedCornerShape(look.radius + 8.dp)
    FlowRow(
        modifier.semantics {
            contentDescription = label
            ouroReport = report(
                "kind" to "reaction-bar",
                "count" to OuroColor.hex(countColor),
                "mineText" to OuroColor.hex(mineText),
                "mineFill" to OuroColor.hex(c.accent),
                "edge" to OuroColor.hex(edge),
                "behind" to OuroColor.hex(behind),
                "spoken" to label,
                "disabled" to disabled,
            )
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (r in reactions) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            val fill = when {
                pressed && r.mine -> lerp(c.accent, c.foreground, 0.12f)
                r.mine -> c.accent
                pressed -> c.muted
                else -> behind
            }
            Box(
                Modifier
                    .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                    .toggleable(value = r.mine, interactionSource = interaction, indication = null, enabled = !disabled, role = Role.Checkbox) { onReact(r.key) }
                    .semantics(mergeDescendants = true) { contentDescription = "${r.name}, ${r.count}" },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .height(28.dp)
                        .background(fill, shape)
                        .border(if (focused) 2.dp else 1.dp, if (focused) OuroColor.push(c.ring, behind, line) else edge, shape)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(r.emoji, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp))
                    BasicText(r.count.toString(), style = TextStyle(color = if (r.mine) mineText else countColor, fontSize = 14.sp, lineHeight = 20.sp))
                }
            }
        }
        if (showMore) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Box(
                Modifier
                    .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                    .clickable(interactionSource = interaction, indication = null, enabled = !disabled, role = Role.Button, onClick = onMore)
                    .semantics(mergeDescendants = true) { contentDescription = moreLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .height(28.dp)
                        .background(behind, shape)
                        .border(if (focused) 2.dp else 1.dp, if (focused) OuroColor.push(c.ring, behind, line) else edge, shape)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("+", style = TextStyle(color = countColor, fontSize = 16.sp, lineHeight = 20.sp))
                }
            }
        }
    }
}
