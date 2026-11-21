package bot.ouro.ui.hx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun Gallery(setting: Setting, only: String? = null, long: Boolean = false, reopen: Int = 0, overlays: Boolean = true) {
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
                        OuroTextField("Search recipes", value = "lemon", onValueChange = {}, Modifier.testTag("text-field-clearable"), type = OuroTextFieldType.Search, clearable = true, variant = OuroTextFieldVariant.Plain)
                        OuroTextField("Serves", value = "4", onValueChange = {}, Modifier.testTag("text-field-large"), size = OuroTextFieldSize.Lg)
                    }
                }
                if (only == null || only == "select") {
                    Column(Modifier.testTag("gallery-select"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroSelect(
                            "Course", value = null, onValueChange = {}, options = COURSES, modifier = Modifier.testTag("select-case-course"),
                            placeholder = "Choose a course", required = true, description = if (long) LONG else null,
                        )
                        OuroSelect("Course", value = "main", onValueChange = {}, options = COURSES, modifier = Modifier.testTag("select-case-chosen-with-error"), error = if (long) LONG else "Main is full.")
                        OuroSelect("Course", value = "starter", onValueChange = {}, options = COURSES, modifier = Modifier.testTag("select-hidden-label"), hideLabel = true, description = "Starters come first.")
                    }
                }
                if (only == null || only == "switch") {
                    Column(Modifier.testTag("gallery-switch"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroSwitch(if (long) LONG else "Wi-Fi", checked = true, onCheckedChange = {}, Modifier.testTag("switch-case-wifi"))
                        OuroSwitch(
                            if (long) LONG else "Nightly backups", checked = false, onCheckedChange = {}, Modifier.testTag("switch-case-backups"),
                            required = true, description = if (long) LONG else "At 2 a.m.",
                        )
                        OuroSwitch("Notifications", checked = true, onCheckedChange = {}, Modifier.testTag("switch-error"), error = "Turn this on to continue.")
                    }
                }
                if (only == null || only == "field") {
                    Column(Modifier.testTag("gallery-field"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroField(
                            if (long) LONG else "Email", Modifier.testTag("field-case-email"), required = true,
                            description = if (long) LONG else "We never share it.",
                        ) { m, on -> FieldTextBox(m, on) }
                        OuroField("Email", Modifier.testTag("field-case-with-error"), error = if (long) LONG else "Enter an email address.") { m, on -> FieldTextBox(m, on) }
                    }
                }
                if (only == null || only == "separator") {
                    Column(Modifier.testTag("gallery-separator"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OuroSeparator(Modifier.testTag("separator-line"))
                        OuroSeparator(Modifier.testTag("separator-case-or"), label = if (long) LONG else "or")
                        OuroSeparator(Modifier.testTag("separator-success"), tone = OuroTone.Success)
                    }
                }
                if (only == "dialog" || (only == null && overlays)) {
                    Column(Modifier.testTag("gallery-dialog"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        var edit by remember(reopen) { mutableStateOf(true) }
                        if (edit) OuroDialogPanel(
                            if (long) LONG else "Edit recipe", false, if (long) LONG else "Change the title and the notes.", "Close", { edit = false }, Modifier.testTag("dialog-case-edit-recipe"),
                            footer = { OuroButton("Save", onPress = {}) },
                            content = { OuroTextField("Title", value = "Lemon orzo", onValueChange = {}) },
                        )
                        var filters by remember(reopen) { mutableStateOf(true) }
                        if (filters) OuroDialogPanel("Filters", true, null, "Close", { filters = false }, Modifier.testTag("dialog-case-hidden-title"), content = { GalleryText("Show only vegetarian recipes.") })
                    }
                }
                if (only == "sheet" || (only == null && overlays)) {
                    Column(Modifier.testTag("gallery-sheet"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        var save by remember(reopen) { mutableStateOf(true) }
                        if (save) OuroSheetPanel(
                            if (long) LONG else "Save to cookbook", false, if (long) LONG else "Pick a cookbook for this recipe.", "Close", { save = false }, OuroSide.Bottom, Modifier.testTag("sheet-case-save-to-cookbook"),
                            footer = { OuroButton("Save", onPress = {}) },
                        )
                        var beside by remember(reopen) { mutableStateOf(true) }
                        if (beside) OuroSheetPanel("Notes", false, null, "Close", { beside = false }, OuroSide.Right, Modifier.testTag("sheet-case-beside-page"), content = { GalleryText("Keep these next to the recipe.") })
                    }
                }
                if (only == "confirm-dialog" || (only == null && overlays)) {
                    Column(Modifier.testTag("gallery-confirm-dialog"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroConfirmPanel(if (long) LONG else "Delete recipe?", if (long) LONG else "This can't be undone.", "Delete", "Cancel", OuroTone.Destructive, false, {}, {}, Modifier.testTag("confirm-dialog-case-delete-recipe"))
                        OuroConfirmPanel("Share shopping list", "Share your list with this person? They will be able to see it and change it.", "Share", "Cancel", OuroTone.Primary, false, {}, {}, Modifier.testTag("confirm-dialog-case-consequential-action"))
                    }
                }
                if (only == null || only == "tabs") {
                    Column(Modifier.testTag("gallery-tabs"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroTabs(
                            if (long) LONG else "Recipe", value = "ingredients", onValueChange = {},
                            tabs = listOf(OuroTab("ingredients", if (long) LONG else "Ingredients"), OuroTab("method", "Method")), Modifier.testTag("tabs-case-recipe-sections"),
                            panel = { BasicText("The ingredient list", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) },
                        )
                        OuroTabs(
                            "Settings", value = "profile", onValueChange = {},
                            tabs = listOf(OuroTab("profile", "Profile"), OuroTab("billing", if (long) LONG else "Billing", disabled = true)), Modifier.testTag("tabs-case-vertical-with-disabled"),
                            orientation = OuroOrientation.Vertical,
                        )
                    }
                }
                if (only == null || only == "sidebar") {
                    val icon: @Composable () -> Unit = { Box(Modifier.size(24.dp).background(LocalOuroContentColor.current, androidx.compose.foundation.shape.CircleShape)) }
                    Column(Modifier.testTag("gallery-sidebar"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroSidebar(
                            "Main", current = "Recipes", onPress = {}, modifier = Modifier.testTag("sidebar-case-sections-and-badge"),
                            sections = listOf(
                                OuroSidebarSection(null, listOf(OuroSidebarItem(if (long) LONG else "Recipes", icon), OuroSidebarItem("Inbox", icon, badge = "12"))),
                                OuroSidebarSection("Library", listOf(OuroSidebarItem("Cookbooks", icon))),
                            ),
                            header = { BasicText("Spoonjoy", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) },
                        )
                        OuroSidebar(
                            "Main", current = "Recipes", onPress = {}, modifier = Modifier.testTag("sidebar-case-collapsed-rail"), collapsed = true,
                            sections = listOf(OuroSidebarSection(null, listOf(OuroSidebarItem("Recipes", icon), OuroSidebarItem("Cookbooks", icon)))),
                        )
                    }
                }
                if (only == null || only == "item-group") {
                    Column(Modifier.testTag("gallery-item-group"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroItemGroup("Recipes", Modifier.testTag("item-group-case-divided-three-rows")) {
                            item { BasicText(if (long) LONG else "Braised short ribs", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) }
                            item { BasicText("Lemon orzo", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) }
                            item { BasicText("Miso soup", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) }
                        }
                        OuroItemGroup("Recipes", Modifier.testTag("item-group-case-outline"), variant = OuroGroupVariant.Outline, ordered = true) {
                            item { BasicText(if (long) LONG else "Lemon orzo", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) }
                            item { BasicText("Miso soup", style = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)) }
                        }
                        OuroItemGroup("Recipes", Modifier.testTag("item-group-case-empty-message"), empty = if (long) LONG else "No recipes yet") {}
                    }
                }
                if (only == null || only == "swipe-actions") {
                    val text = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)
                    Column(Modifier.testTag("gallery-swipe-actions"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroSwipeActions(
                            "Lunch plans", Modifier.testTag("swipe-actions-case-trailing-delete"),
                            trailing = listOf(OuroSwipeAction("Delete") {}, OuroSwipeAction("Archive") {}), tone = OuroTone.Destructive,
                        ) { BasicText(if (long) LONG else "Lunch plans", style = text) }
                        OuroSwipeActions(
                            "Lunch plans", Modifier.testTag("swipe-actions-case-named-actions-button"),
                            leading = listOf(OuroSwipeAction("Pin") {}), actionsLabel = "More for lunch",
                        ) { BasicText("Lunch plans", style = text) }
                    }
                }
                if (only == null || only == "conversation-list") {
                    Column(Modifier.testTag("gallery-conversation-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroConversationList(
                            "Conversations",
                            listOf(
                                OuroConversation("a", "Ari", "9:41", if (long) LONG else "Lunch at noon?", unread = true),
                                OuroConversation("b", "Sam", "Mon", "Notes from Monday"),
                            ),
                            Modifier.testTag("conversation-list-case-unread-and-selected"),
                            selectedId = "b",
                        )
                        OuroConversationList("Conversations", emptyList(), Modifier.testTag("conversation-list-case-empty-list"), empty = if (long) LONG else "No conversations yet")
                    }
                }
                // Chat kinds draw only on request: the all-in-one gallery is already as tall as the snapshot window.
                if (only == "message") {
                    Column(Modifier.testTag("gallery-message"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroMessage("Ana", if (long) LONG else "See you at noon.", Modifier.testTag("message-case-received"), time = "9:41")
                        OuroMessage("You", if (long) LONG else "On my way.", Modifier.testTag("message-case-own-failed"), role = OuroMessageRole.User, time = "9:42", failed = true)
                        OuroMessage("Ouro", if (long) LONG else "Ana joined the conversation.", Modifier.testTag("message-case-system"), role = OuroMessageRole.System)
                    }
                }
                if (only == "composer") {
                    Column(Modifier.testTag("gallery-composer"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroComposer(
                            "Message Mia", "", {}, {}, Modifier.testTag("composer-case-empty"),
                            placeholder = if (long) LONG else "Write a message",
                        )
                        OuroComposer(
                            "Reply in thread", if (long) LONG else "", {}, {}, Modifier.testTag("composer-case-sending-off"),
                            sendDisabledReason = if (long) LONG else "You're offline. Sending resumes when you reconnect.",
                        )
                        OuroComposer("Message Mia", if (long) LONG else "On my way.", {}, {}, Modifier.testTag("composer-case-sending"), sending = true)
                    }
                }
                if (only == "message-list") {
                    Column(Modifier.testTag("gallery-message-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroMessageList(
                            "Conversation with Ana",
                            listOf(
                                OuroChatMessage("a", "Ana", "9:41", if (long) LONG else "See you at noon."),
                                OuroChatMessage("b", "You", "9:42", if (long) LONG else "On my way.", mine = true),
                            ),
                            Modifier.testTag("message-list-case-two-messages"),
                        )
                        OuroMessageList(
                            "Team chat",
                            listOf(OuroChatMessage("a", "Ana", "9:41", if (long) LONG else "Morning.")),
                            Modifier.testTag("message-list-case-older-available"),
                            hasOlder = true,
                        )
                        OuroMessageList(
                            "Live chat",
                            // Only the newest three stay, so the list never outgrows its view however many arrivals a run makes.
                            (listOf(OuroChatMessage("h1", "Ana", "9:00", "Hello."), OuroChatMessage("h2", "You", "9:01", "Hi.", mine = true)) +
                                (1..reopen).map { k -> OuroChatMessage("n$k", if (k % 2 == 1) "Ana" else "You", "9:0${(2 + k) % 10}", if (k % 2 == 1) "Ping $k" else "Pong $k", mine = k % 2 == 0) }).takeLast(3),
                            Modifier.testTag("message-list-case-arrivals"),
                        )
                    }
                }
                if (only == "reaction-bar") {
                    Column(Modifier.testTag("gallery-reaction-bar"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val like = OuroReaction("like", "👍", "Like", 3, mine = true)
                        val love = OuroReaction("heart", "❤️", "Love", 1)
                        OuroReactionBar(
                            if (long) listOf(like, love, OuroReaction("laugh", "😂", "Laugh", 12), OuroReaction("wow", "😮", "Wow", 4)) else listOf(like, love),
                            onReact = {}, modifier = Modifier.testTag("reaction-bar-case-two-reactions"),
                        )
                        OuroReactionBar(listOf(OuroReaction("like", "👍", "Like", 2)), onReact = {}, modifier = Modifier.testTag("reaction-bar-case-with-add"), showMore = true)
                        OuroReactionBar(listOf(OuroReaction("like", "👍", "Like", 2)), onReact = {}, modifier = Modifier.testTag("reaction-bar-case-disabled"), disabled = true)
                    }
                }
                if (only == null || only == "reorderable") {
                    val text = TextStyle(color = LocalOuroContentColor.current, fontSize = 16.sp)
                    var order by remember { mutableStateOf(listOf("Intro", "Verse", "Chorus")) }
                    val rows = order.mapIndexed { i, n -> OuroReorderRow(n, n) { BasicText(if (long && i == 0) LONG else n, style = text) } }
                    Column(Modifier.testTag("gallery-reorderable"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroReorderable("Playlist", rows, Modifier.testTag("reorderable-case-three-rows"), onReorder = { ids, _ -> order = ids })
                        OuroReorderable(
                            "Playlist", listOf("Intro", "Verse", "Chorus").map { n -> OuroReorderRow(n, n) { BasicText(n, style = text) } },
                            Modifier.testTag("reorderable-case-locked"), disabledReason = "Sorted by date",
                        )
                    }
                }
                if (only == null || only == "tab-bar") {
                    val icon: @Composable () -> Unit = { Box(Modifier.size(24.dp).background(LocalOuroContentColor.current, androidx.compose.foundation.shape.CircleShape)) }
                    Column(Modifier.testTag("gallery-tab-bar"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OuroTabBar(
                            "Main", current = "Recipes", onPress = {}, modifier = Modifier.testTag("tab-bar-case-main-with-search"),
                            items = listOf(OuroBarItem("Home", icon), OuroBarItem(if (long) LONG else "Recipes", icon), OuroBarItem("Shopping", icon)),
                            accessory = OuroBarItem("Search", icon),
                        )
                        OuroTabBar("Main", current = "Home", onPress = {}, modifier = Modifier.testTag("tab-bar-case-two-tabs"), items = listOf(OuroBarItem("Home", icon), OuroBarItem("Settings", icon)))
                    }
                }
                if (only == "table") {
                    val cols = listOf(if (long) LONG else "Recipe", "Time", "Serves")
                    val short = listOf("Recipe", "Time", "Serves")
                    val data = listOf(listOf("Braised short ribs", "4 h", "6"), listOf("Herb salad", "10 min", "2"), listOf("Rye bread", "18 h", "8"))
                    Column(Modifier.testTag("gallery-table"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // The empty cases come first: at 200% the long cases outgrow the test window, and a text below its bottom edge is never placed.
                        OuroTable("Recipes", cols, emptyList(), Modifier.testTag("table-case-empty"))
                        OuroTable("Recipes", cols, emptyList(), Modifier.testTag("table-case-empty-custom"), empty = if (long) LONG else "No recipes match.")
                        OuroTable("Recipes", cols, data, Modifier.testTag("table-case-basic"), striped = true)
                        OuroTable("Recipes", short, data, Modifier.testTag("table-case-hidden-caption"), hideCaption = true, dense = true)
                        OuroTable("Recipes", short, data, Modifier.testTag("table-case-sortable"), sortable = listOf("Recipe", "Serves"))
                        OuroTable("Recipes", short, data.reversed(), Modifier.testTag("table-case-sorted"), sortable = listOf("Recipe", "Serves"), sortedBy = "Serves", sortDirection = OuroSortDirection.Descending)
                        OuroTable("Recipes", short, data, Modifier.testTag("table-case-row-links"), onRowPress = {})
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

@Composable
private fun GalleryText(text: String) = BasicText(text, style = TextStyle(color = LocalOuroContentColor.current, fontSize = 14.sp))

internal val COURSES = listOf(OuroSelectOption("starter", "Starter"), OuroSelectOption("main", "Main"))

/** The one control a field names in its cases: core's "A text box", drawn with a plain edge. */
@Composable
internal fun FieldTextBox(modifier: Modifier, enabled: Boolean) {
    val look = Ouro.look
    val edge = OuroColor.push(look.colors.input, Ouro.surface, OuroColor.INCREASED_LINE_CONTRAST)
    val color = OuroColor.readable(look.colors.foreground, Ouro.surface, look.textContrast)
    androidx.compose.foundation.text.BasicTextField(
        value = "", onValueChange = {}, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = OuroMinTarget).border(1.dp, edge, RoundedCornerShape(look.radius)).padding(12.dp),
        textStyle = TextStyle(color = color, fontSize = 16.sp),
    )
}
