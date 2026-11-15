# Working in ouro-ui-android

- Trunk-based: `main` is the only long-lived branch. Small pull requests, auto-merge on, merged green within about 30 minutes.
- No AI attribution anywhere: no `Co-Authored-By` lines, no "Generated with" footers.
- What comes from core is vendored by release tag and SHA-256 and never edited. Change core, then bump the pin.
- Builds, tests and the emulator run on GitHub's free `ubuntu-latest` runners only; no paid runners or services.
- The maintainer has no Android device. Never claim something works on a device; say "on the CI emulator".
