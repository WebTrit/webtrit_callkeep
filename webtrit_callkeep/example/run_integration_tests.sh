#!/usr/bin/env bash
# Run all integration test files sequentially on a single device.
#
# Running `flutter test integration_test/` launches each file in a separate
# parallel process. All processes share the same Android Telecom service, so
# concurrent setUp/tearDown calls from different files interfere with each
# other. This script avoids that by running one file at a time.
#
# Between files, the script force-stops the app to ensure all processes
# (main + :callkeep_core) are dead and Android Telecom has finished
# processing disconnect events before the next APK is installed.
#
# If a file fails immediately (app startup crash, 0 tests run), it is
# retried once after a longer cleanup pause.
#
# Usage:
#   ./run_integration_tests.sh                   # auto-detect device
#   ./run_integration_tests.sh -d <device-id>    # explicit device

set -euo pipefail

DEVICE_FLAG=()
ADB_SERIAL_FLAG=()
if [[ "${1:-}" == "-d" && -n "${2:-}" ]]; then
  DEVICE_FLAG=(-d "$2")
  ADB_SERIAL_FLAG=(-s "$2")
fi

TESTS=(
  integration_test/callkeep_background_services_test.dart
  integration_test/callkeep_call_scenarios_test.dart
  integration_test/callkeep_client_scenarios_test.dart
  integration_test/callkeep_connections_test.dart
  integration_test/callkeep_delegate_edge_cases_test.dart
  integration_test/callkeep_foreground_service_test.dart
  integration_test/callkeep_lifecycle_test.dart
  integration_test/callkeep_reportendcall_reasons_test.dart
  integration_test/callkeep_state_machine_test.dart
  integration_test/callkeep_stress_test.dart
)

APP_ID="com.example.example"

# Force-stop the app (kills main + :callkeep_core processes) and wait for
# Android Telecom to finish processing the resulting disconnect events.
cleanup_device() {
  local wait_secs="${1:-3}"
  adb "${ADB_SERIAL_FLAG[@]+"${ADB_SERIAL_FLAG[@]}"}" shell am force-stop "$APP_ID" 2>/dev/null || true
  sleep "$wait_secs"
}

run_test_file() {
  local test_file="$1"
  flutter test "$test_file" "${DEVICE_FLAG[@]+"${DEVICE_FLAG[@]}"}"
}

PASS=0
FAIL=0
FAILED_FILES=()

FIRST=true
for TEST_FILE in "${TESTS[@]}"; do
  if [[ "$FIRST" == "true" ]]; then
    FIRST=false
  else
    # Standard inter-file cleanup: force-stop + 8s for Telecom to settle.
    # Some test files run many answer/end cycles; Android Telecom needs time
    # to process all disconnect events and release the UiAutomation connection
    # before the next flutter test process can attach.
    cleanup_device 8
  fi

  echo ""
  echo "=========================================="
  echo "Running: $TEST_FILE"
  echo "=========================================="

  if run_test_file "$TEST_FILE"; then
    PASS=$((PASS + 1))
  else
    # Retry once with a longer cleanup pause in case the failure was caused
    # by residual Telecom state from the previous suite (app startup crash).
    echo ""
    echo "  [retry] Cleaning up and retrying $TEST_FILE ..."
    cleanup_device 15
    if run_test_file "$TEST_FILE"; then
      echo "  [retry] Passed on second attempt."
      PASS=$((PASS + 1))
    else
      FAIL=$((FAIL + 1))
      FAILED_FILES+=("$TEST_FILE")
    fi
  fi
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
