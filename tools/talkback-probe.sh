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

adb logcat -c
SVC=com.android.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
spoken() { adb logcat -d | grep OuroSpoken | sed 's/.*OuroSpoken: //'; }
sleep 45
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell am start -W -n bot.ouro.shop/.MainActivity --es screen table > /dev/null
sleep 5
tap() { # <content-desc prefix>
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
  [ -n "$xy" ] && adb shell input tap $xy
}
status=0
check() { # <case> <content-desc prefix> <expected phrase>
  for _ in 1 2 3 4 5 6; do
    adb logcat -c; tap "$2"; sleep 4
    spoken > "$EV/speech-$1.txt"
    grep -qF "$3" "$EV/speech-$1.txt" && { echo "TalkBack case $1: PASS expected \"$3\""; return; }
  done
  echo "TalkBack case $1: FAIL expected \"$3\""; sed 's/^/  spoke: /' "$EV/speech-$1.txt"; status=1
}
check header "Recipe" "Recipe, Column heading"
check sorted "Serves" "Serves, sorted descending"
check row "Herb salad" "Herb salad, 10 min, 2"
adb logcat -d > "$EV/logcat-full.txt" 2>&1
adb shell dumpsys accessibility | head -30 > "$EV/a11y.txt" 2>&1
exit $status
