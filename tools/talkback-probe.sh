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
spoken() { adb logcat -d | grep OuroSpoken | sed 's/.*OuroSpoken: //'; }
wait_spoken() { # <text> <seconds>
  for _ in $(seq 1 "$2"); do spoken | grep -qF "$1" && return 0; sleep 1; done; return 1
}
wait_spoken "Welcome to TalkBack, Page 1" 90; sleep 3
{ adb shell settings get secure enabled_accessibility_services; adb shell dumpsys accessibility | grep -iE "Enabled services|Bound services|Crashed|installedServiceCount" ; } > "$EV/a11y.txt" 2>&1
adb shell input keyevent KEYCODE_BACK; sleep 2
adb logcat -c
adb shell am start -W -n bot.ouro.shop/.MainActivity --es screen table > /dev/null
wait_spoken "In grid Recipes" 30 || echo "TalkBack never spoke the Table"; sleep 3
speak_on_tap() { # <content-desc prefix> <case>
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml "$EV/ui.xml" > /dev/null 2>&1
  local xy; xy=$(python3 - "$1" "$EV/ui.xml" <<'PY'
import re, sys
for n in re.findall(r'<node [^>]*>', open(sys.argv[2]).read()):
    d = re.search(r'content-desc="([^"]*)"', n).group(1)
    if d.startswith(sys.argv[1]):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', n).group(1)))
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  adb logcat -c; adb shell input tap $xy; sleep 4
  adb logcat -d | grep OuroSpoken | sed 's/.*OuroSpoken: //' > "$EV/speech-$2.txt"
}
speak_on_tap "Recipe" header; speak_on_tap "Serves" sorted; speak_on_tap "Herb salad" row
for f in header sorted row; do echo "== $f"; cat "$EV/speech-$f.txt"; done
{ adb shell settings get secure tts_default_synth; adb shell dumpsys activity services bot.ouro.shop; adb shell dumpsys accessibility | head -30; adb shell ps -A | grep -i "talkback\|ouro"; } > "$EV/tts.txt" 2>&1
adb logcat -d > "$EV/logcat-full.txt" 2>&1
grep OuroSpoken "$EV/logcat-full.txt" > "$EV/logcat.txt"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$EV/ui.xml" >/dev/null 2>&1
exit 0
