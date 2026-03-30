#!/usr/bin/env bash
# Orchestrator: run all integration test files on a device or in Chrome (web).
#
# Android (default):
#   Each file is run in isolation via its dedicated script under tools/.
#   Each script force-stops the app and waits for both OS processes
#   (main + :callkeep_core) to be confirmed dead before and after the test,
#   preventing duplicate Telecom connections and wedged state between files.
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

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CHROMEDRIVER_PORT=4444

# ---- Web mode ----------------------------------------------------------------
if [[ "${1:-}" == "--web" ]]; then
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

# ---- Android mode ------------------------------------------------------------
DEVICE_ARGS=()
if [[ "${1:-}" == "-d" && -n "${2:-}" ]]; then
  DEVICE_ARGS=(-d "$2")
fi

# Individual test scripts under tools/, in run order.
# background_services runs last: it starts IncomingCallService which Android
# may restart after force-stop, and running it last reduces the risk of
# ForegroundServiceDidNotStartInTimeException crashes affecting other files.
TEST_SCRIPTS=(
  tools/run_callkeep_client_scenarios.sh
  tools/run_callkeep_connections.sh
  tools/run_callkeep_delegate_edge_cases.sh
  tools/run_callkeep_foreground_service.sh
  tools/run_callkeep_lifecycle.sh
  tools/run_callkeep_reportendcall_reasons.sh
  tools/run_callkeep_state_machine.sh
  tools/run_callkeep_stress.sh
  tools/run_callkeep_call_scenarios.sh
  tools/run_callkeep_background_services.sh
)

PASS=0
FAIL=0
FAILED_SCRIPTS=()

for SCRIPT in "${TEST_SCRIPTS[@]}"; do
  if "$SCRIPT_DIR/$SCRIPT" "${DEVICE_ARGS[@]+"${DEVICE_ARGS[@]}"}"; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
    FAILED_SCRIPTS+=("$SCRIPT")
  fi
done

echo ""
echo "=========================================="
echo "Results: $PASS passed, $FAIL failed"
if [[ ${#FAILED_SCRIPTS[@]} -gt 0 ]]; then
  echo "Failed:"
  for F in "${FAILED_SCRIPTS[@]}"; do
    echo "  - $F"
  done
  exit 1
fi
echo "All integration test files passed."
