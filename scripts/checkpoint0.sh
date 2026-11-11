#!/usr/bin/env bash
# Checkpoint 0 — on-device checklist for Mind Palace (about 10 minutes).
# Guide: docs/CHECKPOINT_0.md
#
# Usage (phone connected by USB, USB debugging on):
#   scripts/checkpoint0.sh                 # run the whole checklist
#   scripts/checkpoint0.sh --skip-reboot   # skip the reboot step (records SKIP)
#   scripts/checkpoint0.sh --pull-export   # copy private export files to ./exports-pulled/
#
# Safety: this script NEVER uninstalls the app and NEVER clears its data.
# Uninstalling deletes every note (allowBackup=false, docs/DECISIONS.md D4).

set -u

PKG="com.mindpalace.app"
MAIN="$PKG/.MainActivity"
SHARE="$PKG/.capture.ShareReceiverActivity"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
RESULTS="$ROOT/docs/TEST_RESULTS.md"
STAMP="$(date '+%Y-%m-%d %H:%M')"
TAG="cp0-$(date '+%H%M%S')"

SKIP_REBOOT=0
PULL_EXPORT=0
for arg in "$@"; do
  case "$arg" in
    --skip-reboot) SKIP_REBOOT=1 ;;
    --pull-export) PULL_EXPORT=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg"; exit 2 ;;
  esac
done

ROWS=()
FAILS=0

record() { # record <PASS|FAIL|SKIP> <step> <detail>
  ROWS+=("| $2 | **$1** | $3 |")
  [ "$1" = "FAIL" ] && FAILS=$((FAILS + 1))
  printf '  -> %s: %s %s\n' "$1" "$2" "${3:+($3)}"
}

ask() { # ask <step> <question>   (y = pass, n = fail, s = skip)
  local answer
  while true; do
    printf '\n%s\n  [y] yes   [n] no   [s] skip > ' "$2"
    read -r answer
    case "$answer" in
      y|Y) record PASS "$1" "confirmed by owner"; return 0 ;;
      n|N) printf '  What happened? (one line) > '; read -r answer
           record FAIL "$1" "${answer:-no detail}"; return 1 ;;
      s|S) record SKIP "$1" "skipped by owner"; return 0 ;;
    esac
  done
}

need_device() {
  command -v adb >/dev/null 2>&1 || { echo "adb not found. Install Android platform-tools."; exit 2; }
  local count
  count="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
  if [ "$count" != "1" ]; then
    echo "Expected exactly 1 connected device, found $count."
    echo "Check the cable, unlock the phone, and accept the USB debugging prompt."
    adb devices
    exit 2
  fi
}

wait_for_boot() {
  adb wait-for-device
  local tries=0
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 3; tries=$((tries + 1))
    [ "$tries" -gt 60 ] && return 1
  done
  return 0
}

need_device

# ---------------------------------------------------------------- export pull
if [ "$PULL_EXPORT" = "1" ]; then
  OUT="$ROOT/exports-pulled"
  mkdir -p "$OUT"
  FILES="$(adb shell run-as "$PKG" ls files/exports 2>/dev/null | tr -d '\r')"
  if [ -z "$FILES" ]; then
    echo "No export files found in the app's private folder."
    exit 1
  fi
  for f in $FILES; do
    adb exec-out run-as "$PKG" cat "files/exports/$f" > "$OUT/$f"
    echo "Pulled $OUT/$f"
  done
  echo "Done. These files contain your notes. Keep them private."
  exit 0
fi

DEVICE="$(adb shell getprop ro.product.model | tr -d '\r') / Android $(adb shell getprop ro.build.version.release | tr -d '\r')"
echo "Checkpoint 0 on: $DEVICE"
echo "Results will be appended to docs/TEST_RESULTS.md"

# ---------------------------------------------------------------- 1. install
echo; echo "== 1. Install =="
if [ ! -f "$APK" ]; then
  echo "APK not found at $APK"
  echo "Build it first:  ./gradlew assembleDebug"
  exit 2
fi
INSTALL_OUT="$(adb install -r "$APK" 2>&1)"
if echo "$INSTALL_OUT" | grep -q "Success"; then
  record PASS "1 Install" "adb install -r"
elif echo "$INSTALL_OUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
  record FAIL "1 Install" "signature mismatch"
  cat <<'MSG'

  STOP. The phone has a copy of Mind Palace signed by a different computer.
  Do NOT uninstall it: uninstalling deletes every note on the phone.
  Build and install from the same computer that installed it before,
  or export your notes first (see docs/CHECKPOINT_0.md, "Signature mismatch").
MSG
else
  record FAIL "1 Install" "$(echo "$INSTALL_OUT" | tail -1)"
fi

# ---------------------------------------------------------------- 2. privacy
echo; echo "== 2. No network permission on the installed app =="
PERMS="$(adb shell dumpsys package "$PKG" | tr -d '\r')"
if echo "$PERMS" | grep -Eq 'android\.permission\.(INTERNET|ACCESS_NETWORK_STATE)'; then
  record FAIL "2 No network permission" "INTERNET or ACCESS_NETWORK_STATE is present"
else
  record PASS "2 No network permission" "dumpsys package"
fi

# ---------------------------------------------------------------- 3. airplane mode
echo; echo "== 3. Airplane mode for the whole run =="
adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1
AIRPLANE="$(adb shell settings get global airplane_mode_on | tr -d '\r')"
if [ "$AIRPLANE" = "1" ]; then
  record PASS "3 Airplane mode on" "set by script"
else
  ask "3 Airplane mode on" "Could not switch airplane mode from the computer. Turn it ON by hand now. Is it on?"
fi

# ---------------------------------------------------------------- 4. launch
echo; echo "== 4. Launch =="
adb shell am force-stop "$PKG"
LAUNCH="$(adb shell am start -W -n "$MAIN" 2>&1 | tr -d '\r')"
if echo "$LAUNCH" | grep -q "Status: ok"; then
  record PASS "4 Launch" "cold start $(echo "$LAUNCH" | awk '/TotalTime/ {print $2}') ms"
else
  record FAIL "4 Launch" "$(echo "$LAUNCH" | tail -1)"
fi

# ---------------------------------------------------------------- 5. typed note
ask "5 Typed note saves" "On the phone: type a note containing the word  $TAG  and tap Save.
Does it show 'Saved (rev 1)' and appear in the list?"

# ---------------------------------------------------------------- 6. force stop
echo; echo "== 6. Survives force stop =="
adb shell am force-stop "$PKG"
adb shell am start -W -n "$MAIN" >/dev/null 2>&1
ask "6 Survives force stop" "The app was force-stopped and reopened. Is the $TAG note still in the list?"

# ---------------------------------------------------------------- 7. share
echo; echo "== 7. Share into Mind Palace =="
SHARE_OUT="$(adb shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "shared-$TAG" -n "$SHARE" 2>&1 | tr -d '\r')"
SHARE_MS="$(echo "$SHARE_OUT" | awk '/TotalTime/ {print $2}')"
if [ -n "$SHARE_MS" ] && [ "$SHARE_MS" -gt 2000 ] 2>/dev/null; then
  record FAIL "7a Share saves in under 2 s" "${SHARE_MS} ms"
else
  record PASS "7a Share saves in under 2 s" "${SHARE_MS:-n/a} ms (script-sent share)"
fi
adb shell am start -W -n "$MAIN" >/dev/null 2>&1
ask "7b Shared note visible" "A test share was sent. Is a note  shared-$TAG  in the list?"
ask "7c Share from a real app" "Open any app with text (Samsung Notes, Chrome while offline shows a page title),
choose Share -> 'Save to Mind Palace'. Did you see the 'Saved' toast and does the note appear?"

# ---------------------------------------------------------------- 8. search
ask "8 Search finds a word" "Type  $TAG  in the search box. Do the matching notes appear?"

# ---------------------------------------------------------------- 9. reboot
echo; echo "== 9. Survives reboot =="
if [ "$SKIP_REBOOT" = "1" ]; then
  record SKIP "9 Survives reboot" "--skip-reboot"
else
  echo "Rebooting the phone. Unlock it when it comes back (about 1 minute)."
  adb reboot
  if wait_for_boot; then
    printf 'Unlock the phone, then press Enter > '; read -r _
    adb shell am start -W -n "$MAIN" >/dev/null 2>&1
    ask "9 Survives reboot" "After the reboot: are both $TAG notes still in the list?"
    AIRPLANE2="$(adb shell settings get global airplane_mode_on | tr -d '\r')"
    if [ "$AIRPLANE2" = "1" ]; then
      record PASS "9b Still offline after reboot" "airplane mode still on"
    else
      record FAIL "9b Still offline after reboot" "airplane mode turned off"
    fi
  else
    record FAIL "9 Survives reboot" "phone did not finish booting within 3 minutes"
  fi
fi

# ---------------------------------------------------------------- 10. database present
echo; echo "== 10. Database file on disk =="
DB_LINE="$(adb shell run-as "$PKG" ls -l databases/mind_palace.db 2>&1 | tr -d '\r')"
if echo "$DB_LINE" | grep -q "mind_palace.db" && ! echo "$DB_LINE" | grep -qi "no such"; then
  record PASS "10 Database file exists" "$(echo "$DB_LINE" | awk '{print $5}') bytes"
else
  record FAIL "10 Database file exists" "$DB_LINE"
fi

# ---------------------------------------------------------------- wrap up
echo
printf 'Turn airplane mode back OFF now? [y/n] > '
read -r off
case "$off" in y|Y) adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1 ;; esac

if [ "$FAILS" = "0" ]; then VERDICT="PASS"; else VERDICT="FAIL ($FAILS failed)"; fi
COMMIT="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"

{
  echo
  echo "## $STAMP — Checkpoint 0 on device — $VERDICT"
  echo
  echo "**Device:** $DEVICE. **Commit:** \`$COMMIT\`. **Run by:** scripts/checkpoint0.sh"
  echo
  echo "| Step | Result | Detail |"
  echo "|---|---|---|"
  for row in "${ROWS[@]}"; do echo "$row"; done
} >> "$RESULTS"

echo
echo "Checkpoint 0: $VERDICT"
echo "Logged to docs/TEST_RESULTS.md — commit that file to keep the record."
[ "$FAILS" = "0" ]
