#!/usr/bin/env bash
# Shared utilities sourced by individual test scripts and the orchestrator.
#
# Callers must set (or leave empty) before sourcing:
#   DEVICE_FLAG      -- flutter -d flag, e.g. (-d abc123) or ()
#   ADB_SERIAL_FLAG  -- adb -s flag,     e.g. (-s abc123) or ()
#
# parse_device_flags "$@" populates both from the -d <id> argument.

APP_ID="com.example.example"

# parse_device_flags: extract -d <device-id> from script arguments.
# Sets DEVICE_FLAG and ADB_SERIAL_FLAG arrays.
parse_device_flags() {
  DEVICE_FLAG=()
  ADB_SERIAL_FLAG=()
  if [[ "${1:-}" == "-d" && -n "${2:-}" ]]; then
    DEVICE_FLAG=(-d "$2")
    ADB_SERIAL_FLAG=(-s "$2")
  fi
}

# cleanup_device: force-stop the app and wait for both OS processes and
# Telecom connections to drain before returning.
#
# Note: the app runs as two OS processes -- the main process ($APP_ID) and
# the :callkeep_core service process ($APP_ID:callkeep_core). pidof only
# matches exact process names, so we use `ps -A | grep` on the device to
# detect both. The grep runs on the device so that only one adb round-trip
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

# run_test_file: run a single test file with one retry on infrastructure
# failures (WebSocket drop during loading). These are not test-logic failures
# -- they occur when the Flutter test runner cannot attach to the app after a
# long previous test file.
# Args: $1 = path to the test file (relative to the example directory).
run_test_file() {
  local test_file="$1"
  if flutter test "$test_file" "${DEVICE_FLAG[@]+"${DEVICE_FLAG[@]}"}"; then
    return 0
  fi
  echo "  [retry] $test_file failed, cleaning up and retrying once..."
  cleanup_device
  flutter test "$test_file" "${DEVICE_FLAG[@]+"${DEVICE_FLAG[@]}"}"
}
