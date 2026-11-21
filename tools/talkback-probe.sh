#!/usr/bin/env bash
set -uo pipefail
EV="${EV:-evidence}"; mkdir -p "$EV"
for _ in $(seq 1 60); do adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > /dev/null 2>&1 && break; sleep 2; done
sleep 20
adb install -r -g "$RUNNER_TEMP/talkback.apk" > "$EV/install-talkback.txt" 2>&1
adb install -r samples/shop/build/outputs/apk/debug/shop-debug.apk > "$EV/install-shop.txt" 2>&1
adb shell pm list packages | grep -iE "talkback" > "$EV/packages.txt" 2>&1
adb shell pm list packages -f com.android.talkback >> "$EV/packages.txt" 2>&1
adb shell settings put secure tts_default_synth bot.ouro.shop
adb shell svc power stayon true; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard
adb shell am start -W -n bot.ouro.shop/.MainActivity --es screen table > /dev/null
sleep 5
adb logcat -c
SVC=com.android.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
spoken() { adb logcat -d | grep OuroSpoken | sed 's/.*OuroSpoken: //'; }
sleep 45
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell am start -W -n bot.ouro.shop/.MainActivity --es screen table > /dev/null
sleep 5
# a UI dump switches TalkBack off and on, which is what makes it start speaking
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; sleep 20
adb logcat -c
for _ in 1 2 3 4 5 6 7 8; do
  adb shell input keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_RIGHT; sleep 3
done
spoken > "$EV/speech-all.txt"
status=0
check() { # <case> <expected phrase>
  if grep -qF "$2" "$EV/speech-all.txt"; then echo "TalkBack case $1: PASS expected \"$2\""
  else echo "TalkBack case $1: FAIL expected \"$2\""; status=1; fi
}
check header "Column heading"
check sorted "Serves, sorted descending"
check row "Herb salad, 10 min, 2"
sed 's/^/  spoke: /' "$EV/speech-all.txt"
adb logcat -d > "$EV/logcat-full.txt" 2>&1
adb shell dumpsys accessibility | head -30 > "$EV/a11y.txt" 2>&1
exit $status
