#!/usr/bin/env bash
# Runs inside android-emulator-runner on CI: the X-ray on the emulator, then pictures of each screen as a person sees
# it, and what Android itself lists for the app's AppFunctions. Everything goes to $EV.
set -uo pipefail
EV="${EV:-evidence}"
mkdir -p "$EV"
adb shell getprop ro.build.version.release > "$EV/android-version.txt"
adb shell getprop ro.build.version.sdk >> "$EV/android-version.txt"
adb shell cmd app_function help > "$EV/cmd-app-function-help.txt" 2>&1 || true
# Booted is not settled: wait until the package manager and activity manager answer, then a little longer.
for _ in $(seq 1 60); do
  adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > /dev/null 2>&1 && break
  sleep 2
done
sleep 20

./gradlew --no-daemon :samples:shop:connectedDebugAndroidTest
code=$?
cp -r samples/shop/build/outputs/androidTest-results "$EV/" 2>/dev/null || true
cp -r samples/shop/build/reports/androidTests "$EV/" 2>/dev/null || true
# A run where Android fell over before any test started passes Gradle with zero tests; that is not a pass.
ran=$(grep -ho 'tests="[0-9]*"' samples/shop/build/outputs/androidTest-results/connected/debug/*.xml 2>/dev/null | head -1 | tr -dc 0-9)
if [ "${ran:-0}" -eq 0 ]; then echo "::error::the X-ray ran no tests on the emulator"; code=1; fi

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
  # The emulator takes the picture from the host where it can; the test image has no host screenshot, so then the guest does.
  adb emu screenrecord screenshot "$EV/$name.png" > /dev/null 2>&1
  [ -s "$EV/$name.png" ] || adb exec-out screencap -p > "$EV/$name.png"
  [ -s "$EV/$name.png" ] || echo "::warning::no picture of $name"
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
adb logcat -b crash -d > "$EV/logcat-crash.txt" 2>&1 || true
exit $code
