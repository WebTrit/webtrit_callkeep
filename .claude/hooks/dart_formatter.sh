#!/usr/bin/env bash
# PostToolUse hook — auto-formats .dart files after Edit or Write.
# Skips generated files (*.g.dart, *.pigeon.dart, *.freezed.dart).

set -euo pipefail

# Read tool input from stdin
INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_name',''))" 2>/dev/null || true)

if [[ "$TOOL_NAME" != "Edit" && "$TOOL_NAME" != "Write" ]]; then
  exit 0
fi

FILE_PATH=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))" 2>/dev/null || true)

if [[ -z "$FILE_PATH" ]]; then
  exit 0
fi

# Only process .dart files
if [[ "$FILE_PATH" != *.dart ]]; then
  exit 0
fi

# Skip generated files
BASENAME=$(basename "$FILE_PATH")
if [[ "$BASENAME" == *.g.dart || "$BASENAME" == *.pigeon.dart || "$BASENAME" == *.freezed.dart || "$BASENAME" == *.gr.dart ]]; then
  exit 0
fi

# Skip if file doesn't exist
if [[ ! -f "$FILE_PATH" ]]; then
  exit 0
fi

dart format --line-length 120 "$FILE_PATH" 2>/dev/null || true
