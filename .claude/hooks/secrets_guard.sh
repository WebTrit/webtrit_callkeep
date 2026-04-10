#!/usr/bin/env bash
# PreToolUse hook — blocks access to secret/credential files.

set -euo pipefail

INPUT=$(cat)

FILE_PATH=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))" 2>/dev/null || true)

if [[ -z "$FILE_PATH" ]]; then
  exit 0
fi

BASENAME=$(basename "$FILE_PATH")

BLOCKED_PATTERNS=(".env" ".jks" ".keystore" ".p12" ".pem" ".key" ".p8")

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if [[ "$BASENAME" == *"$pattern"* ]]; then
    echo "ERROR: Access to secret/credential file blocked: $FILE_PATH" >&2
    exit 1
  fi
done
