#!/usr/bin/env bash
set -uo pipefail
EV="${EV:-evidence}"; mkdir -p "$EV"
for _ in $(seq 1 60); do adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > /dev/null 2>&1 && break; sleep 2; done
sleep 20
adb install -r "$RUNNER_TEMP/talkback.apk" > "$EV/install-talkback.txt" 2>&1
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
sleep 25
{ adb shell settings get secure enabled_accessibility_services; adb shell dumpsys accessibility | grep -iE "Enabled services|Bound services|Crashed|installedServiceCount" ; } > "$EV/a11y.txt" 2>&1
adb shell settings put secure enabled_accessibility_services ""; sleep 3
adb shell am force-stop com.android.talkback; sleep 2
cat > "$EV/prefs.xml" <<'XML'
<map><string name="pref_log_level">2</string><boolean name="first_time_user" value="false" /></map>
XML
adb push "$EV/prefs.xml" /data/local/tmp/tb-prefs.xml > "$EV/prefs.txt" 2>&1
adb shell "run-as com.android.talkback mkdir -p shared_prefs; run-as com.android.talkback cp /data/local/tmp/tb-prefs.xml shared_prefs/com.android.talkback_preferences.xml" >> "$EV/prefs.txt" 2>&1
adb shell "run-as com.android.talkback cat shared_prefs/com.android.talkback_preferences.xml" >> "$EV/prefs.txt" 2>&1
adb shell settings put secure enabled_accessibility_services "$SVC"; sleep 20
adb shell am start -W -n bot.ouro.shop/.MainActivity --es screen table > /dev/null
sleep 8
for i in $(seq 1 14); do adb shell input swipe 800 1200 200 1200 150; sleep 3; done
{ adb shell settings get secure tts_default_synth; adb shell dumpsys activity services bot.ouro.shop; adb shell dumpsys accessibility | head -30; adb shell ps -A | grep -i "talkback\|ouro"; } > "$EV/tts.txt" 2>&1
adb logcat -d > "$EV/logcat-full.txt" 2>&1
grep OuroSpoken "$EV/logcat-full.txt" > "$EV/logcat.txt"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$EV/ui.xml" >/dev/null 2>&1
exit 0
