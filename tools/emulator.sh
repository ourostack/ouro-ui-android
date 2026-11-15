#!/usr/bin/env bash
# Runs inside android-emulator-runner on CI: the X-ray on the emulator, then pictures of each screen as a person sees
# it, and what Android itself lists for the app's AppFunctions. Everything goes to $EV.
set -uo pipefail
EV="${EV:-evidence}"
mkdir -p "$EV"
adb shell getprop ro.build.version.release > "$EV/android-version.txt"
adb shell getprop ro.build.version.sdk >> "$EV/android-version.txt"
adb shell cmd app_function help > "$EV/cmd-app-function-help.txt" 2>&1 || true

./gradlew --no-daemon :samples:shop:connectedDebugAndroidTest
code=$?
cp -r samples/shop/build/outputs/androidTest-results "$EV/" 2>/dev/null || true
cp -r samples/shop/build/reports/androidTests "$EV/" 2>/dev/null || true

adb install -r samples/shop/build/outputs/apk/debug/shop-debug.apk
# Android indexes a newly installed app's AppFunctions in the background; wait up to two minutes for them.
for _ in $(seq 1 24); do
  adb shell cmd app_function list-app-functions --package bot.ouro.shop > "$EV/cmd-app-function-list.txt" 2>&1 || true
  grep -q addShoppingListItem "$EV/cmd-app-function-list.txt" && break
  sleep 5
done
adb shell dumpsys app_function > "$EV/dumpsys-app-function.txt" 2>&1 || true

shot() {
  local name="$1"; shift
  adb shell am force-stop bot.ouro.shop
  adb shell am start -W -n bot.ouro.shop/.MainActivity "$@" > /dev/null
  sleep 6
  # The emulator takes the picture from the host: screencap in the guest aborts under software rendering on API 37.
  adb emu screenrecord screenshot "$EV/$name.png" > /dev/null
}
shot shop-light --ez dark false
shot gallery-light --es screen gallery --ez dark false --ez contrast false
shot gallery-dark --es screen gallery --ez dark true --ez contrast false
shot gallery-contrast --es screen gallery --ez dark false --ez contrast true
shot gallery-contrast-dark --es screen gallery --ez dark true --ez contrast true
shot gallery-cookbook --es screen gallery --es theme cookbook --ez dark false
shot agent-console --es screen agent --ez dark false
adb shell am force-stop bot.ouro.shop
adb logcat -d | grep -iE "appfunction|appsearch.*(index|app_?fun)" | tail -300 > "$EV/logcat-appfunctions.txt" || true
exit $code
