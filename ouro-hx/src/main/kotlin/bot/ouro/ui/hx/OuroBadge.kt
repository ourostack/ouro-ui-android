// Core's Badge spec (vendor/core/components/badge.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A short label or a count beside something else, such as Vegan or 3. It is words, not a control: TalkBack reads its
 * text, and its icon is hidden. [variant] is Solid, Soft or Outline.
 */
@Composable
fun OuroBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: OuroTone = OuroTone.Secondary,
    variant: OuroVariant = OuroVariant.Solid,
    icon: (@Composable () -> Unit)? = null,
) {
    require(variant != OuroVariant.Plain) { "OuroBadge: variant is Plain, which a Badge doesn't take. Fix: Solid, Soft or Outline." }
    val look = Ouro.look
    val under = Ouro.surface
    val paint = remember(look, tone, variant, under) { OuroPaint.resolve(look.colors, tone, variant, look.textContrast, under) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .semantics(mergeDescendants = true) {
                ouroReport = report(
                    "kind" to "badge",
                    "tone" to tone.name.lowercase(),
                    "variant" to variant.name.lowercase(),
                    "label" to OuroColor.hex(paint.label),
                    "behind" to OuroColor.hex(paint.behind(under)),
                )
            }
            .heightIn(min = 24.dp)
            .then(if (paint.fill != Color.Unspecified) Modifier.background(paint.fill, shape) else Modifier)
            .then(if (paint.edge != Color.Unspecified) Modifier.border(1.dp, paint.edge, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Box(Modifier.clearAndSetSemantics {}) { icon() }
        BasicText(text, style = TextStyle(color = paint.label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold))
    }
}
