<!-- ourostack:brand:start -->
<p align="center">
  <a href="https://github.com/ourostack"><img src="https://raw.githubusercontent.com/ourostack/.github/main/brand/headers/ouro-ui-android.webp" alt="Ouro resting his chin on a greenhouse seed tray where phones grow like seedlings, while a small robot lifts one out to check it" width="100%"></a>
</p>
<!-- ourostack:brand:end -->

# Ouro UI for Android

Ouro UI's native kit for Android: core's components in Jetpack Compose for people, and AppFunctions for agents, both from [ouro-ui-core](https://github.com/ourostack/ouro-ui-core)'s one `defineAction` declaration. The X-ray checks that what agents can call and what people can press agree.

The plan is core's [native-kits decision](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-native-kits.md) and its [Android record](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-android-kit.md). There is one Compose kit, component specs are vendored from core by hash, and only the rendering is written here.

## No Android devices here: collaborators welcome

The maintainer owns no Android phone or tablet. Everything in this repository is checked on Android emulators in GitHub's CI, never on a real device. If you have Android devices and want to help, open an issue or a pull request from a fork; you're very welcome. Collaborators join as outside collaborators on this public repository or through forks.

## What's here

| Part | What it is |
|---|---|
| `ouro-hx` (`bot.ouro.ui:ouro-hx`) | `OuroTheme` from core's tokens (six themes, light and dark, increased contrast, reduce motion), `OuroButton`, `OuroBadge`, `OuroCard` and `OuroItem` (Apple's first set), and `OuroCheckbox`, `OuroTextField`, `OuroSeparator` and `OuroDescriptionList`. `OuroComposedSurface` draws Ouro's composed tree (below). |
| `ouro-actions` (`bot.ouro.ui:ouro-actions`) | The runtime behind every action. A person's press and an agent's AppFunction call take one path. A person-only action refuses agents with core's typed refusal, and a consequential one is refused until the person confirms. |
| `vendor/core/ouro-actions-android.mts` | Core's Android generator, vendored from its release by SHA-256: action declarations become `OuroActions.kt` (an AppFunction per action an agent may call, none for person-only) and `doors.json` (which door carries which action, and why not). |
| `vendor/core/composed` | Core's composed-tree catalogs and conformance cases. The catalog comes from the release's dist and the cases from the tag's commit, each pinned by SHA-256 in `core-provenance.json`. `ouro-hx`'s port of core's `composed.mts` (`bot.ouro.ui.hx.composed`) is tested against every tree they hold. |
| `samples/shop` | A shopping list that declares its actions once and opens both doors. Its `composed` screen draws a card an agent composed (`agent-card.mts`) from the app's catalog. CI runs its X-ray and the card on an emulator. |

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
dependencies { implementation("bot.ouro.ui:ouro-hx:0.1.0") }
```

```kotlin
OuroTheme(OuroThemeName.Pearl, dark = isSystemInDarkTheme()) {
    OuroButton("Add to list", onPress = { /* ... */ }, tone = OuroTone.Primary)
}
```

AppFunctions need compileSdk 37 and AGP 9.1 or later. Google lists Android 16 (API 36) and later. On CI's API 36 emulator image Android indexed none of the sample's AppFunctions, so CI checks them on Android 17 (API 37). To give agents your actions, declare them once as `shopping.actions.mts` does, then run core's generator into your app's sources. Take `ouro-actions.mts` and `ouro-actions-android.mts` from a [core release](https://github.com/ourostack/ouro-ui-core/releases) into one folder and run `node ouro-actions-android.mts generate <actions.mts> <dir>` (Node 22.18 or later). Declare the generated `<Set>AppFunctionService` in your manifest, as `samples/shop/src/main/AndroidManifest.xml` does.

## Draw what an agent composed

Android draws Ouro's composed tree: the UI an agent composes for a person, checked against your app's catalog, where every button is one of your declared actions with its consent class. It is the same tree web's `ComposedSurface` and Apple's `ComposedSurface` draw. An MCP tool result carries it as `structuredContent.ouro` (core's `mcpAppTreeResult`).

```kotlin
val catalog = ComposedCatalog.parse(assets.open("shopping.catalog.json").reader().readText())
when (val read = OuroComposed.read(catalog, toolResultJson)) {
    is OuroComposed.Read.Ready -> OuroComposedSurface(
        read.tree, catalog, data = read.data,
        onPress = { press -> runAction(press.action, press.input) },   // read, write, and consequential once confirmed
        onHandoff = { action, how -> openScreenFor(action) },          // person-only: your own screen, never a press
    )
    is OuroComposed.Read.Refused -> showProblems(read.problems)
}
```

Write the catalog from your declarations with core's generator: take `ouro-a2ui-catalog.mts`, `ouro-a2ui.mts`, `ouro-composed.mts`, `ouro-mcp-app.mts` and `ouro-actions.mts` from a core release into one folder, then run `node ouro-a2ui-catalog.mts catalog <actions.mts> <catalog.json>`. `samples/shop` does exactly this, and CI checks that the catalog still matches.

What the surface does:

- It checks the tree with core's validator (`bot.ouro.ui.hx.composed`, core's `composed.mts` ported) and draws nothing it refuses, so a sender can't relabel a button or loosen its consent.
- Read and write buttons press at once. A consequential one opens a confirm sheet that shows each prepared value under its label, with the id small beside a name. It presses only on confirm. The sheet's rows are built from your catalog and the input, never read from the tree ([ouro-ui-core#88](https://github.com/ourostack/ouro-ui-core/issues/88)).
- A person-only button calls `onHandoff` and never presses.
- Fields (TextField, Checkbox, Select) write the person's answers into the data model, and a button bound to a field sends what they entered.
- The surface doesn't let an agent press anything. Its buttons are the person's; every `PersonPress` is `by = "person"`. An agent calls the action through its own AppFunction.
- Checkbox, TextField, Separator, DescriptionList, Badge, Card and Button are Ouro HX's components, checked against core's claims. Core has no spec yet for Heading, Text, Image, Stack, List, Alert or Select, so the surface draws those from the theme's tokens. Headings are marked as headings, images are described, lists say how many items they hold, and controls are 48 dp. Select draws one radio button per option.

Limits:

- Android reads no A2UI messages: send Ouro's tree (`structuredContent.ouro`), not an A2UI stream (`structuredContent.a2ui`). `OuroComposed.read` refuses an A2UI result and says so.
- The default image loader fetches only https URLs, and your app needs the INTERNET permission. Pass `image` to use your own loader.
- Like everything here, it is checked on the CI emulator only, never on a device.

## What CI proves

- **build:** `ouro-actions`' runtime tests, and core's claims for every component in Robolectric. The claims are names and roles in the semantics tree, 48 dp touch targets, reduced motion, text that reflows at 200%, and contrast in all six themes with pixels sampled. It also checks the snapshots in light, dark, increased contrast and 200% text.
- **emulator:** the sample on an Android 17 emulator, Google's automated-test image (ATD). The X-ray compares what Android indexed against the declarations. The composed card's buttons are checked as the declared actions: add runs the same handler, share runs only after the person confirms, and place order hands off without ordering. Then CI photographs each screen.
- **generated:** the vendored core files match their release by SHA-256 (the dist's manifest, or the tag's commit for files the dist doesn't carry), the sample's generated Kotlin and composed catalog match its declarations, and its agent card passes core's validator.

## License

MIT
