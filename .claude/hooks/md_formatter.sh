#!/usr/bin/env bash
# PostToolUse hook — auto-formats .md files after Edit or Write.
# Requires: markdownlint-cli2 (npm install -g markdownlint-cli2)

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

if [[ "$FILE_PATH" != *.md ]]; then
  exit 0
fi

# Resolve markdownlint-cli2: prefer PATH, fall back to known nvm location
MDLINT=""
if command -v markdownlint-cli2 &>/dev/null; then
  MDLINT="markdownlint-cli2"
elif [[ -x "$HOME/.nvm/versions/node/v24.1.0/bin/markdownlint-cli2" ]]; then
  MDLINT="$HOME/.nvm/versions/node/v24.1.0/bin/markdownlint-cli2"
else
  # Try any nvm node version
  MDLINT=$(find "$HOME/.nvm/versions/node" -name "markdownlint-cli2" -type f 2>/dev/null | head -1 || true)
fi

if [[ -n "$MDLINT" ]]; then
  "$MDLINT" --fix "$FILE_PATH" 2>/dev/null || true
fi
