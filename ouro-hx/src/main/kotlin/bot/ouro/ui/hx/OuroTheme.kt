// The theme the components read: core's colours for a theme and mode, raised for increased contrast, and whether
// motion is reduced. Each follows the system setting unless the app passes it.
package bot.ouro.ui.hx

import android.animation.ValueAnimator
import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import bot.ouro.ui.hx.tokens.OuroColors
import bot.ouro.ui.hx.tokens.OuroThemeName
import bot.ouro.ui.hx.tokens.OuroTokens

/** What the components draw with, provided by [OuroTheme]. */
data class OuroLook(
    val name: OuroThemeName,
    val dark: Boolean,
    /** Android's contrast setting is Medium or High: text reads at 7:1 and lines at 3:1. */
    val increasedContrast: Boolean,
    /** Android's Remove animations is on: nothing moves, presses don't scale. */
    val reduceMotion: Boolean,
    val colors: OuroColors,
    /** The theme's base corner radius. */
    val radius: Dp,
) {
    /** The contrast the components' words reach: 4.5:1, or 7:1 with increased contrast. */
    val textContrast: Double get() = if (increasedContrast) OuroColor.INCREASED_TEXT_CONTRAST else OuroColor.TEXT_CONTRAST

    companion object {
        /** The look for a theme and settings. */
        fun of(name: OuroThemeName, dark: Boolean, increasedContrast: Boolean = false, reduceMotion: Boolean = false): OuroLook {
            val theme = OuroTokens.theme(name)
            val base = theme.colors(dark)
            return OuroLook(name, dark, increasedContrast, reduceMotion, if (increasedContrast) OuroColor.increasedContrast(base) else base, theme.radius)
        }
    }
}

/** The look the components read. Outside [OuroTheme] it is Pearl in light mode. */
val LocalOuroLook = staticCompositionLocalOf { OuroLook.of(OuroTokens.defaultTheme, dark = false) }

/** The current look: `Ouro.look.colors.primary`. */
object Ouro {
    val look: OuroLook
        @Composable @ReadOnlyComposable
        get() = LocalOuroLook.current
}

/**
 * Ouro HX for [content]: [name]'s colours in light or dark mode, raised for increased contrast, with motion reduced
 * when the person asks. Each setting follows the system unless passed.
 */
@Composable
fun OuroTheme(
    name: OuroThemeName = OuroTokens.defaultTheme,
    dark: Boolean = isSystemInDarkTheme(),
    increasedContrast: Boolean = systemIncreasedContrast(),
    reduceMotion: Boolean = systemReduceMotion(),
    content: @Composable () -> Unit,
) {
    val look = remember(name, dark, increasedContrast, reduceMotion) { OuroLook.of(name, dark, increasedContrast, reduceMotion) }
    CompositionLocalProvider(LocalOuroLook provides look, content = content)
}

/** Android's contrast setting (Settings, Accessibility, Color and motion, Contrast) at Medium or High. Android 14 and later. */
@Composable
fun systemIncreasedContrast(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val context = LocalContext.current
    val ui = remember(context) { context.getSystemService(UiModeManager::class.java) } ?: return false
    var contrast by remember(ui) { mutableFloatStateOf(runCatching { ui.contrast }.getOrDefault(0f)) }
    DisposableEffect(ui) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        runCatching { ui.addContrastChangeListener(context.mainExecutor, listener) }
        onDispose { runCatching { ui.removeContrastChangeListener(listener) } }
    }
    return contrast >= 0.5f
}

/** Android's Remove animations (animator duration scale 0). */
@Composable
fun systemReduceMotion(): Boolean = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled() }
