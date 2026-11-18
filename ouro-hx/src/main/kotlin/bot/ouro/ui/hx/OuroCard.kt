// Core's Card spec (vendor/core/components/card.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A frame: Outline is a bordered surface, Plain has none. (Core's `variant` for Card and Item.) */
enum class OuroFrame { Outline, Plain }

/** Space inside (core's `size` for Card and Item): Md or Sm. */
enum class OuroDensity { Md, Sm }

/** The colour words take inside a component's slot: a Card's content, an Item's icon. */
val LocalOuroContentColor = compositionLocalOf { Color.Unspecified }

/** Text the large-text layouts switch at: actions go under the words from this font scale. */
internal const val LARGE_TEXT_SCALE = 1.5f

/**
 * A surface that groups a title, a line under it, a body and a row of controls. Its title is a heading; [action] is
 * one control beside the title, which goes under it at the largest text sizes. What it holds reads its surface from
 * [LocalOuroSurface], so its words keep their contrast on the card.
 */
@Composable
fun OuroCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    variant: OuroFrame = OuroFrame.Outline,
    size: OuroDensity = OuroDensity.Md,
    action: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val look = Ouro.look
    val c = look.colors
    val outlined = variant == OuroFrame.Outline
    val surface = if (outlined) c.card else c.background
    val titleColor = OuroColor.readable(if (outlined) c.cardForeground else c.foreground, surface, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, surface, look.textContrast)
    val shape = RoundedCornerShape(look.radius)
    val gap = if (size == OuroDensity.Sm) 12.dp else 16.dp
    val large = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    Column(
        modifier
            .semantics {
                isTraversalGroup = true
                ouroReport = report("kind" to "card", "title" to OuroColor.hex(titleColor), "description" to OuroColor.hex(descriptionColor), "surface" to OuroColor.hex(surface))
            }
            .then(if (outlined) Modifier.background(surface, shape).border(1.dp, c.border, shape).padding(if (size == OuroDensity.Sm) 16.dp else 24.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (title != null || description != null || action != null) {
            val words: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (title != null) BasicText(title, Modifier.semantics { heading() }, style = TextStyle(color = titleColor, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                    if (description != null) BasicText(description, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
                }
            }
            CompositionLocalProvider(LocalOuroSurface provides surface) {
                if (large || action == null) {
                    words()
                    action?.invoke()
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) { words() }
                        action()
                    }
                }
            }
        }
        if (content != null) CompositionLocalProvider(LocalOuroContentColor provides titleColor, LocalOuroSurface provides surface) { content() }
        if (footer != null) CompositionLocalProvider(LocalOuroSurface provides surface) { footer() }
    }
}
