#!/usr/bin/env bash
# Validates that the current branch name follows the naming convention.
# Pattern: type/description-in-kebab-case
# Types: feat, feature, fix, refactor, chore, build, style, docs, release, test, ci

set -euo pipefail

BRANCH=$(git rev-parse --abbrev-ref HEAD)

# Allow main and develop branches
if [[ "$BRANCH" == "main" || "$BRANCH" == "develop" || "$BRANCH" == "HEAD" ]]; then
  exit 0
fi

PATTERN='^(feat|feature|fix|refactor|chore|build|style|docs|release|test|ci)/.+'
if ! echo "$BRANCH" | grep -qE "$PATTERN"; then
  echo "ERROR: Branch name '$BRANCH' does not follow the naming convention." >&2
  echo "  Expected: type/description  (e.g. feat/android-background-service)" >&2
  echo "  Types: feat, feature, fix, refactor, chore, build, style, docs, release, test, ci" >&2
  exit 1
fi

exit 0
