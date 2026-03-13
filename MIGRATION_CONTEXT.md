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
via 11 incremental PRs. The feature isolates Android `PhoneConnectionService`
into a separate `:callkeep_core` OS process.

**Where is the full plan:** `MIGRATION_PLAN.md` (this repo root, this branch)

**Current overall phase:** Planning complete. No PRs opened yet.

**Next action to take:** Open PR-1 and PR-2 in parallel (both are independent).

---

## Branch Heads (update after every merge)

| Branch | Last known commit | Notes |
|--------|------------------|-------|
| `develop` | `344b9d5` | chore: standardize analysis_options.yaml (#148) |
| `feat/android-callkeep-core-process-migration` | `f360c46` | docs: add core decomposition principle (this file's branch) |

---

## PR Status (update after every merge)

| PR | Branch | Status | Merged commit | Date |
|----|--------|--------|--------------|------|
| PR-1 | `fix/standardize-analysis-options` | `not started` | — | — |
| PR-2 | `fix/android-signaling-and-callback-fixes` | `not started` | — | — |
| PR-3 | `docs/android-architecture-guide` | `not started` | — | — |
| PR-4 | `feat/android-utility-improvements` | `not started` | — | — |
| PR-5 | `test/android-unit-test-coverage` | `not started` | — | — |
| PR-6 | `feat/android-connection-tracker` | `not started` | — | — |
| PR-7 | `feat/android-incoming-call-tracker-integration` | `not started` | — | — |
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

---

## Open Questions (resolve before the relevant PR)

| Question | Relevant PR | Status |
|----------|-------------|--------|
| Commit `2620715` is labeled "tmp" — what exactly is in it? Confirm it is the endCall/endAllCalls fix before porting to PR-2 | PR-2 | **open** |
| `analysis_options.yaml` was already standardized in `344b9d5` on develop — does PR-1 still have delta to apply, or is it already done? | PR-1 | **open** — verify with `git diff develop -- '*/analysis_options.yaml'` |
| Do CI/CD workflow changes on the feature branch need to be included in any PR? | PR-1 or skip | **open** |
| Which exact fields were added to `CallMetaData.kt`? Verify before PR-4. | PR-4 | **open** |
| Does `StorageDelegate` on `develop` have any existing keys that could conflict with new ones from feature branch? | PR-4 | **open** |

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
