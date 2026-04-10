#!/usr/bin/env bash
# Run integration_test/callkeep_reportendcall_reasons_test.dart in isolation.
#
# Usage:
#   ./tools/run_callkeep_reportendcall_reasons.sh
#   ./tools/run_callkeep_reportendcall_reasons.sh -d <device-id>

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
EXAMPLE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=_test_lib.sh
source "$SCRIPT_DIR/_test_lib.sh"

parse_device_flags "$@"

TEST_FILE="integration_test/callkeep_reportendcall_reasons_test.dart"

cd "$EXAMPLE_DIR"

echo "  [init] Cleaning up before $TEST_FILE..."
cleanup_device

echo ""
echo "=========================================="
echo "Running: $TEST_FILE"
echo "=========================================="

run_test_file "$TEST_FILE"
STATUS=$?

cleanup_device

exit $STATUS
