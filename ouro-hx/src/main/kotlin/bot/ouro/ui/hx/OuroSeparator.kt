// Core's Separator spec (vendor/core/components/separator.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which way a Separator runs: across its container, or as tall as its row. */
enum class OuroOrientation { Horizontal, Vertical }

/**
 * A thin line between groups of content. The line is hidden from TalkBack (Android has no separator role); a [label],
 * such as "or", sits in a gap at its middle and is read as text. [tone] colours the line for a status; the label stays
 * muted. A vertical separator takes no label.
 */
@Composable
fun OuroSeparator(
    modifier: Modifier = Modifier,
    label: String? = null,
    orientation: OuroOrientation = OuroOrientation.Horizontal,
    tone: OuroTone? = null,
) {
    require(tone != OuroTone.Secondary) { "OuroSeparator: tone is Secondary, which a Separator doesn't take. Fix: Primary, Destructive, Success, Warning, or none." }
    require(label == null || orientation == OuroOrientation.Horizontal) { "OuroSeparator: a vertical separator has a label. Fix: drop the label, or make it horizontal." }
    val look = Ouro.look
    val c = look.colors
    val lineColor = tone?.let { OuroPaint.tone(c, it).first } ?: c.border
    val labelColor = OuroColor.readable(c.mutedForeground, c.background, look.textContrast)
    val reported = report("kind" to "separator", "label" to OuroColor.hex(labelColor), "behind" to OuroColor.hex(c.background), "labelled" to (label != null))
    if (orientation == OuroOrientation.Vertical) {
        Box(modifier.fillMaxHeight().width(1.dp).background(lineColor).clearAndSetSemantics { ouroReport = reported })
        return
    }
    if (label == null) {
        Box(modifier.fillMaxWidth().height(1.dp).background(lineColor).clearAndSetSemantics { ouroReport = reported })
        return
    }
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { ouroReport = reported }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(lineColor))
        BasicText(label, style = TextStyle(color = labelColor, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center))
        Box(Modifier.weight(1f).height(1.dp).background(lineColor))
    }
}
