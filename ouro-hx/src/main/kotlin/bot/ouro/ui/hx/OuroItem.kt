// Core's Item spec (vendor/core/components/item.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The media before an Item's words: Icon is a 40 dp muted tile hidden from TalkBack; Sm, Md and Lg are 40, 64 and 112 dp pictures. */
enum class OuroMediaSize { Icon, Sm, Md, Lg }

/**
 * A row: media, a title and a line under it, and controls after the words (which go under the words at the largest
 * text sizes). With [onPress] the row is one button named by its title, at least 48 dp tall; its actions stay their
 * own controls.
 */
@Composable
fun OuroItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    variant: OuroFrame = OuroFrame.Plain,
    size: OuroDensity = OuroDensity.Md,
    mediaSize: OuroMediaSize = OuroMediaSize.Sm,
    media: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    onPress: (() -> Unit)? = null,
) {
    val look = Ouro.look
    val c = look.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val surface = if (onPress != null && (hovered || pressed)) c.accent else c.background
    val titleColor = OuroColor.readable(c.foreground, surface, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, surface, look.textContrast)
    val shape = RoundedCornerShape(look.radius / 2)
    val y = if (size == OuroDensity.Sm) 8.dp else 12.dp
    val x = if (variant == OuroFrame.Outline) 16.dp else 0.dp
    val large = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    val reported = report(
        "kind" to "item",
        "pressable" to (onPress != null),
        "title" to OuroColor.hex(titleColor),
        "description" to OuroColor.hex(descriptionColor),
        "surface" to OuroColor.hex(surface),
    )
    val frame = Modifier
        .clip(shape)
        .background(surface, shape)
        .then(if (variant == OuroFrame.Outline) Modifier.border(1.dp, c.border, shape) else Modifier)
    val press = if (onPress != null) {
        Modifier.heightIn(min = OuroMinTarget).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPress).semantics { ouroReport = reported }
    } else {
        Modifier.semantics(mergeDescendants = true) { ouroReport = reported }
    }
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = TextStyle(color = titleColor, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium))
            if (description != null) BasicText(description, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
        }
    }
    val mediaBox: @Composable () -> Unit = {
        if (media != null) {
            if (mediaSize == OuroMediaSize.Icon) {
                Box(Modifier.size(40.dp).background(c.muted, RoundedCornerShape(8.dp)).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalOuroContentColor provides OuroColor.readable(c.mutedForeground, c.muted, 3.0)) { media() }
                }
            } else {
                val side = when (mediaSize) {
                    OuroMediaSize.Md -> 64.dp
                    OuroMediaSize.Lg -> 112.dp
                    else -> 40.dp
                }
                Box(Modifier.size(side).clip(RoundedCornerShape(8.dp))) { media() }
            }
        }
    }
    if (large && actions != null) {
        Column(modifier.then(frame).then(press).padding(horizontal = x, vertical = y), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                mediaBox()
                words(Modifier.weight(1f))
            }
            actions()
        }
    } else {
        Row(modifier.then(frame).then(press).padding(horizontal = x, vertical = y), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            mediaBox()
            words(Modifier.weight(1f))
            actions?.invoke()
        }
    }
}
