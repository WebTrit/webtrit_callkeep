# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See **[AGENTS.md](AGENTS.md)** for build/test commands, code standards, and architecture overview.

## Package-level documentation

Each platform package has its own AGENTS.md with package-specific guidance:

- [`webtrit_callkeep_platform_interface/AGENTS.md`](webtrit_callkeep_platform_interface/AGENTS.md) — shared API contract, models, delegates
- [`webtrit_callkeep_android/AGENTS.md`](webtrit_callkeep_android/AGENTS.md) — Android dual-process architecture, services, Pigeon, background modes
- [`webtrit_callkeep_ios/AGENTS.md`](webtrit_callkeep_ios/AGENTS.md) — iOS CallKit/PushKit integration

## Git / PR conventions

- Commit messages: imperative mood, describe what and why. No AI/tool mentions.
- Fix branches created from base branch; PRs target base branch (not `main`).
- Always `git pull` on base branch before creating a fix branch.
- After creating a PR, switch back to `feat/android-callkeep-core-process-migration`.
