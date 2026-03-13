#!/usr/bin/env bash
# PostToolUse hook — ensures every written file ends with a newline.

set -euo pipefail

INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_name',''))" 2>/dev/null || true)

if [[ "$TOOL_NAME" != "Edit" && "$TOOL_NAME" != "Write" ]]; then
  exit 0
fi

FILE_PATH=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('file_path',''))" 2>/dev/null || true)

if [[ -z "$FILE_PATH" || ! -f "$FILE_PATH" ]]; then
  exit 0
fi

# Skip binary files
if ! file "$FILE_PATH" | grep -q "text"; then
  exit 0
fi

# Add newline at EOF if missing
if [[ -s "$FILE_PATH" ]] && [[ "$(tail -c1 "$FILE_PATH" | wc -l)" -eq 0 ]]; then
  echo "" >> "$FILE_PATH"
fi
