// Core's ItemGroup spec (vendor/core/components/item-group.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How an [OuroItemGroup] sets its rows apart: Divided draws a line between rows, Plain none, Outline puts the whole list in one bordered box. */
enum class OuroGroupVariant { Divided, Plain, Outline }

/** The rows of an [OuroItemGroup], added with [item] or [items]. */
class OuroItemGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    /** Adds one row, usually an [OuroItem]. */
    fun item(content: @Composable () -> Unit) {
        rows += content
    }

    /** Adds [count] rows, each drawn by [content] with its index. */
    fun items(count: Int, content: @Composable (Int) -> Unit) {
        for (i in 0 until count) rows += { content(i) }
    }
}

/**
 * A labelled list of [OuroItem]s, with a line between the rows ([variant] Divided), none (Plain) or one border around
 * the whole list (Outline). [label] shows above the list and names it for TalkBack; the list is a collection that
 * knows how many rows it holds, and each row is a collection item. With [ordered] each row also says its place
 * ("2 of 5"). With no rows and an [empty] message, the message takes the list's place. Add rows in [content]:
 * `OuroItemGroup("Recipes") { item { OuroItem("Lemon orzo") } }`.
 */
@Composable
fun OuroItemGroup(
    label: String? = null,
    modifier: Modifier = Modifier,
    variant: OuroGroupVariant = OuroGroupVariant.Divided,
    ordered: Boolean = false,
    empty: String? = null,
    content: OuroItemGroupScope.() -> Unit,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val emptyColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val rows = OuroItemGroupScope().apply(content).rows
    if (rows.isEmpty() && empty != null) {
        BasicText(
            empty,
            modifier.fillMaxWidth().padding(24.dp).semantics {
                ouroReport = report(
                    "kind" to "item-group",
                    "empty" to true,
                    "text" to OuroColor.hex(emptyColor),
                    "behind" to OuroColor.hex(behind),
                )
            },
            style = TextStyle(color = emptyColor, fontSize = 14.sp, lineHeight = 20.sp),
        )
        return
    }
    val shape = RoundedCornerShape(12.dp)
    val line = c.border
    Column(
        modifier.fillMaxWidth().semantics {
            if (label != null) contentDescription = label
            collectionInfo = CollectionInfo(rowCount = rows.size, columnCount = 1)
            ouroReport = report(
                "kind" to "item-group",
                "empty" to false,
                "ordered" to ordered,
                "labelled" to (label != null),
                "behind" to OuroColor.hex(behind),
            ) + (if (label != null) ";label=${OuroColor.hex(labelColor)}" else "")
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label != null) {
            BasicText(
                label,
                Modifier.clearAndSetSemantics {},
                style = TextStyle(color = labelColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        Column(
            Modifier.fillMaxWidth().then(if (variant == OuroGroupVariant.Outline) Modifier.clip(shape).border(1.dp, line, shape) else Modifier),
        ) {
            rows.forEachIndexed { index, row ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (variant == OuroGroupVariant.Divided && index > 0) {
                                Modifier.drawBehind { drawRect(line, Offset(0f, 0f), Size(size.width, 1.dp.toPx())) }
                            } else {
                                Modifier
                            },
                        )
                        .semantics(mergeDescendants = true) {
                            collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                            if (ordered) stateDescription = "${index + 1} of ${rows.size}"
                        },
                ) {
                    Box(Modifier.padding(horizontal = if (variant == OuroGroupVariant.Outline) 16.dp else 0.dp)) { row() }
                }
            }
        }
    }
}
