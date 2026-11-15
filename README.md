# Ouro UI for Android

Ouro UI's native kit for Android: core's components in Jetpack Compose for people, and AppFunctions for agents, both from [ouro-ui-core](https://github.com/ourostack/ouro-ui-core)'s one `defineAction` declaration. The X-ray checks that what agents can call and what people can press agree.

The plan is core's [native-kits decision](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-native-kits.md) and its [Android record](https://github.com/ourostack/ouro-ui-core/blob/main/docs/decisions/2026-11-14-android-kit.md). There is one Compose kit, component specs are vendored from core by hash, and only the rendering is written here.

## No Android devices here: collaborators welcome

The maintainer owns no Android phone or tablet. Everything in this repository is checked on Android emulators in GitHub's CI, never on a real device. If you have Android devices and want to help, open an issue or a pull request from a fork; you're very welcome. Collaborators join as outside collaborators on this public repository or through forks.

## What's here

| Part | What it is |
|---|---|
| `ouro-hx` (`bot.ouro.ui:ouro-hx`) | `OuroTheme` from core's tokens (six themes, light and dark, increased contrast, reduce motion), and `OuroButton`, `OuroBadge`, `OuroCard` and `OuroItem`, Apple's first set. |
| `ouro-actions` (`bot.ouro.ui:ouro-actions`) | The runtime behind every action. A person's press and an agent's AppFunction call take one path. A person-only action refuses agents with core's typed refusal, and a consequential one is refused until the person confirms. |
| `vendor/core/ouro-actions-android.mts` | Core's Android generator, vendored from its release by SHA-256: action declarations become `OuroActions.kt` (an AppFunction per action an agent may call, none for person-only) and `doors.json` (which door carries which action, and why not). |
| `samples/shop` | A shopping list that declares its actions once and opens both doors. CI runs its X-ray on an emulator. |

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

## What CI proves

- **build:** `ouro-actions`' runtime tests, and core's claims for every component in Robolectric. The claims are names and roles in the semantics tree, 48 dp touch targets, reduced motion, text that reflows at 200%, and contrast in all six themes with pixels sampled. It also checks the snapshots in light, dark, increased contrast and 200% text.
- **emulator:** the sample on an Android 17 emulator, Google's automated-test image (ATD). The X-ray compares what Android indexed against the declarations, then CI photographs each screen.
- **generated:** the vendored core files match their release by SHA-256, and the sample's generated Kotlin matches its declarations.

## License

MIT
