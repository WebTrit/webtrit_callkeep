# Migration Context — Agent Working Memory

> **Purpose:** Persistent context for migration agents. If you are starting a
> new session, read this file first, then read `MIGRATION_PLAN.md` for full
> detail. Update this file after every meaningful decision or state change.
>
> Last updated: 2026-03-13

---

## Quick Orientation (read in 60 seconds)

**What is happening:**
We are migrating `feat/android-callkeep-core-process-migration` into `develop`
via 23 atomic PRs. The feature isolates Android `PhoneConnectionService`
into a separate `:callkeep_core` OS process.

**Where is the full plan:** `MIGRATION_PLAN.md` (this repo root, this branch)

**Current overall phase:** Planning complete. No PRs opened yet.

**Next action to take:** Wave 1 — open all of these simultaneously:
PR-2a, PR-2b, PR-2c, PR-2d, PR-2e, PR-3, PR-4a, PR-4b, PR-4d
(PR-1 skipped — develop reverted to flutter_lints, no delta).

---

## Branch Heads (update after every merge)

| Branch | Last known commit | Notes |
|--------|------------------|-------|
| `develop` | `344b9d5` | chore: standardize analysis_options.yaml (#148) |
| `feat/android-callkeep-core-process-migration` | `fb657b3` | docs: fix commit map |

---

## PR Status (update after every merge)

| PR | Branch | Status | Merged commit | Date |
|----|--------|--------|--------------|------|
| PR-1 | ~~`fix/standardize-analysis-options`~~ | `skipped` | — | 2026-03-13 |
| PR-2a | `fix/signaling-wakelock-cache` | `open` — PR #152 | — | — |
| PR-2b | `fix/signaling-logging` | `open` — PR #151 | — | — |
| PR-2c | `fix/broadcast-receiver-context` | `open` — PR #150 | — | — |
| PR-2d | `fix/lifecycle-null-safety` | `open` — PR #153 | — | — |
| PR-2e | `fix/endcall-callback-timing` | `not started` | — | — |
| PR-3 | `docs/android-architecture-guide` | `not started` | — | — |
| PR-4a | `feat/android-storage-delegate-options` | `not started` | — | — |
| PR-4b | `feat/android-asset-holder-isolated` | `not started` | — | — |
| PR-4c | `feat/android-metadata-diagnostics` | `not started` | — | — |
| PR-4d | `fix/incoming-call-notification-null-safety` | `not started` | — | — |
| PR-5a | `test/retry-manager-test` | `not started` | — | — |
| PR-5b | `test/storage-delegate-sound-test` | `not started` | — | — |
| PR-5c | `test/is-call-phone-security-exception-test` | `not started` | — | — |
| PR-5d | `test/signaling-wakelock-test` | `not started` | — | — |
| PR-5e | `test/callkeep-android-options-dart` | `not started` | — | — |
| PR-6 | `feat/android-connection-tracker` | `not started` | — | — |
| PR-7a | `feat/android-foreground-service-tracker` | `not started` | — | — |
| PR-7b | `feat/android-incoming-call-cold-start` | `not started` | — | — |
| PR-8 | `feat/android-pigeon-api-additions` | `not started` | — | — |
| PR-9a | `feat/android-broadcast-transport-migration` | `not started` | — | — |
| PR-9b | `feat/android-callkeep-core-process-declaration` | `not started` | — | — |
| PR-10 | `feat/example-app-multi-line-calls` | `not started` | — | — |

---

## Decisions Log

Decisions already made — do not re-litigate without strong reason.

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-03-13 | Do NOT merge deletions of `AGENTS.md`, `CONTRIBUTING.md`, `lefthook.yml`, `.claude/hooks/`, `tool/scripts/` from feature branch | These files were deleted in the feature prototype but must stay on `develop` |
| 2026-03-13 | Do NOT merge `.claude/settings.local.json` files | Local-only, should not be committed |
| 2026-03-13 | Split PR-9 into PR-9a (transport) + PR-9b (manifest) | Core decomposition principle: cross-process mechanisms work in-process; migrate transport first while still single-process, then the manifest change becomes trivial |
| 2026-03-13 | `ConnectionsApi` switches to `MainProcessConnectionTracker` in PR-6, not PR-9 | Tracker is already the cross-process-safe read path; doing it early keeps PR-9b minimal |
| 2026-03-13 | docs/ in PR-3 should carry a "planned architecture" warning header | docs describe dual-process which isn't on develop yet when PR-3 lands |
| 2026-03-13 | PR-1 skipped — develop reverted to flutter_lints | Both branches now use same linter; no delta to apply |
| 2026-03-13 | Commit `2620715` ("tmp") skipped — contains only `.claude/settings.local.json` + `TODO.md` | Nothing to port; `TODO.md` stays on feature branch as internal bug audit reference |
| 2026-03-13 | PR-2d source = `c33f8df` (force-unwrap), PR-2e source = `de55f32` (endCall callbacks) | Plan had them swapped; corrected after reading actual commit diffs |
| 2026-03-13 | Split PR-2 into 2a-2e (one fix = one PR) | Smaller scope = faster review, easier revert, cleaner history |
| 2026-03-13 | Split PR-4 into 4a-4d (one utility concern = one PR) | Same principle — additive changes are independent, split reduces risk |
| 2026-03-13 | Split PR-5 into 5a-5e (one test file = one PR) | Tests are fully independent; splitting enables parallel merges |
| 2026-03-13 | Split PR-7 into 7a (ForegroundService) + 7b (IncomingCallService) | Different components, different risk profiles, different reviewers |
| 2026-03-13 | PR-6 kept as one unit (Tracker + ConnectionManager) | Tightly coupled — tracker IS what manager reads; splitting would leave dangling dependencies |
| 2026-03-13 | PR-8 kept as one unit (all Pigeon files) | Dart/Kotlin Pigeon files must always be regenerated and merged together |

---

## Open Questions (resolve before the relevant PR)

| Question | Relevant PR | Status |
|----------|-------------|--------|
| ~~Commit `2620715` content~~ | PR-2e | **resolved** — only settings + TODO.md, skip |
| CI/CD workflow changes on feature branch — include or skip? | skip | **open** |
| Exact fields added to `CallMetaData.kt`? | PR-4c | **open** — run `git show 6af63bd -- '*CallMetaData.kt'` |
| `StorageDelegate` existing keys on develop — conflict risk? | PR-4a | **open** — read current `StorageDelegate.kt` on develop |
| `RetryManager` — does it reference `StorageDelegate`? (affects PR-5a prerequisite) | PR-5a | **open** |

---

## Key Files (source of truth locations)

| What | File | Branch |
|------|------|--------|
| Full PR breakdown & specs | `MIGRATION_PLAN.md` | `feat/android-callkeep-core-process-migration` |
| This context / agent memory | `MIGRATION_CONTEXT.md` | `feat/android-callkeep-core-process-migration` |
| Android architecture source | `webtrit_callkeep_android/docs/architecture.md` | `feat/android-callkeep-core-process-migration` |
| IPC event table | `webtrit_callkeep_android/docs/ipc.md` | `feat/android-callkeep-core-process-migration` |
| TODO / known issues | `webtrit_callkeep_android/TODO.md` | `feat/android-callkeep-core-process-migration` |

---

## Repo Layout Reminder

```
webtrit_callkeep/                  ← main user-facing package
webtrit_callkeep_android/          ← Android implementation (Kotlin + Dart)
webtrit_callkeep_platform_interface/ ← shared API contract (models, delegates)
webtrit_callkeep_ios/              ← iOS implementation
MIGRATION_PLAN.md                  ← (this branch root) full PR plan
MIGRATION_CONTEXT.md               ← (this file) agent working memory
```

---

## How to Resume Work in a New Session

1. Read this file top-to-bottom (2 min).
2. Check **PR Status** table — find the first `not started` PR.
3. Run `git log --oneline develop..feat/android-callkeep-core-process-migration`
   to confirm feature branch is still ahead.
4. Read the PR spec in `MIGRATION_PLAN.md` for the target PR.
5. Check **Open Questions** — resolve any that block the target PR.
6. Create the branch from latest `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b <branch-name-from-plan>
   ```
7. Extract only the relevant files from feature branch:
   ```bash
   git checkout feat/android-callkeep-core-process-migration -- <file1> <file2>
   ```
8. Verify, push, open PR targeting `develop`.
9. After merge: update **Branch Heads**, **PR Status**, and **Open Questions**
   in this file, commit the update to `feat/android-callkeep-core-process-migration`.

---

## Gotchas & Tribal Knowledge

- **Never** `git merge feat/android-callkeep-core-process-migration` wholesale
  — the feature branch deletes repo-wide tooling files.
- Always cherry-pick / extract individual files, never the full branch.
- When extracting Pigeon-related files, always extract the full set together:
  `pigeons/callkeep.messages.dart` + `Generated.kt` + `callkeep.pigeon.dart`
  + `converters.dart`. They must stay in sync.
- `analysis_options.yaml` was already touched in `344b9d5` on develop — diff
  carefully before PR-1 to avoid duplicating a change that's already there.
- The `webtrit_callkeep/test/webtrit_callkeep_test.dart` was deleted on the
  feature branch — do NOT carry that deletion to develop.
- Broadcast receivers for `ConnectionPerform` events use `applicationContext`
  — using an Activity context will silently fail cross-process.
