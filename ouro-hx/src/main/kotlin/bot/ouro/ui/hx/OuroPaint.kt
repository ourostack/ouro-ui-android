// How a tone and a variant become colours. The components draw what this returns; nothing else picks colours. The
// same rules as ouro-ui-windows' OuroPaint, so the kits can't drift.
//
// The rule that keeps text readable: a label takes the tone's own colour only where that reads at 4.5:1 or better
// (7:1 with increased contrast) on what is behind it, and otherwise the theme's foreground. So no theme, tone or
// variant can draw unreadable words.
package bot.ouro.ui.hx

import androidx.compose.ui.graphics.Color
import bot.ouro.ui.hx.tokens.OuroColors

/** A colour role from the theme (core's tone property). */
enum class OuroTone { Primary, Secondary, Destructive, Success, Warning }

/** Visual weight (core's variant property). A Badge takes Solid, Soft and Outline. */
enum class OuroVariant { Solid, Soft, Outline, Plain }

/** What a control draws: its fill and edge ([Color.Unspecified] for none), its words, and its hover and pressed fills. */
data class OuroPaintResult(val fill: Color, val label: Color, val edge: Color, val hover: Color, val pressed: Color) {
    /** What the words sit on: the fill, or the surface when there is none. */
    fun behind(background: Color): Color = if (fill == Color.Unspecified) background else fill
}

/** The colour rules for a tone and a variant. */
object OuroPaint {
    /** How much of the tone a soft fill takes, over the surface. */
    const val SOFT_TINT = 0.16

    /** How much a hover, then a press, moves a fill toward the words' colour: "a step stronger". */
    const val HOVER_STEP = 0.08
    const val PRESSED_STEP = 0.16

    /** A tone's colour and the colour of words on it. */
    fun tone(colors: OuroColors, tone: OuroTone): Pair<Color, Color> = when (tone) {
        OuroTone.Primary -> colors.primary to colors.primaryForeground
        OuroTone.Secondary -> colors.secondary to colors.secondaryForeground
        OuroTone.Destructive -> colors.destructive to colors.destructiveForeground
        OuroTone.Success -> colors.success to colors.successForeground
        OuroTone.Warning -> colors.warning to colors.warningForeground
    }

    /**
     * The colours for [tone] and [variant] over [background] (default the theme's page). [minContrast] is 4.5, or 7
     * with increased contrast.
     */
    fun resolve(colors: OuroColors, tone: OuroTone, variant: OuroVariant, minContrast: Double = OuroColor.TEXT_CONTRAST, background: Color = colors.background): OuroPaintResult {
        val (t, onT) = tone(colors, tone)
        val fg = colors.foreground
        val fill: Color
        val label: Color
        val edge: Color
        when (variant) {
            OuroVariant.Solid -> {
                fill = t
                label = if (OuroColor.contrast(onT, t) >= minContrast) onT else OuroColor.push(onT, t, minContrast)
                edge = t
            }
            OuroVariant.Soft -> {
                fill = OuroColor.mix(background, t, SOFT_TINT)
                label = readable(t, fill, fg, minContrast)
                edge = fill
            }
            OuroVariant.Outline -> {
                fill = Color.Unspecified
                label = readable(t, background, fg, minContrast)
                edge = readable(t, background, fg, 3.0)
            }
            OuroVariant.Plain -> {
                fill = Color.Unspecified
                label = readable(t, background, fg, minContrast)
                edge = Color.Unspecified
            }
        }
        val under = if (fill == Color.Unspecified) background else fill
        return OuroPaintResult(fill, label, edge, OuroColor.mix(under, label, HOVER_STEP), OuroColor.mix(under, label, PRESSED_STEP))
    }

    /** The tone's colour when it reads at [min]:1 on [background], else the foreground (pushed if even that doesn't). */
    fun readable(tone: Color, background: Color, foreground: Color, min: Double): Color =
        if (OuroColor.contrast(tone, background) >= min) tone else OuroColor.push(foreground, background, min)
}
