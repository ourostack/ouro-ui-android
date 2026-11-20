// Core's Field spec (vendor/core/components/field.spec.json), in Compose.
package bot.ouro.ui.hx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Words around one control of your own: the [label] above it, the [description] under that and the [error] under the
 * control, 8 dp apart. [control] is given a [Modifier] to put on the one control it draws, and whether the field is
 * [enabled]. That modifier makes the control's name the label (", required" when [required]) and its state
 * description the error then the description, and the drawn words are hidden from TalkBack so nothing is read
 * twice. A field adds no element and no tab stop. [hideLabel] hides the words but keeps the name. Use
 * [OuroTextField], [OuroSelect], [OuroCheckbox] or [OuroSwitch] instead when one of them is the control, because each
 * has its own label, description and error.
 */
@Composable
fun OuroField(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    hideLabel: Boolean = false,
    control: @Composable (controlModifier: Modifier, enabled: Boolean) -> Unit,
) {
    val look = Ouro.look
    val c = look.colors
    val behind = Ouro.surface
    val labelColor = OuroColor.readable(c.foreground, behind, look.textContrast)
    val descriptionColor = OuroColor.readable(c.mutedForeground, behind, look.textContrast)
    val errorColor = OuroPaint.readable(c.destructive, behind, c.foreground, look.textContrast)
    val name = if (required) "$label, required" else label
    val spoken = listOfNotNull(error, description).joinToString(". ").ifEmpty { null }
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .semantics {
                ouroReport = report(
                    "kind" to "field",
                    "label" to OuroColor.hex(labelColor),
                    "description" to OuroColor.hex(descriptionColor),
                    "error" to OuroColor.hex(errorColor),
                    "behind" to OuroColor.hex(behind),
                    "required" to required,
                    "hasError" to (error != null),
                    "hasNotes" to (spoken != null),
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!hideLabel) {
            BasicText(
                buildAnnotatedString {
                    append(label)
                    if (required) withStyle(SpanStyle(color = errorColor)) { append(" *") }
                },
                Modifier.semantics { hideFromAccessibility() },
                style = TextStyle(color = labelColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            )
        }
        if (description != null) BasicText(description, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = descriptionColor, fontSize = 14.sp, lineHeight = 20.sp))
        control(
            Modifier.semantics {
                contentDescription = name
                if (error != null) this.error(error)
                if (spoken != null) stateDescription = spoken
            },
            enabled,
        )
        if (error != null) BasicText(error, Modifier.semantics { hideFromAccessibility() }, style = TextStyle(color = errorColor, fontSize = 14.sp, lineHeight = 20.sp))
    }
}
