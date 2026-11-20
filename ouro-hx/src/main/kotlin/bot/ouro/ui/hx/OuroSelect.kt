// Core's Select spec (vendor/core/components/select.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** One choice in an [OuroSelect]: the [value] the app keeps and the [label] words that name it. */
data class OuroSelectOption(val value: String, val label: String)

/**
 * A button that shows the chosen words (or the [placeholder]) and a chevron, and opens a menu of [options] below it.
 * The button is at least 48 dp tall. TalkBack names it by its [label] (", required" when [required]) as a dropdown
 * with the expand action, and reads the chosen words, then the [error], then the [description] as its state. Each option is
 * a menu item that says whether it is selected. Space, Enter or Down opens the menu, the arrows move, Enter chooses
 * and Escape closes it and returns focus to the button. The button takes the accent fill while pressed and never
 * scales. [hideLabel] hides the words but keeps the name. Use [OuroTabs] instead when the choices are views of the
 * same thing, or [OuroCheckbox] when each choice is on or off by itself.
 */
@Composable
fun OuroSelect(
    label: String,
    value: String?,
    onValueChange: (String) -> Unit,
    options: List<OuroSelectOption>,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    description: String? = null,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    hideLabel: Boolean = false,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val line = OuroColor.INCREASED_LINE_CONTRAST
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val errorColor = OuroPaint.readable(c.destructive, behind, c.foreground, look.textContrast)
    val edge = if (error != null) OuroPaint.readable(c.destructive, behind, c.foreground, line) else OuroColor.push(c.input, behind, line)
    val ring = OuroColor.push(c.ring, behind, line)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    var open by remember { mutableStateOf(false) }
    var buttonWidth by remember { mutableStateOf(0) }
    var buttonHeight by remember { mutableStateOf(0) }
    val trigger = remember { FocusRequester() }
    val chosen = options.firstOrNull { it.value == value }?.label
    val name = if (required) "$label, required" else label
    val spoken = listOfNotNull(chosen, error, description).joinToString(". ").ifEmpty { null }
    val fill = if (pressed) c.accent else behind
    val textColor = OuroColor.readable(if (pressed) c.accentForeground else c.foreground, fill, look.textContrast)
    val hintColor = OuroColor.readable(c.mutedForeground, fill, look.textContrast)
    val shape = RoundedCornerShape(look.radius)
    val width = with(LocalDensity.current) { buttonWidth.toDp() }
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .semantics {
                ouroReport = report(
                    "kind" to "select",
                    "label" to OuroColor.hex(labelColor),
                    "text" to OuroColor.hex(textColor),
                    "description" to OuroColor.hex(descriptionColor),
                    "error" to OuroColor.hex(errorColor),
                    "behind" to OuroColor.hex(behind),
                    "edge" to OuroColor.hex(edge),
                    "required" to required,
                    "hasError" to (error != null),
                    "hasNotes" to (spoken != null),
                    // The button takes a fill while pressed and never scales, so nothing moves under reduced motion either.
                    "pressScale" to 1f,
                    "pressFill" to true,
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!hideLabel) {
            // Drawn for the eye; TalkBack hears the label as the button's own name, so it isn't read twice.
            BasicText(
                buildAnnotatedString {
                    append(label)
                    if (required) withStyle(SpanStyle(color = errorColor)) { append(" *") }
                },
                Modifier.semantics { hideFromAccessibility() },
                style = TextStyle(color = labelColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { buttonWidth = it.width; buttonHeight = it.height }
                    .sizeIn(minHeight = OuroMinTarget)
                    .focusRequester(trigger)
                    .onKeyEvent { e ->
                        if (enabled && e.type == KeyEventType.KeyDown && (e.key == Key.Spacebar || e.key == Key.DirectionDown)) { open = true; true } else false
                    }
                    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.DropdownList) { open = true }
                    .background(fill, shape)
                    .border(if (focused) 2.dp else 1.dp, if (focused) ring else edge, shape)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = name
                        role = Role.DropdownList
                        if (spoken != null) stateDescription = spoken
                        if (error != null) this.error(error)
                        expand { if (enabled) open = true; enabled }
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    chosen ?: placeholder.orEmpty(),
                    Modifier.weight(1f),
                    style = TextStyle(color = if (chosen != null) textColor else hintColor, fontSize = 16.sp, lineHeight = 22.sp),
                )
                Canvas(Modifier.size(16.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.38f)
                        lineTo(size.width * 0.5f, size.height * 0.68f)
                        lineTo(size.width * 0.8f, size.height * 0.38f)
                    }
                    drawPath(path, hintColor, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (open) {
                val first = remember { FocusRequester() }
                Popup(
                    offset = IntOffset(0, buttonHeight + 4),
                    onDismissRequest = { open = false; trigger.requestFocus() },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier
                            .width(width.coerceAtLeast(160.dp))
                            .background(c.popover, shape)
                            .border(1.dp, edge, shape)
                            .padding(vertical = 4.dp)
                            .onKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { open = false; trigger.requestFocus(); true } else false
                            },
                    ) {
                        for ((i, option) in options.withIndex()) {
                            val isChosen = option.value == value
                            val itemColor = OuroColor.readable(c.popoverForeground, c.popover, look.textContrast)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .sizeIn(minHeight = OuroMinTarget)
                                    .then(if (i == options.indexOfFirst { it.value == value }.coerceAtLeast(0)) Modifier.focusRequester(first) else Modifier)
                                    .clickable(role = Role.Button) { open = false; onValueChange(option.value); trigger.requestFocus() }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = option.label
                                        selected = isChosen
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                BasicText(
                                    option.label,
                                    Modifier.clearAndSetSemantics {},
                                    style = TextStyle(color = itemColor, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal),
                                )
                            }
                        }
                    }
                    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                }
            }
        }
        // The button itself says its error and description as its state, so the drawn lines aren't read twice.
        if (error != null) BasicText(error, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = errorColor, fontSize = 14.sp, lineHeight = 20.sp))
        if (description != null) BasicText(description, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
    }
}
