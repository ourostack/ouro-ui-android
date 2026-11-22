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
{ adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"; adb shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity"; } > "$EV/focus.txt" 2>&1
focus() { # <case> <content-desc prefix>
  adb logcat -c
  adb shell am broadcast -a bot.ouro.shop.FOCUS -p bot.ouro.shop --es name "'$2'" > /dev/null; sleep 5
  spoken > "$EV/speech-$1.txt"; adb logcat -d | grep OuroFocus >> "$EV/focus-calls.txt"
}
# the toggle leaves focus on the header, so read it last, after focus has moved away
focus sorted Serves; focus row "Herb salad"; focus header Recipe
# TalkBack's own next/previous, from a person's input: which input moves its focus on the emulator
walk() { # <driver> <steps> <command...>
  d=$1; n=$2; shift 2
  focus "other-$d" "Herb salad"; focus "start-$d" Recipe
  for i in $(seq 1 "$n"); do
    adb logcat -c; adb shell "$@" > "$EV/drive-$d-$i.txt" 2>&1; sleep 4
    spoken > "$EV/nav-$d-$i.txt"
    adb logcat -c; adb shell am broadcast -a bot.ouro.shop.WHERE -p bot.ouro.shop > /dev/null; sleep 1
    adb logcat -d | grep OuroWhere | sed 's/.*OuroWhere: //' > "$EV/where-$d-$i.txt"
  done
  echo "== walk $d"; for i in $(seq 1 "$n"); do echo "step $i: focus=$(cat "$EV/where-$d-$i.txt") speech=$(tr '\n' '|' < "$EV/nav-$d-$i.txt")"; done
}
walk altkeys 4 input keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_RIGHT
walk metakeys 4 input keycombination KEYCODE_META_LEFT KEYCODE_DPAD_RIGHT
walk swipe 4 input swipe 200 1200 900 1200 120
walk dpad 4 input keyevent KEYCODE_DPAD_RIGHT
adb logcat -d | grep -iE "talkback|accessib" | cut -c1-240 > "$EV/tb-log.txt"
status=0
if grep -qE "no node|no root" "$EV/focus-calls.txt"; then echo "TalkBack focus hook found no node"; status=1; fi
check() { # <case> <expected phrase>
  if grep -qF "$2" "$EV/speech-$1.txt"; then echo "TalkBack case $1: PASS expected \"$2\""
  else echo "TalkBack case $1: FAIL expected \"$2\""; status=1; fi
}
check header "Recipe, Column heading"
check sorted "Serves, sorted descending"
check row "Herb salad, 10 min, 2, Row 3"
for f in header sorted row; do echo "== $f"; cat "$EV/speech-$f.txt"; done
adb logcat -d > "$EV/logcat-full.txt" 2>&1
adb shell dumpsys input | grep -iE "keyboard|Device [0-9-]+:" | head -30 > "$EV/a11y.txt"; adb shell dumpsys activity service com.android.talkback/com.google.android.marvin.talkback.TalkBackService | head -80 >> "$EV/a11y.txt"; adb shell dumpsys accessibility | head -30 >> "$EV/a11y.txt" 2>&1
exit $status
