// Colour math, in sRGB: WCAG contrast, mixing, and the increased-contrast colours. Core has no high-contrast tokens
// yet, so they are derived here exactly as ouro-ui-apple's OuroColor.increasedContrast derives them.
package bot.ouro.ui.hx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import bot.ouro.ui.hx.tokens.OuroColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG contrast, mixing and increased contrast. */
object OuroColor {
    /** WCAG's minimum for text. */
    const val TEXT_CONTRAST = 4.5

    /** The contrast increased contrast raises text to (WCAG AAA). */
    const val INCREASED_TEXT_CONTRAST = 7.0

    /** The contrast increased contrast raises borders, inputs and focus rings to. */
    const val INCREASED_LINE_CONTRAST = 3.0

    private val black = Color(0f, 0f, 0f)
    private val white = Color(1f, 1f, 1f)

    /** The colour in sRGB, clipped: a Display P3 token outside sRGB becomes its nearest sRGB colour. */
    fun srgb(c: Color): Color {
        if (c.colorSpace == ColorSpaces.Srgb) return c
        val v = c.colorSpace.connect(ColorSpaces.Srgb).transform(c.red, c.green, c.blue)
        return Color(v[0].coerceIn(0f, 1f), v[1].coerceIn(0f, 1f), v[2].coerceIn(0f, 1f), c.alpha)
    }

    /** WCAG 2 relative luminance. */
    fun luminance(c: Color): Double {
        val s = srgb(c)
        fun linear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * linear(s.red) + 0.7152 * linear(s.green) + 0.0722 * linear(s.blue)
    }

    /** WCAG 2 contrast between two opaque colours, from 1 to 21. */
    fun contrast(a: Color, b: Color): Double {
        val (x, y) = luminance(a) to luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** [t] of the way from [a] to [b], in gamma-encoded sRGB, opaque. */
    fun mix(a: Color, b: Color, t: Double): Color {
        val (p, q) = srgb(a) to srgb(b)
        val k = t.coerceIn(0.0, 1.0).toFloat()
        return Color(p.red + (q.red - p.red) * k, p.green + (q.green - p.green) * k, p.blue + (q.blue - p.blue) * k, 1f)
    }

    /** #AARRGGBB, as the components report it. */
    fun hex(c: Color): String {
        val s = srgb(c)
        fun ch(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X%02X".format(ch(s.alpha), ch(s.red), ch(s.green), ch(s.blue))
    }

    /**
     * The least mix of [color] toward black or white (whichever gains more) that reaches [target] against [surface]
     * (and [other], when given), or the extreme when nothing less does. Returned unchanged when it already reads.
     */
    fun push(color: Color, surface: Color, target: Double, other: Color? = null): Color {
        fun worst(c: Color) = min(contrast(c, surface), other?.let { contrast(c, it) } ?: Double.POSITIVE_INFINITY)
        if (worst(color) >= target) return color
        val toward = if (worst(white) >= worst(black)) white else black
        if (worst(toward) < target) return toward
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2
            if (worst(mix(color, toward, mid)) >= target) hi = mid else lo = mid
        }
        return mix(color, toward, hi)
    }

    /** [text] where it reads at [target]:1 on [surface], else pushed until it does. */
    fun readable(text: Color, surface: Color, target: Double = TEXT_CONTRAST): Color = push(text, surface, target)

    /**
     * The colours for increased contrast: each text colour moves toward black or white until it reaches 7:1 on its
     * surface, and each line until 3:1. A surface moves only when its text can't get there alone.
     */
    fun increasedContrast(c: OuroColors): OuroColors {
        var v = c
        fun separate(get: (OuroColors) -> Color, bg: (OuroColors) -> Color, set: OuroColors.(Color, Color) -> OuroColors) {
            val fg = push(get(v), bg(v), INCREASED_TEXT_CONTRAST)
            val back = if (contrast(fg, bg(v)) < INCREASED_TEXT_CONTRAST) push(bg(v), fg, INCREASED_TEXT_CONTRAST) else bg(v)
            v = v.set(fg, back)
        }
        separate({ it.cardForeground }, { it.card }) { f, b -> copy(cardForeground = f, card = b) }
        separate({ it.popoverForeground }, { it.popover }) { f, b -> copy(popoverForeground = f, popover = b) }
        separate({ it.primaryForeground }, { it.primary }) { f, b -> copy(primaryForeground = f, primary = b) }
        separate({ it.secondaryForeground }, { it.secondary }) { f, b -> copy(secondaryForeground = f, secondary = b) }
        separate({ it.accentForeground }, { it.accent }) { f, b -> copy(accentForeground = f, accent = b) }
        separate({ it.destructiveForeground }, { it.destructive }) { f, b -> copy(destructiveForeground = f, destructive = b) }
        separate({ it.successForeground }, { it.success }) { f, b -> copy(successForeground = f, success = b) }
        separate({ it.warningForeground }, { it.warning }) { f, b -> copy(warningForeground = f, warning = b) }
        separate({ it.sidebarForeground }, { it.sidebar }) { f, b -> copy(sidebarForeground = f, sidebar = b) }
        separate({ it.sidebarPrimaryForeground }, { it.sidebarPrimary }) { f, b -> copy(sidebarPrimaryForeground = f, sidebarPrimary = b) }
        separate({ it.sidebarAccentForeground }, { it.sidebarAccent }) { f, b -> copy(sidebarAccentForeground = f, sidebarAccent = b) }
        separate({ it.foreground }, { it.background }) { f, b -> copy(foreground = f, background = b) }
        // Muted text sits on muted fills and on the page: it must pass on both.
        separate({ it.mutedForeground }, { it.muted }) { f, b -> copy(mutedForeground = f, muted = b) }
        v = v.copy(mutedForeground = push(v.mutedForeground, v.background, INCREASED_TEXT_CONTRAST, v.muted))
        v = v.copy(
            border = push(v.border, v.background, INCREASED_LINE_CONTRAST),
            input = push(v.input, v.background, INCREASED_LINE_CONTRAST),
            ring = push(v.ring, v.background, INCREASED_LINE_CONTRAST),
            sidebarBorder = push(v.sidebarBorder, v.sidebar, INCREASED_LINE_CONTRAST),
            sidebarRing = push(v.sidebarRing, v.sidebar, INCREASED_LINE_CONTRAST),
        )
        return v
    }
}
