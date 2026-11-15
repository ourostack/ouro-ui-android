// The paint rules, without drawing: every tone and variant's words reach 4.5:1 (7:1 with increased contrast) in every
// theme and mode, and the rules for hover, press and soft fills hold.
package bot.ouro.ui.hx

import androidx.compose.ui.graphics.Color
import bot.ouro.ui.hx.tokens.OuroThemeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaintTest {
    @Test
    fun every_tone_and_variant_is_readable_in_every_theme_and_mode() {
        val bad = mutableListOf<String>()
        for (name in OuroThemeName.entries) for (dark in listOf(false, true)) for (increased in listOf(false, true)) {
            val look = OuroLook.of(name, dark, increased)
            for (tone in OuroTone.entries) for (variant in OuroVariant.entries) {
                val p = OuroPaint.resolve(look.colors, tone, variant, look.textContrast)
                val r = OuroColor.contrast(p.label, p.behind(look.colors.background))
                if (r < look.textContrast - 0.01) bad += "${name.id} dark=$dark increased=$increased $tone $variant ${"%.2f".format(r)}:1"
            }
        }
        assertTrue("below the contrast floor:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun increased_contrast_raises_text_and_lines() {
        for (name in OuroThemeName.entries) for (dark in listOf(false, true)) {
            val c = OuroLook.of(name, dark, increasedContrast = true).colors
            assertTrue("${name.id} foreground", OuroColor.contrast(c.foreground, c.background) >= 7.0 - 0.01)
            assertTrue("${name.id} muted words", OuroColor.contrast(c.mutedForeground, c.background) >= 7.0 - 0.01)
            assertTrue("${name.id} border", OuroColor.contrast(c.border, c.background) >= 3.0 - 0.01)
        }
    }

    @Test
    fun plain_has_no_fill_and_soft_is_a_tint() {
        val c = OuroLook.of(OuroThemeName.Pearl, dark = false).colors
        val plain = OuroPaint.resolve(c, OuroTone.Primary, OuroVariant.Plain)
        assertEquals(Color.Unspecified, plain.fill)
        assertEquals(c.background, plain.behind(c.background))
        val soft = OuroPaint.resolve(c, OuroTone.Primary, OuroVariant.Soft)
        assertNotEquals(c.primary, soft.fill)
        assertNotEquals(soft.fill, soft.pressed)
    }
}
