# Releasing

Releases are tags `vX.Y.Z` on `main`, published as GitHub releases by `.github/workflows/release.yml`.

1. Merge everything for the release through pull requests, and wait for `main`'s CI to pass, `emulator` included.
   That job is the proof on Android: the X-ray on an Android 17 emulator (what AppFunctions Android indexed, the
   buttons on screen, an agent's call through the same handler a person presses, the consequential action's
   confirmation and the person-only refusal), and pictures of each screen. The `build` job proves core's claims for every component and checks the
   snapshots in light, dark, increased contrast and 200% text.
2. In a pull request, set `ouro.version` in `gradle.properties` to the new version and write the notes in
   `docs/releases/vX.Y.Z.md`.
3. Check the next free tag (`git ls-remote --tags origin`), then tag and push:

   ```sh
   git tag -a vX.Y.Z -m "vX.Y.Z: <one line>" <commit>
   git push origin vX.Y.Z
   ```

   The release workflow checks the tag matches `ouro.version` and has notes, runs the tests, builds the Maven files
   for `bot.ouro.ui:ouro-actions` and `bot.ouro.ui:ouro-hx`, and creates the release with them attached. It then
   publishes every release's Maven files to GitHub Pages as one Maven repository:

   ```kotlin
   repositories { maven { url = uri("https://ourostack.github.io/ouro-ui-android/maven") } }
   dependencies { implementation("bot.ouro.ui:ouro-hx:X.Y.Z") }
   ```

4. Wait for the release workflow to pass. A failure with no open issue is a bug: file it before anything is rerun.

## What a release can't prove

The maintainer has no Android device. A release is checked on the CI emulator only, and its notes say so. If you
have a device and find a difference, open an issue; that's exactly the help we're looking for.

## Maven Central (later)

Pages needs no account and no token to read. Maven Central would let apps skip the extra repository line, but it
needs a Sonatype account that only Ari can create. GitHub Packages needs a token even to download, so it isn't used.
