#!/usr/bin/env bash
set -uo pipefail
EV="${EV:-evidence}"; mkdir -p "$EV"
for _ in $(seq 1 60); do adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > /dev/null 2>&1 && break; sleep 2; done
sleep 20
adb install -r "$RUNNER_TEMP/talkback.apk" > "$EV/install-talkback.txt" 2>&1
adb install -r samples/shop/build/outputs/apk/debug/shop-debug.apk > "$EV/install-shop.txt" 2>&1
adb shell pm list packages | grep -iE "talkback" > "$EV/packages.txt" 2>&1
adb shell pm list packages -f com.android.talkback >> "$EV/packages.txt" 2>&1
adb shell setprop persist.log.tag V
adb shell svc power stayon true; adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard
adb shell am start -W -n bot.ouro.shop/.MainActivity --es screen table > /dev/null
sleep 5
adb logcat -c
SVC=com.android.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
sleep 25
{ adb shell settings get secure enabled_accessibility_services; adb shell dumpsys accessibility | grep -iE "Enabled services|Bound services|Crashed|installedServiceCount" ; } > "$EV/a11y.txt" 2>&1
for i in $(seq 1 12); do adb shell input swipe 800 1200 200 1200 150; sleep 3; done
adb logcat -d > "$EV/logcat-full.txt" 2>&1
grep -iE "speech|spoken|utterance|talkback" "$EV/logcat-full.txt" | head -400 > "$EV/logcat.txt"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$EV/ui.xml" >/dev/null 2>&1
exit 0
