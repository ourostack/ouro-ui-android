// Pictures of each component in light, dark, increased contrast and at 200% text, kept in src/test/snapshots. CI
// verifies them on every pull request (verifyRoborazziDebug); after a deliberate change, record them again
// (recordRoborazziDebug) and commit the new pictures with the change.
package bot.ouro.ui.hx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h6000dp-xxhdpi")
class SnapshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun components_in_light_dark_increased_contrast_and_large_text() {
        var setting by mutableStateOf(LIGHT)
        rule.setContent { Gallery(setting, overlays = false) }
        for (s in listOf(LIGHT, DARK, CONTRAST, CONTRAST_DARK, TEXT_200)) {
            setting = s
            rule.waitForIdle()
            for (kind in listOf("button", "badge", "card", "item", "checkbox", "text-field", "select", "switch", "separator", "description-list", "tabs", "tab-bar", "sidebar")) {
                rule.onNodeWithTag("gallery-$kind").captureRoboImage("src/test/snapshots/$kind-${s.id}.png")
            }
        }
    }

    @Test
    fun overlays_in_light_dark_increased_contrast_and_large_text() {
        var setting by mutableStateOf(LIGHT)
        var kind by mutableStateOf("dialog")
        rule.setContent { Gallery(setting, only = kind) }
        for (k in listOf("dialog", "sheet", "confirm-dialog")) for (s in listOf(LIGHT, DARK, CONTRAST, CONTRAST_DARK, TEXT_200)) {
            kind = k
            setting = s
            rule.waitForIdle()
            rule.onNodeWithTag("gallery-$k").captureRoboImage("src/test/snapshots/$k-${s.id}.png")
        }
    }

    @Test
    fun a_composed_surface_in_light_dark_increased_contrast_and_large_text() {
        var setting by mutableStateOf(LIGHT)
        rule.setContent { ComposedGallery(setting) }
        for (s in listOf(LIGHT, DARK, CONTRAST, CONTRAST_DARK, TEXT_200)) {
            setting = s
            rule.waitForIdle()
            rule.onNodeWithTag("composed-gallery").captureRoboImage("src/test/snapshots/composed-${s.id}.png")
        }
    }

    @Test
    fun every_theme() {
        var setting by mutableStateOf(LIGHT)
        rule.setContent { Gallery(setting, overlays = false) }
        for (theme in bot.ouro.ui.hx.tokens.OuroThemeName.entries) for (dark in listOf(false, true)) {
            setting = Setting("${theme.id}-${if (dark) "dark" else "light"}", theme = theme, dark = dark)
            rule.waitForIdle()
            rule.onNodeWithTag("gallery").captureRoboImage("src/test/snapshots/theme-${setting.id}.png")
        }
    }
}
