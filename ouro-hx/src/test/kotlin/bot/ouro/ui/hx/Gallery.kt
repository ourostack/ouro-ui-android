package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.ouro.ui.hx.composed.ComposedCatalog
import bot.ouro.ui.hx.composed.ComposedJson
import bot.ouro.ui.hx.composed.ComposedNode
import bot.ouro.ui.hx.tokens.OuroThemeName

/** One setting a claim is checked under. */
data class Setting(
    val id: String,
    val theme: OuroThemeName = OuroThemeName.Pearl,
    val dark: Boolean = false,
    val increasedContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val fontScale: Float = 1f,
)

val LIGHT = Setting("light")
val DARK = Setting("dark", dark = true)
val CONTRAST = Setting("contrast", increasedContrast = true)
val CONTRAST_DARK = Setting("contrast-dark", dark = true, increasedContrast = true)
val NO_ANIMATIONS = Setting("noanim", reduceMotion = true)
val TEXT_200 = Setting("text200", fontScale = 2f)

/** Words long enough to need a second line in a narrow column at 200% text. */
const val LONG = "Add everything from this week's recipes to the list"

/** Every component the kit ships, in every tone and variant and in each of core's conformance cases. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Gallery(setting: Setting, only: String? = null, long: Boolean = false) {
    val density = LocalDensity.current
    OuroTheme(setting.theme, dark = setting.dark, increasedContrast = setting.increasedContrast, reduceMotion = setting.reduceMotion) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, setting.fontScale)) {
            val c = Ouro.look.colors
            Column(
                // Long words at 200% run far past the window, so the long gallery scrolls: every component gets the height it asks
                // for, as in an app, and none is squeezed to nothing below the fold.
                Modifier.testTag("gallery").then(if (long) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .background(c.background).padding(16.dp).then(if (long) Modifier.width(240.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (only == null || only == "button") {
                    FlowRow(Modifier.testTag("gallery-button"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (v in OuroVariant.entries) for (t in OuroTone.entries) {
                            OuroButton(if (long) LONG else t.name, onPress = {}, Modifier.testTag("button-${t.name}-${v.name}"), tone = t, variant = v)
                        }
                        OuroButton("Delete list", onPress = {}, Modifier.testTag("button-case-destructive-outline"), tone = OuroTone.Destructive, variant = OuroVariant.Outline)
                    }
                }
                if (only == null || only == "badge") {
                    FlowRow(Modifier.testTag("gallery-badge"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (v in listOf(OuroVariant.Solid, OuroVariant.Soft, OuroVariant.Outline)) for (t in OuroTone.entries) {
                            OuroBadge(if (long) LONG else t.name, Modifier.testTag("badge-${t.name}-${v.name}"), tone = t, variant = v)
                        }
                        OuroBadge("Vegan", Modifier.testTag("badge-case-soft-success"), tone = OuroTone.Success, variant = OuroVariant.Soft)
                        OuroBadge("3", Modifier.testTag("badge-case-solid-count"), tone = OuroTone.Destructive)
                    }
                }
                if (only == null || only == "card") {
                    Column(Modifier.testTag("gallery-card"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroCard(
                            Modifier.testTag("card-case-titled-with-action"),
                            title = if (long) LONG else "Pantry",
                            description = "What you have on hand",
                            action = { OuroButton("Edit", onPress = {}, variant = OuroVariant.Soft, tone = OuroTone.Secondary, size = OuroSize.Sm) },
                        ) { BasicText("Flour, rice, black beans.", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) }
                        OuroCard(Modifier.testTag("card-sm"), title = "Weeknights", description = "Quick dinners", size = OuroDensity.Sm)
                    }
                }
                if (only == null || only == "item") {
                    Column(Modifier.testTag("gallery-item"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OuroItem(
                            if (long) LONG else "Braised short ribs",
                            Modifier.testTag("item-case-pressable-with-badge"),
                            description = "3 hours, serves six",
                            mediaSize = OuroMediaSize.Icon,
                            // An icon-sized picture, not words: icons keep their size when text grows.
                            media = { Box(Modifier.size(20.dp).background(LocalOuroContentColor.current, CircleShape)) },
                            actions = { OuroBadge("New", tone = OuroTone.Primary, variant = OuroVariant.Soft) },
                            onPress = {},
                        )
                        OuroItem("Lemon orzo", Modifier.testTag("item-case-static-row"), description = "25 minutes")
                        OuroItem("Pantry list", Modifier.testTag("item-outline"), description = "12 things", variant = OuroFrame.Outline, size = OuroDensity.Sm, onPress = {})
                    }
                }
                if (only == null || only == "checkbox") {
                    Column(Modifier.testTag("gallery-checkbox"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OuroCheckbox(if (long) LONG else "I agree to the terms", checked = false, onCheckedChange = {}, Modifier.testTag("checkbox-case-terms"), required = true, description = if (long) LONG else null)
                        OuroCheckbox("Whole basket", checked = false, onCheckedChange = {}, Modifier.testTag("checkbox-case-mixed"), indeterminate = true)
                        OuroCheckbox("Make it vegetarian", checked = true, onCheckedChange = {}, Modifier.testTag("checkbox-checked"), description = "Swaps the stock for vegetable stock.")
                        OuroCheckbox("Pantry staples", checked = false, onCheckedChange = {}, Modifier.testTag("checkbox-error"), error = "Choose at least one.")
                        OuroCheckbox("Select all", checked = true, onCheckedChange = {}, Modifier.testTag("checkbox-hidden-label"), hideLabel = true)
                    }
                }
                if (only == null || only == "text-field") {
                    Column(Modifier.testTag("gallery-text-field"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroTextField(
                            if (long) LONG else "Email", value = "", onValueChange = {}, Modifier.testTag("text-field-case-email-with-description"),
                            type = OuroTextFieldType.Email, required = true, description = if (long) LONG else "We send your recipes here.",
                        )
                        OuroTextField("Password", value = "lemon-orzo", onValueChange = {}, Modifier.testTag("text-field-case-password"), type = OuroTextFieldType.Password)
                        OuroTextField("Recipe title", value = "", onValueChange = {}, Modifier.testTag("text-field-case-with-error"), error = "Give the recipe a title.", placeholder = "Lemon orzo")
                    }
                }
                if (only == null || only == "separator") {
                    Column(Modifier.testTag("gallery-separator"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroSeparator(Modifier.testTag("separator-line"))
                        OuroSeparator(Modifier.testTag("separator-case-or"), label = if (long) LONG else "or")
                        OuroSeparator(Modifier.testTag("separator-success"), tone = OuroTone.Success)
                    }
                }
                if (only == null || only == "description-list") {
                    OuroDescriptionList(
                        listOf(OuroDescriptionItem(if (long) LONG else "Prep time", if (long) LONG else "20 minutes"), OuroDescriptionItem("Serves", "6")),
                        Modifier.testTag("gallery-description-list"),
                    )
                }
            }
        }
    }
}

/** A tree from core's own cases, as an agent composed it: fields, a write button and a consequential one, then every button kind. */
@Composable
fun ComposedGallery(setting: Setting) {
    val density = LocalDensity.current
    val dir = java.io.File("../vendor/core/composed")
    val catalog = androidx.compose.runtime.remember { ComposedCatalog.parse(java.io.File(dir, "recipe.catalog.json").readText()) }
    val trees = androidx.compose.runtime.remember {
        listOf("inputs.json" to "plan", "action-buttons.json" to "buttons").map { (file, surface) ->
            val case = ComposedJson.parse(java.io.File(dir, "cases/$file").readText()) as Map<*, *>
            val tree = ComposedNode.fromJson(((case["expect"] as Map<*, *>)["surfaces"] as Map<*, *>)[surface])
            @Suppress("UNCHECKED_CAST")
            val data = (((case["messages"] as List<*>)[0] as Map<*, *>)["createSurface"] as Map<*, *>)["dataModel"] as? Map<String, Any?> ?: emptyMap()
            tree to data
        }
    }
    OuroTheme(setting.theme, dark = setting.dark, increasedContrast = setting.increasedContrast, reduceMotion = setting.reduceMotion) {
        CompositionLocalProvider(LocalDensity provides Density(density.density, setting.fontScale)) {
            Column(Modifier.testTag("composed-gallery").background(Ouro.look.colors.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for ((tree, data) in trees) OuroComposedSurface(tree, catalog, onPress = {}, onHandoff = { _, _ -> }, data = data)
            }
        }
    }
}
