#!/usr/bin/env bash
set -uo pipefail
EV="${EV:-evidence}"; mkdir -p "$EV"
sleep 30
{
  adb shell pm list packages | grep -iE "talkback|marvin|accessib|vending|gms"
  adb shell pm list packages -f com.google.android.marvin.talkback
  adb shell dumpsys package com.google.android.marvin.talkback | grep -iE "versionName|versionCode|service"
  adb shell getprop ro.build.flavor
} > "$EV/packages.txt" 2>&1
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
sleep 15
{ adb shell settings get secure enabled_accessibility_services; adb shell dumpsys accessibility | head -60; } > "$EV/a11y.txt" 2>&1
adb logcat -d | grep -iE "talkback|marvin" | tail -50 > "$EV/logcat.txt" 2>&1
