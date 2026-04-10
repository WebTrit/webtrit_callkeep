#!/usr/bin/env bash
# Run all integration test files on a device or in Chrome (web).
#
# Android (default):
#   Each file is run in isolation because `flutter test integration_test/`
#   launches all files in parallel, and concurrent setUp/tearDown calls share
#   the same Android Telecom service, leading to duplicate connections and
#   wedged state.
#
#   Between files the app is force-stopped and we wait until both OS processes
#   (main + :callkeep_core) are confirmed dead before the next file starts.
#
# Web:
#   All test files are combined in integration_test/all_tests.dart and run in
#   a single flutter drive session via chromedriver. Android-only test groups
#   are automatically skipped.
#
# Usage:
#   ./run_integration_tests.sh                   # auto-detect Android device
#   ./run_integration_tests.sh -d <device-id>    # explicit Android device
#   ./run_integration_tests.sh --web             # run on Chrome (web)

set -euo pipefail

CHROMEDRIVER_PORT=4444

# ---- Web mode ------------------------------------------------------------
if [[ "${1:-}" == "--web" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  echo "  [web] Killing any existing chromedriver on port $CHROMEDRIVER_PORT..."
  lsof -ti tcp:$CHROMEDRIVER_PORT | xargs kill -9 2>/dev/null || true
  sleep 1
  echo "  [web] Starting chromedriver..."
  npx chromedriver --port=$CHROMEDRIVER_PORT &
  CHROMEDRIVER_PID=$!
  trap "kill $CHROMEDRIVER_PID 2>/dev/null || true" EXIT
  sleep 2
  echo "  [web] Running all tests in Chrome..."
  cd "$SCRIPT_DIR"
  flutter drive \
    --driver=test_driver/integration_test.dart \
    --target=integration_test/all_tests.dart \
    -d chrome
  exit $?
fi

# ---- Android mode --------------------------------------------------------
DEVICE_FLAG=()
ADB_SERIAL_FLAG=()
if [[ "${1:-}" == "-d" && -n "${2:-}" ]]; then
  DEVICE_FLAG=(-d "$2")
  ADB_SERIAL_FLAG=(-s "$2")
fi

TESTS=(
  integration_test/callkeep_client_scenarios_test.dart
  integration_test/callkeep_connections_test.dart
  integration_test/callkeep_delegate_edge_cases_test.dart
  integration_test/callkeep_foreground_service_test.dart
  integration_test/callkeep_lifecycle_test.dart
  integration_test/callkeep_reportendcall_reasons_test.dart
  integration_test/callkeep_state_machine_test.dart
  integration_test/callkeep_stress_test.dart
  integration_test/callkeep_call_scenarios_test.dart
  # background_services_test runs last: it starts IncomingCallService which
  # Android may restart after force-stop. Running it last avoids the 5-second
  # ForegroundServiceDidNotStartInTimeException crash contaminating other files.
  integration_test/callkeep_background_services_test.dart
)

APP_ID="com.example.example"

# Inter-file cleanup: force-stop the app, free wedged Telecom connections,
# then poll until both app processes are confirmed dead. This ensures the
# next test file starts from a clean process state.
#
# Note: the app runs as two OS processes -- the main process ($APP_ID) and
# the :callkeep_core service process ($APP_ID:callkeep_core). pidof only
# matches exact process names, so we use `ps -A | grep` on the device to
# detect both. The grep is run on the device so that only one adb round-trip
# is needed per poll iteration.
#
# After process death we also poll `dumpsys telecom` until the mCalls section
# no longer references our package. Telecom drains DISCONNECTING connections
# asynchronously after force-stop; if the next test starts before Telecom
# finishes, callkeep.setUp() fails and all tests report "did not complete [E]".
cleanup_device() {
  adb "${ADB_SERIAL_FLAG[@]+"${ADB_SERIAL_FLAG[@]}"}" shell am force-stop "$APP_ID" 2>/dev/null || true
  adb "${ADB_SERIAL_FLAG[@]+"${ADB_SERIAL_FLAG[@]}"}" shell telecom cleanup-stuck-calls 2>/dev/null || true
  while adb "${ADB_SERIAL_FLAG[@]+"${ADB_SERIAL_FLAG[@]}"}" shell \
      "ps -A 2>/dev/null | grep -q '$APP_ID'" 2>/dev/null; do
    sleep 1
  done
  # Poll dumpsys telecom until our app has no active connections.
  # The current call state lives in the "mCalls:" section of the dump.
  # When it is empty (no active connections from any app), our app's package
  # will not appear between "mCalls:" and the next section header.
  # Timeout after 30 s.
  local deadline=$((SECONDS + 30))
  while [[ $SECONDS -lt $deadline ]]; do
    local dump
    dump=$(adb "${ADB_SERIAL_FLAG[@]+"${ADB_SERIAL_FLAG[@]}"}" shell \
      "dumpsys telecom 2>/dev/null" 2>/dev/null || true)
    # Extract the mCalls section and check if our package appears in it.
    # When no calls are active, this section is empty and APP_ID won't match.
    local mcalls_section
    mcalls_section=$(echo "$dump" | awk '/mCalls:/,/mCallAudioManager:/')
    if ! echo "$mcalls_section" | grep -q "${APP_ID}"; then
      break
    fi
    sleep 1
  done
  # Fixed safety margin for UiAutomation to release its session.
  sleep 5
}

run_test_file() {
  local test_file="$1"
  # Retry once on infrastructure failures (WebSocket drop during loading).
  # These are not test-logic failures -- they occur when the Flutter test
  # runner cannot attach to the app after a long previous test file.
  if flutter test "$test_file" "${DEVICE_FLAG[@]+"${DEVICE_FLAG[@]}"}"; then
    return 0
  fi
  echo "  [retry] $test_file failed, cleaning up and retrying once..."
  cleanup_device
  flutter test "$test_file" "${DEVICE_FLAG[@]+"${DEVICE_FLAG[@]}"}"
}

PASS=0
FAIL=0
FAILED_FILES=()

# Initial cleanup so the suite starts from a known state regardless of what
# was running on the device before.
echo "  [init] Cleaning up any stale app state..."
cleanup_device
echo "  [init] Device ready."

for TEST_FILE in "${TESTS[@]}"; do
  echo ""
  echo "=========================================="
  echo "Running: $TEST_FILE"
  echo "=========================================="

  if run_test_file "$TEST_FILE"; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    FAILED_FILES+=("$TEST_FILE")
  fi

  cleanup_device
done

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed"
if [[ ${#FAILED_FILES[@]} -gt 0 ]]; then
  echo "Failed files:"
  for F in "${FAILED_FILES[@]}"; do
    echo "  - $F"
  done
  exit 1
fi
echo "All integration test files passed."
