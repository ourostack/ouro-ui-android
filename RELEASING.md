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
   for `bot.ouro.ui:ouro-actions` and `bot.ouro.ui:ouro-hx`, and creates the release with them attached. Its `central`
   job signs those files and publishes them to Maven Central (below). It then
   publishes every release's Maven files to GitHub Pages as one Maven repository (its own jobs, so a failed deploy reruns
   alone; to rebuild and deploy the site by hand, run the Release workflow on `main` from the Actions tab):

   ```kotlin
   repositories { maven { url = uri("https://ourostack.github.io/ouro-ui-android/maven") } }
   dependencies { implementation("bot.ouro.ui:ouro-hx:X.Y.Z") }
   ```

4. Wait for the release workflow to pass. A failure with no open issue is a bug: file it before anything is rerun.

## What a release can't prove

The maintainer has no Android device. A release is checked on the CI emulator only, and its notes say so. If you
have a device and find a difference, open an issue; that's exactly the help we're looking for.

## Maven Central

The `central` job in `release.yml` downloads the Maven files the release attached, and `scripts/central-bundle.sh`
adds a placeholder javadoc jar (the kit is Kotlin, documented in the README), an `.asc` signature and `.md5` and `.sha1`
checksums for every file. `scripts/central-publish.sh` uploads that bundle to the
[Central Portal](https://central.sonatype.com/api-doc) with `publishingType=AUTOMATIC` and waits until Central says
`PUBLISHED`. A version Central already has is skipped, so rerunning the job is safe. CI's `build` job builds the same
bundle with a throwaway key on every pull request and checks every signature, checksum and required POM field.

What it needs, all in place:

- **The namespace.** `bot.ouro` is registered on the Central Portal account that signs in with GitHub as
  `arimendelow`. Central verifies it with a DNS TXT record on `ouro.bot` holding the verification key the Portal shows
  under Namespaces; `bot.ouro.ui` is inside it. Until it shows **Verified**, uploads are refused and the `central` job
  fails, so verify it before the first tag after this change.
- **Secrets** on this repository: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (a Portal user token named
  `ouro-ui-android release`, no expiry; revoke and replace it at https://central.sonatype.com/usertoken), and
  `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` (the release signing key, Ed25519, no expiry). Its public half is
  [docs/signing-key.asc](docs/signing-key.asc), published on keyserver.ubuntu.com, where Central looks it up. The
  token and the key are also in the maintainer's macOS keychain.
