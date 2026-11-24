#!/usr/bin/env bash
# Turns a Maven repository folder (Gradle's build/maven, or a release's ouro-ui-android-vX.Y.Z-maven.zip unpacked) into
# the bundle Maven Central's Portal takes: each artifact gets a placeholder javadoc jar (the kit is Kotlin, documented
# in its README), an .asc signature per file, and an .md5 and .sha1 per file. Sign with the key already in gpg's keyring;
# GPG_PASSPHRASE is its passphrase. Usage: scripts/central-bundle.sh <maven-dir> <bundle.zip>
set -euo pipefail
src="$1"
out="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp -R "$src"/. "$work"/
cd "$work"
# The bundle carries the versions only; Central writes its own maven-metadata.xml.
find . -name 'maven-metadata.xml*' -delete
find . -name '*.pom' | while read -r pom; do
  dir="$(dirname "$pom")"; base="$(basename "$pom" .pom)"
  if [ ! -f "$dir/$base-javadoc.jar" ]; then
    mkdir -p "$work/.javadoc" && printf 'Ouro UI for Android is documented in its README: https://github.com/ourostack/ouro-ui-android\n' > "$work/.javadoc/README.md"
    (cd "$work/.javadoc" && zip -qX "$OLDPWD/$dir/$base-javadoc.jar" README.md)
  fi
done
rm -rf "$work/.javadoc"
find . -type f ! -name '*.asc' ! -name '*.md5' ! -name '*.sha1' ! -name '*.sha256' ! -name '*.sha512' | while read -r f; do
  [ -f "$f.md5" ] || openssl md5 -r "$f" | cut -d' ' -f1 > "$f.md5"
  [ -f "$f.sha1" ] || openssl sha1 -r "$f" | cut -d' ' -f1 > "$f.sha1"
  printf %s "$GPG_PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 --armor --detach-sign "$f"
done
rm -f "$out"
zip -qr "$out" .
echo "Bundle: $out"
find . -type f | sort
