#!/usr/bin/env bash
set -e

CHROMEDRIVER_PORT=4444
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Kill any leftover chromedriver on this port
lsof -ti tcp:$CHROMEDRIVER_PORT | xargs kill -9 2>/dev/null || true
sleep 1

# Start chromedriver in background
npx chromedriver --port=$CHROMEDRIVER_PORT &
CHROMEDRIVER_PID=$!
trap "kill $CHROMEDRIVER_PID 2>/dev/null || true" EXIT
sleep 2

# Run tests
cd "$SCRIPT_DIR"
flutter drive \
  --driver=test_driver/integration_test.dart \
  --target=integration_test/all_tests.dart \
  -d chrome
