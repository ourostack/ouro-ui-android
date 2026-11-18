// Core's DescriptionList spec (vendor/core/components/description-list.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** One pair of a DescriptionList: a term and its details, such as "Prep time" and "20 minutes". */
data class OuroDescriptionItem(val term: String, val details: String)

/** Below which width a DescriptionList stacks each term over its details (core's `stackBelow`): Sm 448 dp, Md 512 dp. */
enum class OuroStackBelow(val width: Dp) { Sm(448.dp), Md(512.dp) }

/**
 * One thing's details as pairs of a term and its value, such as a recipe's prep time and servings. Each pair is one
 * element for TalkBack, heard as its term, then its details. Narrower than [stackBelow], or at the largest text sizes,
 * each term sits over its details; wider, terms take a column of their own.
 */
@Composable
fun OuroDescriptionList(
    items: List<OuroDescriptionItem>,
    modifier: Modifier = Modifier,
    stackBelow: OuroStackBelow = OuroStackBelow.Sm,
) {
    val look = Ouro.look
    val c = look.colors
    val termColor = OuroColor.readable(c.mutedForeground, c.background, look.textContrast)
    val detailsColor = OuroColor.readable(c.foreground, c.background, look.textContrast)
    val large = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE
    val termStyle = TextStyle(color = termColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val detailsStyle = TextStyle(color = detailsColor, fontSize = 16.sp, lineHeight = 22.sp)
    BoxWithConstraints(
        modifier.semantics {
            isTraversalGroup = true
            ouroReport = report("kind" to "description-list", "term" to OuroColor.hex(termColor), "details" to OuroColor.hex(detailsColor), "surface" to OuroColor.hex(c.background), "pairs" to items.size)
        },
    ) {
        val stacked = large || maxWidth < stackBelow.width
        val termWidth = min(224.dp, maxWidth * 0.4f)
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) OuroSeparator()
                val pair = Modifier.padding(vertical = 12.dp).semantics(mergeDescendants = true) {}
                if (stacked) {
                    Column(pair, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(item.term, style = termStyle)
                        BasicText(item.details, style = detailsStyle)
                    }
                } else {
                    Row(pair, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BasicText(item.term, Modifier.width(termWidth), style = termStyle)
                        BasicText(item.details, Modifier.weight(1f), style = detailsStyle)
                    }
                }
            }
        }
    }
}
