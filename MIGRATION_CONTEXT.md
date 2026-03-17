# Migration Context — Agent Working Memory

> **Purpose:** Persistent context for migration agents. If you are starting a
> new session, read this file first, then read `MIGRATION_PLAN.md` for full
> detail. Update this file after every meaningful decision or state change.
>
> Last updated: 2026-03-17 (session 12)

---

## Quick Orientation (read in 60 seconds)

**What is happening:**
We are migrating `feat/android-callkeep-core-process-migration` into `develop`
via atomic PRs. The feature isolates Android `PhoneConnectionService`
into a separate `:callkeep_core` OS process.

**Where is the full plan:** `MIGRATION_PLAN.md` (this repo root, this branch)

**Current overall phase:** Wave 1 done. Wave 2 in progress.

**Next action to take:**

- PR-6a — ready to start (`MainProcessConnectionTracker` new class, zero risk)
- PR-6b — ready to start (independent of 6a)
- PR-7b-guards — ready to start (independent, no dependencies)

---

## Branch Heads (update after every merge)

| Branch | Last known commit | Notes |
|--------|------------------|-------|
| `develop` | `401d4c8` | refactor(android): replace OutgoingCallbacksManager with per-call BroadcastReceiver in startCall (#175) |
| `feat/android-callkeep-core-process-migration` | `c2c1f42` | docs: mark PR-2e open as PR #158 |

---

## PR Status (update after every merge)

### Merged / Dropped

| PR | Branch | Status | Merged commit | Date |
|----|--------|--------|--------------|------|
| PR-1 | ~~`fix/standardize-analysis-options`~~ | `skipped` | — | 2026-03-13 |
| PR-2a | `fix/signaling-wakelock-cache` | `merged` — PR #152 closed, superseded by #154 | `e58e456` | 2026-03-13 |
| PR-2b | `fix/signaling-logging` | `merged` — PR #151 | `efb911a` | 2026-03-13 |
| PR-2c | `fix/broadcast-receiver-context` | `merged` — PR #150 | `9306d95` | 2026-03-13 |
| PR-2d | `fix/lifecycle-null-safety` | `merged` — PR #153 | `b2b391f` | 2026-03-13 |
| PR-2e | `fix/endcall-callback-timing` | `merged` — PR #158 | `faf8de6` | 2026-03-17 |
| PR-4a | `feat/android-storage-delegate-options` | `merged` — PR #157 | `85749be` | 2026-03-17 |
| PR-4b | `refactor/asset-holder-remove-flutter-assets-dependency` | `merged` — PR #156 | `ea9033b` | 2026-03-13 |
| PR-4c | `feat/android-metadata-diagnostics` | `merged` — PR #171 | `28a6148` | 2026-03-17 |
| PR-4d | `fix/incoming-call-notification-null-safety` | `merged` — PR #170 | `aa653bb` | 2026-03-17 |
| PR-5a | `test/retry-manager-test` | `dropped` — RetryManager removed in #172 | — | 2026-03-17 |
| PR-5b | `test/storage-delegate-sound-test` | `merged` — shipped inside #157 | `85749be` | 2026-03-17 |
| PR-5c | `test/is-call-phone-security-exception-test` | `dropped` — isCallPhoneSecurityException() removed in #172 | — | 2026-03-17 |
| PR-5d | `test/signaling-wakelock-test` | `merged` — shipped inside #153 | `b2b391f` | 2026-03-13 |
| PR-5e | `test/callkeep-android-options-dart` | `merged` — PR #174 | `b2f4da5` | 2026-03-17 |
| PR-7a-fix | `fix/android-foreground-service-start-call-race` | `merged` — PR #175 | `401d4c8` | 2026-03-17 |
| PR-10 | `feat/example-app-multi-line-calls` | `merged` — PR #149 (merged ahead of schedule) | `830a447` | 2026-03-13 |
| — | `refactor/remove-outgoing-call-retry` | `merged` — PR #172 (out-of-plan, supersedes PR-5a/5c) | `9be1d30` | 2026-03-17 |
| — | `fix/storage-delegate-test-isolation` | `merged` — PR #173 (out-of-plan, fixes Known Bug #2) | `84aa6f1` | 2026-03-17 |
| — | `refactor/android-split-connection-event-enums` | `merged` — PR #176 (out-of-plan, splits ConnectionPerform into CallLifecycleEvent + CallMediaEvent) | `ea74bb0` | 2026-03-17 |

### Remaining (sorted by execution order)

| PR | Branch | Status | Notes |
|----|--------|--------|-------|
| PR-6a | `feat/android-connection-tracker-class` | `not started` | New `MainProcessConnectionTracker.kt` + `MainProcessConnectionTrackerTest.kt`. Zero risk -- nothing uses it yet. |
| PR-6b | `feat/android-connection-manager-hardening` | `not started` | `ConnectionManager.kt` atomic check-and-add + `validateConnectionAddition()` + `ConnectionManagerTest.kt` + `ValidateConnectionAdditionTest.kt`. Independent of 6a. |
| PR-6c | `feat/android-connections-api-switch` | `not started` | `ConnectionsApi.kt`: switch from `ConnectionManager` to `MainProcessConnectionTracker`. Depends on PR-6a. |
| PR-7a-replay | `feat/android-foreground-service-reconnect-replay` | `not started` | Reconnect replay via `MainProcessConnectionTracker.getAll()` + `FailedCallsStoreTest.kt`. Depends on PR-6a. |
| PR-7b-guards | `fix/android-call-lifecycle-null-guards` | `not started` | `CallLifecycleHandler` null guards + suppress double notification after answer. Standalone, no dependencies. |
| PR-7b-coldstart | `feat/android-incoming-call-cold-start` | `not started` | `IncomingCallService.handleLaunch()` pre-populate tracker, `markAnswered` + `IncomingCallServiceFullScreenTest.kt`. Depends on PR-6a, PR-7a-replay. Re-diff required -- #166 rewrote `IncomingCallService` + `CallLifecycleHandler`. |
| PR-8a | `feat/android-pigeon-regenerate` | `not started` | Update `callkeep.messages.dart`, regenerate `Generated.kt` + `callkeep.pigeon.dart` + `converters.dart`. Depends on PR-7b-coldstart. |
| PR-8b | `feat/android-options-dart-api` | `not started` | `CallkeepAndroidOptions` + `webtrit_callkeep_android.dart` + `callkeep_options.dart` in platform_interface. Depends on PR-8a. |
| PR-9a | `feat/android-broadcast-transport-migration` | `not started` | Replace all direct main-process -> `PhoneConnectionService` calls with broadcasts (still single process). Depends on PR-8b. **Note:** keep 9a-broadcaster / 9a-receiver / 9a-foreground as one PR -- partial broadcast migration leaves the system in broken state between merges. |
| PR-9b | `feat/android-callkeep-core-process-declaration` | `not started` | `android:process=":callkeep_core"` in manifest + `AssetHolder.initForIsolatedProcess()`. Depends on PR-9a. |
| PR-3 | `docs/android-architecture-guide` | `not started` | 13 doc files in `webtrit_callkeep_android/docs/`. Written last -- after PR-9b. |

---

## Critical Path

```
PR-6a --> PR-6c --> PR-7a-replay --> PR-7b-coldstart --> PR-8a --> PR-8b --> PR-9a --> PR-9b --> PR-3
PR-6b (independent)
PR-7b-guards (independent)
```

---

## Out-of-Plan Commits on develop (2026-03-13 to 2026-03-17)

These were merged directly by the user outside the plan. They affect which plan
PRs still need to be done and which feature-branch code needs re-evaluation.

| Commit | PR | Files touched | Impact on plan |
|--------|-----|--------------|----------------|
| `6aa7894` | #159 | `IncomingCallHandler.kt` | foregroundServiceType fix -- independent |
| `e54b78d` | #163 | `PhoneConnection.kt`, build.gradle, `PhoneConnectionTerminateTest.kt` | terminateWithCause idempotent + test |
| `e37dfc9` | #164 | `ConnectionManager.kt`, `PhoneConnection.kt`, `PhoneConnectionService.kt`, `PhoneConnectionServiceDispatcher.kt`, `ForegroundService.kt`, `IncomingCallHandler.kt`, `ConnectionManagerTest.kt`, `PhoneConnectionServiceDispatcherTest.kt` | **Major**: pending reservation, deferred-answer, directNotifiedCallIds, tearDown rewrite. PR-6 feature-branch code will conflict -- must re-diff before extracting |
| `09d659b` | #165 | `PhoneConnectionService.kt`, `ConnectionManagerTest.kt` | removePending after addConnection -- extends #164 |
| `f894982` | #166 | `IncomingCallService.kt`, `CallLifecycleHandler.kt`, `CallLifecycleHandlerTest.kt` | SIP BYE ordering fix -- PR-7b feature-branch code will conflict |
| `edca45a` | #167 | integration tests only | test coverage for #166 |
| `19cb855` | #168 | example `build.gradle` | NDK version -- no plan impact |
| `7037531` | #169 | integration tests only | extensive call scenario + background service tests |
| `9be1d30` | #172 | `ForegroundService.kt`, retry logic removed | out-of-plan -- removes OutgoingCallbacksManager predecessor |
| `ea74bb0` | #176 | `ConnectionServicePerformBroadcaster.kt` + all callers | out-of-plan -- `ConnectionPerform` split into `CallLifecycleEvent` + `CallMediaEvent`; any future PR touching broadcast events must use the new enum names |
| `401d4c8` | #175 | `ForegroundService.kt` | out-of-plan -- `startCall()` rewritten with per-call BroadcastReceiver pattern |

**Key takeaway for PR-6 and PR-7b-coldstart:** The production code for `ConnectionManager`,
`ForegroundService`, `IncomingCallService`, and `CallLifecycleHandler` has been
significantly rewritten in #163–#166 and further in #175–#176. Do NOT blindly
`git checkout feature-branch -- file` for those files. Diff the feature branch
additions against the new develop baseline and apply only the delta.

---

## Decisions Log

Decisions already made -- do not re-litigate without strong reason.

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-03-13 | Do NOT merge deletions of `AGENTS.md`, `CONTRIBUTING.md`, `lefthook.yml`, `.claude/hooks/`, `tool/scripts/` from feature branch | These files were deleted in the feature prototype but must stay on `develop` |
| 2026-03-13 | Do NOT merge `.claude/settings.local.json` files | Local-only, should not be committed |
| 2026-03-13 | Split PR-9 into PR-9a (transport) + PR-9b (manifest) | Core decomposition principle: cross-process mechanisms work in-process; migrate transport first while still single-process, then the manifest change becomes trivial |
| 2026-03-13 | `ConnectionsApi` switches to `MainProcessConnectionTracker` in PR-6c, not PR-9 | Tracker is already the cross-process-safe read path; doing it early keeps PR-9b minimal |
| 2026-03-13 | docs/ in PR-3 should carry a "planned architecture" warning header | docs describe dual-process which isn't on develop yet when PR-3 lands |
| 2026-03-13 | PR-1 skipped -- develop reverted to flutter_lints | Both branches now use same linter; no delta to apply |
| 2026-03-13 | Commit `2620715` ("tmp") skipped -- contains only `.claude/settings.local.json` + `TODO.md` | Nothing to port; `TODO.md` stays on feature branch as internal bug audit reference |
| 2026-03-13 | PR-2d source = `c33f8df` (force-unwrap), PR-2e source = `de55f32` (endCall callbacks) | Plan had them swapped; corrected after reading actual commit diffs |
| 2026-03-13 | PR-8 kept as one unit (all Pigeon files) | Dart/Kotlin Pigeon files must always be regenerated and merged together -- split into 8a (pigeon regeneration) + 8b (Dart API wiring) |
| 2026-03-13 | PR-2a (#152) closed, superseded by #154 | Reviewer added improvements to resetWakeLock + Context.POWER_SERVICE; both landed as single commit `e58e456` |
| 2026-03-13 | PR-10 (example app) merged ahead of schedule as #149 | Out-of-order merge; does not block any other PR -- example app is independent of library |
| 2026-03-13 | PR-5d already satisfied -- test shipped inside #153 commit | `SignalingIsolateServiceWakeLockTest.kt` was included in the force-unwrap PR; no separate PR needed |
| 2026-03-13 | PR-4b refactored: remove `FlutterAssets` dependency entirely instead of adding `initForIsolatedProcess` stub | `FlutterPlugin.FlutterAssets` should not travel beyond `WebtritCallkeepPlugin`. Path `"flutter_assets/$asset"` is a fixed Flutter convention -- inline it in `FlutterAssetManager`. PR #155 closed; PR #156 opened on `refactor/asset-holder-remove-flutter-assets-dependency` |
| 2026-03-17 | PR-5b satisfied -- StorageDelegateSoundTest shipped inside PR-4a (#157) | Test file was included when PR-4a was merged; no separate PR needed |
| 2026-03-17 | PR-6 must be re-diffed before extraction -- out-of-plan commits #163-#165 rewrote ConnectionManager + ForegroundService | Do not blindly checkout those files from feature branch; apply only the MainProcessConnectionTracker delta |
| 2026-03-17 | PR-7b must be re-diffed before extraction -- out-of-plan commit #166 rewrote IncomingCallService + CallLifecycleHandler | Apply only the cold-start handler addition delta |
| 2026-03-17 | PR-9a kept as one PR (not split into 3) | Partial broadcast migration leaves system in broken intermediate state -- broadcaster sends but receiver not yet registered, or vice versa. Must land atomically. |
| 2026-03-17 | PR-6 decomposed into 6a/6b/6c, PR-7a into 7a-fix/7a-replay, PR-7b into 7b-guards/7b-coldstart, PR-8 into 8a/8b | Smaller atomic PRs, faster review cycles, independent work where possible |
| 2026-03-17 | PR-7a-fix landed as PR #175 -- replaced OutgoingCallbacksManager with per-call BroadcastReceiver | Race: performStartCall fired after timeout; fix: AtomicBoolean + per-call receiver + pendingCallCleanupsByCallId map |
| 2026-03-17 | ConnectionPerform split into CallLifecycleEvent + CallMediaEvent (PR #176) -- out-of-plan refactor | Single flat enum mixed lifecycle and media concerns; sealed interface ConnectionEvent now groups both; all future broadcast event code must use the new names |

---

## Open Questions (resolve before the relevant PR)

| Question | Relevant PR | Status |
|----------|-------------|--------|
| CI/CD workflow changes on feature branch -- include or skip? | skip | **open** |
| After out-of-plan #163-#165 + #175-#176 rewrites: which exact delta does PR-6 still need to add? | PR-6a/6b/6c | **open** -- run `git diff origin/develop..feat/android-callkeep-core-process-migration -- '*.kt'` for ConnectionManager + ForegroundService before starting |
| After out-of-plan #166 rewrite: which exact delta does PR-7b-coldstart still need to add? | PR-7b-coldstart | **open** -- same approach: diff before extracting |

---

## Known Bugs to Fix (not yet in a PR)

| Bug | Location | Description | Suggested fix |
|-----|----------|-------------|---------------|
| ~~`performStartCall` fires after timeout~~ | ~~`ForegroundService.startCall()`~~ | **Fixed in PR #175** -- replaced OutgoingCallbacksManager with per-call BroadcastReceiver + AtomicBoolean; resolved.get() guard in onReceive prevents side-effects after timeout. | -- |
| ~~`StorageDelegate.sharedPreferences` singleton not reset between tests~~ | ~~`StorageDelegateIncomingCallTest`~~ | **Fixed in PR #173** -- caching removed; `sharedPreferences(context)` now resolves fresh via `context.applicationContext` on every call. | -- |

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
2. Check **Remaining** table -- find the first `not started` PR.
3. Run `git log --oneline develop..feat/android-callkeep-core-process-migration`
   to confirm feature branch is still ahead.
4. Read the PR spec in `MIGRATION_PLAN.md` for the target PR.
5. Check **Open Questions** -- resolve any that block the target PR.
6. Create the branch from latest `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b <branch-name-from-plan>
   ```
7. Extract only the relevant files from feature branch:
   ```bash
   git checkout feat/android-callkeep-core-process-migration -- <file1> <file2>
   ```
   **Exception for PR-6 and PR-7b-coldstart** -- see Out-of-Plan Commits section; diff first.
8. Verify, push, open PR targeting `develop`.
9. After merge: update **Branch Heads**, **PR Status**, and **Open Questions**
   in this file, commit the update to `feat/android-callkeep-core-process-migration`.

---

## Gotchas & Tribal Knowledge

- **Never** `git merge feat/android-callkeep-core-process-migration` wholesale
  -- the feature branch deletes repo-wide tooling files.
- Always cherry-pick / extract individual files, never the full branch.
- When extracting Pigeon-related files, always extract the full set together:
  `pigeons/callkeep.messages.dart` + `Generated.kt` + `callkeep.pigeon.dart`
  + `converters.dart`. They must stay in sync.
- The `webtrit_callkeep/test/webtrit_callkeep_test.dart` was deleted on the
  feature branch -- do NOT carry that deletion to develop.
- Broadcast receivers now use `CallLifecycleEvent` / `CallMediaEvent` (not `ConnectionPerform`).
  Any future PR touching broadcast event registration or dispatch must use the new enum names.
- PR-6 + PR-7b-coldstart: develop baseline has changed significantly from the feature
  branch version of those files (#163-#166 + #175). Always diff before extracting.
- PR-9a must land as one atomic PR -- partial broadcast migration breaks the system.
