// Core's TextField spec (vendor/core/components/text-field.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What kind of text a field holds (core's `type`): each sets the keyboard. Password hides the text and adds a Show button. */
enum class OuroTextFieldType { Text, Email, Password, Tel, Url, Search }

/** How a field is drawn (core's `variant`): Outline is a box with an edge, Plain is the line under it only. */
enum class OuroTextFieldVariant { Outline, Plain }

/** How tall a field is (core's `size`): Md is 48 dp with 16 sp text, Lg is 56 dp with larger text. */
enum class OuroTextFieldSize { Md, Lg }

/**
 * A labelled field for a line of text, at least 48 dp tall. The [label] stays above it and is its name for TalkBack
 * (", required" when [required]); a [placeholder] is an example of what to type, never the name. An [error] turns the
 * edge destructive and is read with the field as its state description, then the error and the [description]. [clearable] adds a Clear button while there is text, and [variant] and [size] set how it is drawn. [hideLabel] hides the
 * words but keeps the name. [keyboardType] overrides the type's keyboard, such as [KeyboardType.Number] for a number,
 * and [minLines] above 1 makes a longer answer over several lines.
 */
@Composable
fun OuroTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    placeholder: String? = null,
    type: OuroTextFieldType = OuroTextFieldType.Text,
    required: Boolean = false,
    hideLabel: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType? = null,
    minLines: Int = 1,
    onSubmit: (() -> Unit)? = null,
    variant: OuroTextFieldVariant = OuroTextFieldVariant.Outline,
    size: OuroTextFieldSize = OuroTextFieldSize.Md,
    clearable: Boolean = false,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val textColor = labelColor
    val descriptionColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val errorColor = OuroPaint.readable(c.destructive, behind, c.foreground, look.textContrast)
    val line = OuroColor.INCREASED_LINE_CONTRAST
    val edge = if (error != null) OuroPaint.readable(c.destructive, behind, c.foreground, line) else OuroColor.push(c.input, behind, line)
    val ring = OuroColor.push(c.ring, behind, line)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }
    val password = type == OuroTextFieldType.Password
    val singleLine = minLines <= 1
    val name = if (required) "$label, required" else label
    val shape = RoundedCornerShape(look.radius)
    val large = size == OuroTextFieldSize.Lg
    val plain = variant == OuroTextFieldVariant.Plain
    val spoken = listOfNotNull(error, description).joinToString(" ").ifEmpty { null }
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .semantics {
                ouroReport = report(
                    "kind" to "text-field",
                    "label" to OuroColor.hex(labelColor),
                    "text" to OuroColor.hex(textColor),
                    "description" to OuroColor.hex(descriptionColor),
                    "error" to OuroColor.hex(errorColor),
                    "behind" to OuroColor.hex(behind),
                    "edge" to OuroColor.hex(edge),
                    "required" to required,
                    "hasError" to (error != null),
                    "hasNotes" to (spoken != null),
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!hideLabel) {
            // Drawn for the eye; TalkBack hears the label as the field's own name, so it isn't read twice.
            BasicText(
                buildAnnotatedString {
                    append(label)
                    if (required) withStyle(SpanStyle(color = errorColor)) { append(" *") }
                },
                Modifier.semantics { hideFromAccessibility() },
                style = TextStyle(color = labelColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (large) 56.dp else OuroMinTarget)
                .semantics {
                    contentDescription = name
                    if (error != null) this.error(error)
                    if (spoken != null) stateDescription = spoken
                },
            enabled = enabled,
            textStyle = TextStyle(color = textColor, fontSize = if (large) 18.sp else 16.sp, lineHeight = if (large) 24.sp else 22.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType ?: when (type) {
                    OuroTextFieldType.Email -> KeyboardType.Email
                    OuroTextFieldType.Password -> KeyboardType.Password
                    OuroTextFieldType.Tel -> KeyboardType.Phone
                    OuroTextFieldType.Url -> KeyboardType.Uri
                    else -> KeyboardType.Text
                },
                imeAction = when {
                    !singleLine -> ImeAction.Default
                    type == OuroTextFieldType.Search -> ImeAction.Search
                    else -> ImeAction.Done
                },
            ),
            // Without onSubmit, Done keeps its own action: it hides the keyboard.
            keyboardActions = KeyboardActions(
                onDone = onSubmit?.let { submit ->
                    fun KeyboardActionScope.() {
                        submit()
                        defaultKeyboardAction(ImeAction.Done)
                    }
                },
                onSearch = onSubmit?.let { submit -> fun KeyboardActionScope.() { submit() } },
            ),
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = interaction,
            cursorBrush = SolidColor(textColor),
            decorationBox = { inner ->
                val lineColor = if (focused) ring else edge
                val lineWidth = if (focused) 2.dp else 1.dp
                val frame = if (plain) {
                    Modifier.drawBehind {
                        val y = size.height - lineWidth.toPx() / 2
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), lineWidth.toPx())
                    }
                } else {
                    Modifier.background(behind, shape).border(lineWidth, lineColor, shape)
                }
                Row(
                    frame.padding(start = if (plain) 0.dp else 12.dp, end = if (password || clearable) 0.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (value.isEmpty() && placeholder != null) {
                            BasicText(placeholder, Modifier.clearAndSetSemantics {}, style = TextStyle(color = descriptionColor, fontSize = 16.sp, lineHeight = 22.sp))
                        }
                        inner()
                    }
                    if (clearable && value.isNotEmpty() && enabled) {
                        Box(
                            Modifier
                                .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                                .clickable(role = Role.Button) { onValueChange("") }
                                .clearAndSetSemantics {
                                    contentDescription = "Clear"
                                    role = Role.Button
                                    onClick { onValueChange(""); true }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText("Clear", style = TextStyle(color = descriptionColor, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    if (password) {
                        Box(
                            Modifier
                                .sizeIn(minWidth = OuroMinTarget, minHeight = OuroMinTarget)
                                .clickable(enabled = enabled, role = Role.Button) { revealed = !revealed }
                                .clearAndSetSemantics {
                                    contentDescription = "Show password"
                                    role = Role.Button
                                    onClick { revealed = !revealed; true }
                                    stateDescription = if (revealed) "Shown" else "Hidden"
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(if (revealed) "Hide" else "Show", style = TextStyle(color = descriptionColor, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            },
        )
        // The field itself says its error; the words under it are for the eye, so they aren't read twice.
        if (error != null) BasicText(error, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = errorColor, fontSize = 14.sp, lineHeight = 20.sp))
        if (description != null) BasicText(description, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
    }
}
