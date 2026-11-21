<!-- ourostack:brand:start -->
<p align="center">
  <a href="https://github.com/ourostack"><img src="https://raw.githubusercontent.com/ourostack/.github/main/brand/headers/ouro-ui-android.webp" alt="Ouro resting his chin on a greenhouse seed tray where phones grow like seedlings, while a small robot lifts one out to check it" width="100%"></a>
</p>
<!-- ourostack:brand:end -->

# Ouro UI for Android

Ouro UI's native kit for Android: core's components in Jetpack Compose for people, and AppFunctions for agents, both from [ouro-ui-core](https://github.com/ourostack/ouro-ui-core)'s one `defineAction` declaration. The X-ray checks that what agents can call and what people can press agree.

The plan is core's [native-kits decision](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-native-kits.md) and its [Android record](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-android-kit.md). There is one Compose kit, component specs are vendored from core by hash, and only the rendering is written here.

## Agent cards

An agent can put a card on a person's screen: a recipe, a shopping list, a form with buttons. Android draws two formats with one surface, `OuroComposedSurface`, and both are limited to your catalog: every button is one of your declared actions, with its label, consent class and confirm sheet taken from the catalog, never from the agent.

| Format | Where it is in an MCP tool result | Draw it |
|---|---|---|
| Ouro's composed tree, the source of truth | `structuredContent.ouro` (core's `mcpAppTreeResult`), or `structuredContent.ouro.update` for a change to the card shown (`mcpAppTreeUpdateResult`) | `card.receive(toolResult)` |
| [A2UI](https://a2ui.org/) v1.0 messages (`createSurface`, `updateComponents`, `updateDataModel`, `deleteSurface`) | `structuredContent.a2ui.messages` (core's A2UI `mcpAppToolResult`) | `card.receive(toolResult)`, or `card.receiveA2UI(messages)` for the problems in A2UI's format |

One catalog serves both: generate it from your actions with core's `ouro-a2ui-catalog.mts catalog` (below). A2UI is an adapter onto Ouro's tree, as on web and Apple ([core's decision](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-composed-ui-own-tree.md)): `OuroA2UI` (`bot.ouro.ui.hx.composed`) is core's `a2ui.mts` in Kotlin. It validates the messages, resolves them into the composed tree and its data model, and the tree must then pass the same checks as one the agent sends itself.

```kotlin
val card = rememberOuroComposedState(catalog)
LaunchedEffect(toolResult) { card.receive(toolResult) }   // structuredContent.ouro or structuredContent.a2ui
OuroComposedSurface(
    card,
    onPress = { press -> runAction(press.action, press.input) },
    onHandoff = { action, how -> openScreenFor(action) },
    // A card that came as A2UI sends each press as A2UI's action message instead.
    onA2UIAction = { message -> val a = message["action"] as Map<*, *>; runAction(a["name"] as String, a["context"] as Map<String, Any?>) },
)
```

A tree or a message stream that fails the checks shows one short alert, "This card can't be shown", in place of the card. `card.receiveA2UI(messages)` returns A2UI's problems in A2UI's own error format (`VALIDATION_FAILED` and the rest, with a JSON pointer into the stream), word for word as core writes them, for the agent to correct. CI runs core's 13 A2UI conformance cases and the sample's card in both formats (What CI proves, below).

What's still missing:

- **One A2UI surface per card.** `receiveA2UI` shows the first surface the messages leave, or the one you name; a stream that makes several needs one `OuroComposedState` each.
- **The whole stream each time.** Pass every message so far, again as more arrive. While the surface is the one the same `createSurface` made, what the person entered stays.
- **No A2UI functions, themes or other catalogs.** As in core: `callRendererFunction` and `agentFunctionResponse` are refused (agents act through their own AppFunctions), a `theme` is refused (your Ouro theme styles the card), and `catalogId` must be your app's catalog, so A2UI's basic catalog isn't drawn.
- **Only `@accessibility.label` is read.** `description` and `hidden` are checked but not drawn, as in core's resolver; and the surface names buttons and fields by their catalog label and field label, so TalkBack doesn't read the `@label` yet either.
- **Limits core doesn't have yet.** An A2UI surface may go at most 24 components deep, resolve to at most 5,000 nodes, and copy at most a million characters out of its data (`OuroA2UI.MAX_DEPTH`, `MAX_NODES`, `MAX_SIZE`), as on Windows; core may adopt them ([ouro-ui-core#100](https://github.com/ourostack/ouro-ui-core/issues/100)). A stream names children by id, so nothing else stops a long chain from exhausting the stack, or shared children and templates from multiplying a large data model past what the app's memory holds.
- **Two edges differ from JavaScript.** A component id's letters are checked by Unicode category, so a rare letter the JVM and a browser's engine classify differently could pass on one and fail on the other. A data write past the end of a list writes nothing here; in JavaScript it leaves a gap.

## No Android devices here: collaborators welcome

The maintainer owns no Android phone or tablet. Everything in this repository is checked on Android emulators in GitHub's CI, never on a real device. If you have Android devices and want to help, open an issue or a pull request from a fork; you're very welcome. Collaborators join as outside collaborators on this public repository or through forks.

## What's here

| Part | What it is |
|---|---|
| `ouro-hx` (`bot.ouro.ui:ouro-hx`) | `OuroTheme` from core's tokens (six themes, light and dark, increased contrast, reduce motion), `OuroButton`, `OuroBadge`, `OuroCard` and `OuroItem` (Apple's first set), and `OuroCheckbox`, `OuroTextField`, `OuroSwitch`, `OuroSelect`, `OuroField`, `OuroSeparator` and `OuroDescriptionList` and `OuroTabs`, `OuroTabBar` and `OuroSidebar`, `OuroItemGroup`, `OuroSwipeActions`, `OuroReorderable`, and the overlays `OuroDialog`, `OuroSheet` and `OuroConfirmDialog` (platform windows drawn from the theme, not Material's `AlertDialog` or `ModalBottomSheet`, so the kit stays free of Material; each has a pane title, a heading and the dismiss action). `OuroComposedSurface` draws an agent's card, as Ouro's composed tree or as A2UI messages (`OuroA2UI`, core's adapter ported). |
| `ouro-actions` (`bot.ouro.ui:ouro-actions`) | The runtime behind every action. A person's press and an agent's AppFunction call take one path. A person-only action refuses agents with core's typed refusal, and a consequential one asks the person first with a built-in confirmation (`OuroConfirm`), which an app can replace with its own `confirm`. |
| `vendor/core/ouro-actions-android.mts` | Core's Android generator, vendored from its release by SHA-256: action declarations become `OuroActions.kt` (an AppFunction per action an agent may call, none for person-only) and `doors.json` (which door carries which action, and why not). |
| `vendor/core/composed` | Core's composed-tree catalogs and conformance cases. The catalog comes from the release's dist and the cases from the tag's commit, each pinned by SHA-256 in `core-provenance.json`. `ouro-hx`'s ports of core's `composed.mts` and `a2ui.mts` (`bot.ouro.ui.hx.composed`) are tested against every case they hold. |
| `samples/shop` | A shopping list that declares its actions once and opens both doors. Its `composed` screen draws a card an agent composed (`agent-card.mts`) from the app's catalog, and its `a2ui` screen the same card arriving as A2UI messages. CI runs its X-ray and both cards on an emulator. |

## Use it

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven { url = uri("https://ourostack.github.io/ouro-ui-android/maven") }
    }
}
// build.gradle.kts
dependencies { implementation("bot.ouro.ui:ouro-hx:0.9.0") }
```

From v0.5.0 on, [the release workflow](.github/workflows/release.yml) gives each file it attaches a GitHub build attestation, so you can check that a library was built from its tag: `gh attestation verify ouro-hx-0.9.0.aar --repo ourostack/ouro-ui-android`.

```kotlin
OuroTheme(OuroThemeName.Pearl, dark = isSystemInDarkTheme()) {
    OuroButton("Add to list", onPress = { /* ... */ }, tone = OuroTone.Primary)
}
```

AppFunctions need compileSdk 37 and AGP 9.1 or later. Google lists Android 16 (API 36) and later. On CI's API 36 emulator image Android indexed none of the sample's AppFunctions, so CI checks them on Android 17 (API 37). To give agents your actions, declare them once as `shopping.actions.mts` does, then run core's generator into your app's sources. Take `ouro-actions.mts` and `ouro-actions-android.mts` from a [core release](https://github.com/ourostack/ouro-ui-core/releases) into one folder and run `node ouro-actions-android.mts generate <actions.mts> <dir>` (Node 22.18 or later). Declare the generated `<Set>AppFunctionService` in your manifest, as `samples/shop/src/main/AndroidManifest.xml` does.

## Ask the person before a consequential call

A consequential action (one that reaches other people, such as sharing a list) never runs from an agent's AppFunction call until the person says yes. Windows asks with a message box. Android has no system prompt to ask for an app, so `ouro-actions` asks itself, with platform views only (no Compose needed). Call `OuroConfirm.install` once from `Application.onCreate`, after you set your handlers:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MyActions.bind()                            // each action's perform
        OuroConfirm.install(this, OuroActions.set)  // the built-in confirmation, for each consequential action without a confirm
    }
}
```

What the person sees is the composed card's confirm sheet in platform views: the action's label as a question ("Share list?"), its description, and each prepared value under its label, then Don't and Allow. Don't is the default button. Here is how the call behaves:

- **App on screen:** the confirmation opens at once.
- **App in the background**, an AppFunction call's usual case: a high-priority notification titled with the question. Tapping it opens the confirmation. The notification has no Allow and shows none of the values, so the person reads them before they answer.
- **No notification permission, app in the background:** nothing can ask, so the call is refused with `OuroNeedsPerson`, whose message says why and how to fix it. On Android 13 and later your app asks the person for `POST_NOTIFICATIONS` (the library's manifest declares it): `requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)`, as the sample's agent console does. A notification channel the person turned off counts the same.
- **Waiting:** the call suspends until the person answers or two minutes pass (`OuroConfirm.DEFAULT_TIMEOUT_MS`; pass `timeoutMs` to change it). Don't, Back, tapping outside, leaving the screen and the timeout all mean no, and no runs nothing. The agent gets AppFunctions' denied error with the reason (`The person said no to Share list.`, or that no one answered in time).
- **One answer, for one call:** each pending confirmation has a fresh 128-bit random id. The confirmation activity isn't exported, the notification's `PendingIntent` is immutable and single-use, and the screen reads what to show from the pending confirmation under that id, never from the intent. A stale or forged intent shows nothing and answers nothing.

The question under the title is the action's description, or the `confirm` question its declaration gives, which core's `doors.json` lists under each action's `text`; pass it as `confirmTexts = mapOf("share_shopping_list" to "…")` so the person reads the same words the other doors show.

The values come from the call's input: each field that isn't null, its name as words (`shareWith` is "Share with") and its value as text, without `idempotencyKey`. To use your own labels, or to keep them when R8 renames fields, pass `describe`:

```kotlin
OuroConfirm.install(this, OuroActions.set) { action, input ->
    listOf(OuroConfirmRow("With", (input as ShareShoppingListInput).with))
}
```

To ask another way, set `confirm` on the action yourself: `install` leaves any action that already has one alone. `OuroConfirm.install(this, set, timeoutMs = 30_000)` sets the wait. An action with no `confirm` at all is refused, as before, so nothing consequential runs that no person agreed to.

## Draw what an agent composed

Android draws Ouro's composed tree: the UI an agent composes for a person, checked against your app's catalog, where every button is one of your declared actions with its consent class. It is the same tree web's `ComposedSurface` and Apple's `ComposedSurface` draw. An MCP tool result carries it as `structuredContent.ouro` (core's `mcpAppTreeResult`).

```kotlin
val catalog = ComposedCatalog.parse(assets.open("shopping.catalog.json").reader().readText())
val card = rememberOuroComposedState(catalog)   // or an OuroComposedState in your ViewModel, to outlive a rotation
LaunchedEffect(toolResultJson) { card.receive(toolResultJson) }   // a tree, or an update to the one shown
OuroComposedSurface(
    card,
    onPress = { press -> runAction(press.action, press.input) },   // read, write, and consequential once confirmed
    onHandoff = { action, how -> openScreenFor(action) },          // person-only: your own screen, never a press
)
```

An agent can change the card it sent without resending it: a tool result's `structuredContent.ouro.update` (core's `mcpAppTreeUpdateResult`, core 0.19.0 on) replaces nodes by id and sets data by JSON Pointer. `card.receive(result)` applies it, or `card.update(update)` applies one you got another way, with core's `applyTreeUpdate`: all of it or nothing. An id the tree lacks or a template's instances share, an unsafe path, or a tree that would fail the checks (a relabeled button, loosened consent) is refused. What the person entered stays across an update, except where the agent changed the value or the list item it sits in, as on web. A result that carries a tree, even a refused one, takes the card's place, so an update never changes a card the agent has since replaced. `rememberOuroComposedState` lasts as long as the composition: keep the state in a ViewModel, or replay the last whole tree and then each update, so a recreated Activity doesn't get an update for a card it no longer shows. If you hold the tree yourself, pass each new `tree` and `data` to the other overload, `OuroComposedSurface(tree, catalog, ...)`, which keeps the person's answers the same way.

Write the catalog from your declarations with core's generator: take `ouro-a2ui-catalog.mts`, `ouro-a2ui.mts`, `ouro-composed.mts`, `ouro-mcp-app.mts` and `ouro-actions.mts` from a core release into one folder, then run `node ouro-a2ui-catalog.mts catalog <actions.mts> <catalog.json>`. `samples/shop` does exactly this, and CI checks that the catalog still matches.

What the surface does:

- It checks the tree with core's validator (`bot.ouro.ui.hx.composed`, core's `composed.mts` ported) and draws none of a tree or update it refuses, so a sender can't relabel a button or loosen its consent. It shows a short alert instead, "This card can't be shown", as web does, and `onRefused` gets the problems.
- Read and write buttons press at once. A consequential one opens a confirm sheet that shows each prepared value under its label, with the id small beside a name. It presses only on confirm. A tree whose sheet would show the person anything other than what is sent is refused, as core 0.18.1's validator says ([ouro-ui-core#88](https://github.com/ourostack/ouro-ui-core/issues/88)), and the surface builds the rows from your catalog and the input anyway. Your `label` resolver names only id fields (those with `shownAs`); every other row shows exactly the value sent.
- A person-only button calls `onHandoff` and never presses.
- A field's `value` or a bound input whose pointer names `__proto__`, `constructor` or `prototype` is refused, and such a pointer writes nothing, as in core 0.18.2 ([ouro-ui-core#91](https://github.com/ourostack/ouro-ui-core/issues/91)). Names a sender chooses (component, action, input field) are the catalog's only when the catalog itself has them.
- Fields (TextField, Checkbox, Select) write the person's answers into the data model, and a button bound to a field sends what they entered.
- The surface doesn't let an agent press anything. Its buttons are the person's; every `PersonPress` is `by = "person"`. An agent calls the action through its own AppFunction.
- Tabs, TabBar, Sidebar, ItemGroup, SwipeActions, Reorderable, Dialog, Sheet, ConfirmDialog, Checkbox, Switch, Select, Field, TextField, Separator, DescriptionList, Badge, Card and Button are Ouro HX's components, checked against core's claims. Core has no spec yet for Heading, Text, Image, Stack, List or Alert, and the surface's Select is its own radio list, so the surface draws those from the theme's tokens. Headings are marked as headings, images are described, lists say how many items they hold, and controls are 48 dp. Select draws one radio button per option.

Limits:

- A2UI has its own limits, in Agent cards above. `OuroComposed.read` reads only Ouro's tree; `OuroComposedState.receive` takes either.
- Android refuses a tree nested more than 100 components deep, and JSON nested more than 512 levels, so a forged one can't exhaust the stack. Core sets no such limit; no screen comes near it.
- The default image loader fetches only https URLs, and your app needs the INTERNET permission. Pass `image` to use your own loader.
- Like everything here, it is checked on the CI emulator only, never on a device.

## What CI proves

- **build:** `ouro-actions`' runtime tests, including the built-in confirmation (what is asked, the wait, the timeout, no permission, the ids; the notification and the screen in Robolectric); the A2UI adapter against core's 13 A2UI conformance cases, each giving exactly its expected trees or problems; and core's claims for every component in Robolectric. The claims are names and roles in the semantics tree, 48 dp touch targets, reduced motion, text that reflows at 200%, and contrast in all six themes with pixels sampled. It also checks the snapshots in light, dark, increased contrast and 200% text.
- **emulator:** the sample on an Android 17 emulator, Google's automated-test image (ATD). The X-ray compares what Android indexed against the declarations. The composed card's buttons are checked as the declared actions: add runs the same handler, share runs only after the person confirms in the card's own sheet, and place order hands off without ordering. An agent's share through its AppFunction asks the person too: Allow runs it, and Don't and Back refuse it, with the app on screen; with the app in the background a notification asks, carries no Allow and none of the values, and opens the same confirmation. The same card arriving as A2UI messages is checked the same way, and its add sends A2UI's action message. Then CI photographs each screen.
- **generated:** the vendored core files match their release by SHA-256 (the dist's manifest, or the tag's commit for files the dist doesn't carry), the sample's generated Kotlin and composed catalog match its declarations, and its agent card, in both formats, passes core's validators.

## License

MIT
