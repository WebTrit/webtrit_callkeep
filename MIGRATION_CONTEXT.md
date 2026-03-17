# Migration Context — Agent Working Memory

> **Purpose:** Persistent context for migration agents. If you are starting a
> new session, read this file first, then read `MIGRATION_PLAN.md` for full
> detail. Update this file after every meaningful decision or state change.
>
> Last updated: 2026-03-17 (session 6)

---

## Quick Orientation (read in 60 seconds)

**What is happening:**
We are migrating `feat/android-callkeep-core-process-migration` into `develop`
via atomic PRs. The feature isolates Android `PhoneConnectionService`
into a separate `:callkeep_core` OS process.

**Where is the full plan:** `MIGRATION_PLAN.md` (this repo root, this branch)

**Current overall phase:** Wave 1 mostly done. Wave 2 partial.

**Next action to take:**
- PR-4d — ready to start (PR-4a merged, dependency satisfied)
- PR-4c — ready to start (independent)
- PR-5a, PR-5c, PR-5e — ready to start (independent)
- PR-6 — needs re-evaluation (ConnectionManager/ForegroundService significantly
  changed by out-of-plan commits #163–#166; read those diffs before extracting
  the feature branch version)

---

## Branch Heads (update after every merge)

| Branch | Last known commit | Notes |
|--------|------------------|-------|
| `develop` | `7037531` | test(integration): add call scenarios (#169) |
| `feat/android-callkeep-core-process-migration` | `c2c1f42` | docs: mark PR-2e open as PR #158 |

---

## PR Status (update after every merge)

| PR | Branch | Status | Merged commit | Date |
|----|--------|--------|--------------|------|
| PR-1 | ~~`fix/standardize-analysis-options`~~ | `skipped` | — | 2026-03-13 |
| PR-2a | `fix/signaling-wakelock-cache` | `merged` — PR #152 closed, superseded by #154 | `e58e456` | 2026-03-13 |
| PR-2b | `fix/signaling-logging` | `merged` — PR #151 | `efb911a` | 2026-03-13 |
| PR-2c | `fix/broadcast-receiver-context` | `merged` — PR #150 | `9306d95` | 2026-03-13 |
| PR-2d | `fix/lifecycle-null-safety` | `merged` — PR #153 | `b2b391f` | 2026-03-13 |
| PR-2e | `fix/endcall-callback-timing` | `merged` — PR #158 | `faf8de6` | 2026-03-17 |
| PR-3 | `docs/android-architecture-guide` | `not started` | — | — |
| PR-4a | `feat/android-storage-delegate-options` | `merged` — PR #157 | `85749be` | 2026-03-17 |
| PR-4b | `refactor/asset-holder-remove-flutter-assets-dependency` | `merged` — PR #156 | `ea9033b` | 2026-03-13 |
| PR-4c | `feat/android-metadata-diagnostics` | `not started` | — | — |
| PR-4d | `fix/incoming-call-notification-null-safety` | `open` — PR #170 | — | — |
| PR-5a | `test/retry-manager-test` | `not started` | — | — |
| PR-5b | `test/storage-delegate-sound-test` | `merged` — shipped inside #157 | `85749be` | 2026-03-17 |
| PR-5c | `test/is-call-phone-security-exception-test` | `not started` | — | — |
| PR-5d | `test/signaling-wakelock-test` | `merged` — shipped inside #153 | `b2b391f` | 2026-03-13 |
| PR-5e | `test/callkeep-android-options-dart` | `not started` | — | — |
| PR-6 | `feat/android-connection-tracker` | `not started` | — | — |
| PR-7a | `feat/android-foreground-service-tracker` | `not started` | — | — |
| PR-7b | `feat/android-incoming-call-cold-start` | `not started` | — | — |
| PR-8 | `feat/android-pigeon-api-additions` | `not started` | — | — |
| PR-9a | `feat/android-broadcast-transport-migration` | `not started` | — | — |
| PR-9b | `feat/android-callkeep-core-process-declaration` | `not started` | — | — |
| PR-10 | `feat/example-app-multi-line-calls` | `merged` — PR #149 (merged ahead of schedule) | `830a447` | 2026-03-13 |

---

## Out-of-Plan Commits on develop (2026-03-13 to 2026-03-17)

These were merged directly by the user outside the plan. They affect which plan
PRs still need to be done and which feature-branch code needs re-evaluation.

| Commit | PR | Files touched | Impact on plan |
|--------|-----|--------------|----------------|
| `6aa7894` | #159 | `IncomingCallHandler.kt` | foregroundServiceType fix — independent |
| `e54b78d` | #163 | `PhoneConnection.kt`, build.gradle, `PhoneConnectionTerminateTest.kt` | terminateWithCause idempotent + test |
| `e37dfc9` | #164 | `ConnectionManager.kt`, `PhoneConnection.kt`, `PhoneConnectionService.kt`, `PhoneConnectionServiceDispatcher.kt`, `ForegroundService.kt`, `IncomingCallHandler.kt`, `ConnectionManagerTest.kt`, `PhoneConnectionServiceDispatcherTest.kt` | **Major**: pending reservation, deferred-answer, directNotifiedCallIds, tearDown rewrite. PR-6 feature-branch code will conflict — must re-diff before extracting |
| `09d659b` | #165 | `PhoneConnectionService.kt`, `ConnectionManagerTest.kt` | removePending after addConnection — extends #164 |
| `f894982` | #166 | `IncomingCallService.kt`, `CallLifecycleHandler.kt`, `CallLifecycleHandlerTest.kt` | SIP BYE ordering fix — PR-7b feature-branch code will conflict |
| `edca45a` | #167 | integration tests only | test coverage for #166 |
| `19cb855` | #168 | example `build.gradle` | NDK version — no plan impact |
| `7037531` | #169 | integration tests only | extensive call scenario + background service tests |

**Key takeaway for PR-6 and PR-7b:** The production code for `ConnectionManager`,
`ForegroundService`, `IncomingCallService`, and `CallLifecycleHandler` has been
significantly rewritten in #163–#166. Do NOT blindly `git checkout feature-branch -- file`
for those files. Instead, diff the feature branch additions against the new develop
baseline and apply only the delta (MainProcessConnectionTracker itself + ConnectionsApi
switch for PR-6; cold-start handler for PR-7b).

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
| 2026-03-13 | PR-2a (#152) closed, superseded by #154 | Reviewer added improvements to resetWakeLock + Context.POWER_SERVICE; both landed as single commit `e58e456` |
| 2026-03-13 | PR-10 (example app) merged ahead of schedule as #149 | Out-of-order merge; does not block any other PR — example app is independent of library |
| 2026-03-13 | PR-5d already satisfied — test shipped inside #153 commit | `SignalingIsolateServiceWakeLockTest.kt` was included in the force-unwrap PR; no separate PR needed |
| 2026-03-13 | PR-4b refactored: remove `FlutterAssets` dependency entirely instead of adding `initForIsolatedProcess` stub | `FlutterPlugin.FlutterAssets` should not travel beyond `WebtritCallkeepPlugin`. Path `"flutter_assets/$asset"` is a fixed Flutter convention — inline it in `FlutterAssetManager`. PR #155 closed; PR #156 opened on `refactor/asset-holder-remove-flutter-assets-dependency` |
| 2026-03-17 | PR-5b satisfied — StorageDelegateSoundTest shipped inside PR-4a (#157) | Test file was included when PR-4a was merged; no separate PR needed |
| 2026-03-17 | PR-6 must be re-diffed before extraction — out-of-plan commits #163–#165 rewrote ConnectionManager + ForegroundService | Do not blindly checkout those files from feature branch; apply only the MainProcessConnectionTracker delta |
| 2026-03-17 | PR-7b must be re-diffed before extraction — out-of-plan commit #166 rewrote IncomingCallService + CallLifecycleHandler | Apply only the cold-start handler addition delta |

---

## Open Questions (resolve before the relevant PR)

| Question | Relevant PR | Status |
|----------|-------------|--------|
| CI/CD workflow changes on feature branch — include or skip? | skip | **open** |
| `isIncomingCall` field — only new field in `CallMetaData.kt`? | PR-4c | **resolved** — confirmed: `isIncomingCall: Boolean?` is the only new field |
| `RetryManager` — does it reference `StorageDelegate`? (affects PR-5a prerequisite) | PR-5a | **open** |
| After out-of-plan #163–#165 rewrites: which exact delta does PR-6 still need to add? | PR-6 | **open** — run `git diff origin/develop..feat/android-callkeep-core-process-migration -- '*.kt'` for ConnectionManager + ForegroundService before starting |
| After out-of-plan #166 rewrite: which exact delta does PR-7b still need to add? | PR-7b | **open** — same approach: diff before extracting |

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
webtrit_callkeep/                  <- main user-facing package
webtrit_callkeep_android/          <- Android implementation (Kotlin + Dart)
webtrit_callkeep_platform_interface/ <- shared API contract (models, delegates)
webtrit_callkeep_ios/              <- iOS implementation
MIGRATION_PLAN.md                  <- (this branch root) full PR plan
MIGRATION_CONTEXT.md               <- (this file) agent working memory
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
   **Exception for PR-6 and PR-7b** — see Out-of-Plan Commits section; diff first.
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
- PR-6 + PR-7b: develop baseline has changed significantly from the feature branch
  version of those files. Always diff before extracting.
