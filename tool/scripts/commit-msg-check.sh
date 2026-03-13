#!/usr/bin/env bash
# Validates commit message format.
# Rules:
#   - Must follow Conventional Commits: type(scope): description
#   - Type must be one of: feat, fix, docs, style, refactor, test, chore, build, ci, perf, revert
#   - Description must start with a lowercase letter
#   - No Cyrillic characters allowed anywhere in the message
#   - No references to AI tools (Claude, ChatGPT, Copilot, etc.)

set -euo pipefail

COMMIT_MSG_FILE="${1:-}"

if [[ -z "$COMMIT_MSG_FILE" ]]; then
  echo "Usage: $0 <commit-msg-file>" >&2
  exit 1
fi

MSG=$(cat "$COMMIT_MSG_FILE")

# Strip comment lines
CLEAN_MSG=$(echo "$MSG" | grep -v '^#' || true)

if [[ -z "$CLEAN_MSG" ]]; then
  echo "ERROR: Commit message is empty." >&2
  exit 1
fi

FIRST_LINE=$(echo "$CLEAN_MSG" | head -n1)

# Check Conventional Commits format
PATTERN='^(feat|fix|docs|style|refactor|test|chore|build|ci|perf|revert)(\(.+\))?: .+'
if ! echo "$FIRST_LINE" | grep -qE "$PATTERN"; then
  echo "ERROR: Commit message must follow Conventional Commits format." >&2
  echo "  Expected: type(scope): description" >&2
  echo "  Types: feat, fix, docs, style, refactor, test, chore, build, ci, perf, revert" >&2
  echo "  Got: $FIRST_LINE" >&2
  exit 1
fi

# Check description starts with lowercase
DESCRIPTION=$(echo "$FIRST_LINE" | sed 's/^[^:]*: //')
FIRST_CHAR="${DESCRIPTION:0:1}"
if [[ "$FIRST_CHAR" =~ [A-Z] ]]; then
  echo "ERROR: Commit description must start with a lowercase letter." >&2
  echo "  Got: $DESCRIPTION" >&2
  exit 1
fi

# Check for Cyrillic characters
if echo "$CLEAN_MSG" | grep -qP '[\x{0400}-\x{04FF}]' 2>/dev/null || \
   echo "$CLEAN_MSG" | LC_ALL=C grep -q '[А-Яа-яЁёІіЇїЄє]' 2>/dev/null; then
  echo "ERROR: Commit message must not contain Cyrillic characters." >&2
  exit 1
fi

# Check for AI tool mentions
AI_PATTERN='(Claude|ChatGPT|Copilot|Gemini|claude\.ai|openai|anthropic)'
if echo "$CLEAN_MSG" | grep -qiE "$AI_PATTERN"; then
  echo "ERROR: Commit message must not mention AI tools." >&2
  exit 1
fi

exit 0
