// Core's Table spec (vendor/core/components/table.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which way a sorted [OuroTable] column runs. */
enum class OuroSortDirection { Ascending, Descending }

private val TableColumnWidth = 160.dp
private val TableStickyHeight = 320.dp

/**
 * A data grid: a [caption], a header per column and a row per entry of [rows], each a list of cells the same length as
 * [columns]. The first column is the row header. TalkBack reads the grid's rows and columns, and each row as its cells joined with commas. The grid scrolls sideways
 * inside its own region when it is wider than the screen. With [onRowPress], a row is one button named by its row header
 *. With [sortable] naming columns, their headers are buttons that call [onSort] with the column
 * ([sortedBy], [sortDirection] say which is sorted; its name gains ", sorted ascending" or ", sorted descending" and a
 * change is announced). [striped] shades alternate rows, [dense] shortens plain rows to 32 dp and [stickyHeader] keeps the
 * header in view while the rows scroll. With no rows, [empty] shows in their place.
 * `OuroTable("Recipes", listOf("Dish", "Serves"), listOf(listOf("Soup", "4")))`.
 * Use [OuroDescriptionList] instead for one thing's labelled facts, and [OuroItemGroup] for a list with one column.
 */
@Composable
fun OuroTable(
    caption: String,
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    hideCaption: Boolean = false,
    sortable: List<String> = emptyList(),
    sortedBy: String? = null,
    sortDirection: OuroSortDirection = OuroSortDirection.Ascending,
    striped: Boolean = false,
    dense: Boolean = false,
    stickyHeader: Boolean = false,
    empty: String = "Nothing here yet.",
    sortedLabel: String = "sorted",
    onSort: (String) -> Unit = {},
    onRowPress: ((Int) -> Unit)? = null,
) {
    require(columns.isNotEmpty()) { "OuroTable: columns is empty. Fix: pass at least one column, the first is the row header." }
    require(rows.all { it.size == columns.size }) { "OuroTable: a row has a different number of cells than columns. Fix: give every row ${columns.size} cells." }
    val look = Ouro.look
    val c = look.colors
    val surface = Ouro.surface
    val text = OuroColor.readable(c.foreground, surface, look.textContrast)
    val quiet = OuroColor.readable(c.mutedForeground, surface, look.textContrast)
    val stripe = c.muted
    val onStripe = OuroColor.readable(c.foreground, stripe, look.textContrast)
    val scroll = rememberScrollState()
    val rowHeight: Dp = if (dense) 32.dp else 40.dp
    val reported = report(
        "kind" to "table",
        "rows" to rows.size,
        "striped" to striped,
        "dense" to dense,
        "text" to OuroColor.hex(text),
        "behind" to OuroColor.hex(surface),
        "stripeText" to OuroColor.hex(onStripe),
        "stripe" to OuroColor.hex(stripe),
        "details" to OuroColor.hex(quiet),
    )
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = caption
            collectionInfo = CollectionInfo(rowCount = rows.size + 1, columnCount = columns.size)
            ouroReport = reported
        },
    ) {
        if (!hideCaption) {
            BasicText(
                caption,
                Modifier.padding(bottom = 8.dp).clearAndSetSemantics {},
                style = TextStyle(color = text, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Row {
                columns.forEachIndexed { i, name ->
                    HeaderCell(name, i, name in sortable, name == sortedBy, sortDirection, sortedLabel, text, onSort)
                }
            }
            val body: @Composable () -> Unit = {
                if (rows.isEmpty()) {
                    BasicText(
                        empty,
                        Modifier.width(TableColumnWidth * columns.size).padding(16.dp).semantics {
                            collectionItemInfo = CollectionItemInfo(rowIndex = 1, rowSpan = 1, columnIndex = 0, columnSpan = columns.size)
                        },
                        style = TextStyle(color = quiet, fontSize = 14.sp, lineHeight = 20.sp),
                    )
                } else {
                    rows.forEachIndexed { r, cells ->
                        val shaded = striped && r % 2 == 1
                        TableRow(cells, r, if (shaded) onStripe else text, if (shaded) stripe else null, rowHeight, onRowPress)
                    }
                }
            }
            if (stickyHeader) {
                Column(Modifier.heightIn(max = TableStickyHeight).verticalScroll(rememberScrollState())) { body() }
            } else {
                body()
            }
        }
    }
}

@Composable
private fun HeaderCell(
    name: String,
    index: Int,
    sortable: Boolean,
    sorted: Boolean,
    direction: OuroSortDirection,
    sortedLabel: String,
    color: androidx.compose.ui.graphics.Color,
    onSort: (String) -> Unit,
) {
    val look = Ouro.look
    val c = look.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val word = if (direction == OuroSortDirection.Ascending) "ascending" else "descending"
    val spoken = if (sorted) "$name, $sortedLabel $word" else name
    val base = Modifier.width(TableColumnWidth)
    val info = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
    val modifier = if (sortable) {
        base
            .heightIn(min = OuroMinTarget)
            .then(if (focused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
            .focusable(interactionSource = interaction)
            .pointerInput(name, onSort) { detectTapGestures(onTap = { onSort(name) }) }
            .onKeyEvent { ev -> (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.Spacebar)).also { if (it) onSort(name) } }
            .semantics {
                heading()
                role = Role.Button
                contentDescription = spoken
                collectionItemInfo = info
                if (sorted) {
                    stateDescription = "Sorted $word"
                    liveRegion = LiveRegionMode.Polite
                }
                onClick(null) { onSort(name); true }
            }
    } else {
        base.heightIn(min = 40.dp).semantics { heading(); contentDescription = name; collectionItemInfo = info }
    }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        BasicText(
            name,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics {},
            style = TextStyle(color = color, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    index: Int,
    color: androidx.compose.ui.graphics.Color,
    wash: androidx.compose.ui.graphics.Color?,
    height: Dp,
    onRowPress: ((Int) -> Unit)?,
) {
    val look = Ouro.look
    val c = look.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val linked = onRowPress != null
    val press = { onRowPress?.invoke(index); Unit }
    var modifier: Modifier = Modifier.heightIn(min = if (linked) OuroMinTarget else height)
    if (wash != null) modifier = modifier.background(wash)
    if (linked) {
        modifier = modifier
            .then(if (focused) Modifier.border(2.dp, c.ring, RoundedCornerShape(look.radius)) else Modifier)
            .focusable(interactionSource = interaction)
            .pointerInput(index, onRowPress) { detectTapGestures(onTap = { press() }) }
            .onKeyEvent { ev -> (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.Spacebar)).also { if (it) press() } }
            .semantics {
                role = Role.Button
                contentDescription = cells.first()
                collectionItemInfo = CollectionItemInfo(rowIndex = index + 1, rowSpan = 1, columnIndex = 0, columnSpan = cells.size)
                onClick(null) { press(); true }
            }
    } else {
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = cells.joinToString(", ")
            collectionItemInfo = CollectionItemInfo(rowIndex = index + 1, rowSpan = 1, columnIndex = 0, columnSpan = cells.size)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { col, cell ->
            val cellInfo = CollectionItemInfo(rowIndex = index + 1, rowSpan = 1, columnIndex = col, columnSpan = 1)
            BasicText(
                cell,
                Modifier.width(TableColumnWidth).padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(if (linked) Modifier.clearAndSetSemantics {} else Modifier.semantics { collectionItemInfo = cellInfo }),
                style = TextStyle(color = color, fontSize = 14.sp, lineHeight = 20.sp),
            )
        }
    }
}
