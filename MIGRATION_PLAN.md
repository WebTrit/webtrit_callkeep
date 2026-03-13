# Migration Plan: `feat/android-callkeep-core-process-migration` → `develop`

> **Living document.** Update after every PR merge.
> Last updated: 2026-03-13

---

## Overview

The feature branch implements Android dual-process architecture, moving
`PhoneConnectionService` (Telecom `ConnectionService`) from the main app
process into an isolated `:callkeep_core` process. This prevents stale Binder
references and zombie Telecom states when the main process crashes.

**Total scope:** 127 files, +7 751 / −3 185 lines

**Goal:** Extract this work into atomic, independently-reviewable PRs.
One concern = one PR. Smallest possible scope at each step.

---

## Core Decomposition Principle

> **Cross-process-compatible mechanisms work in-process too.
> In-process-only mechanisms do NOT work cross-process.**

| Mechanism | In single process | Cross-process |
|-----------|:-----------------:|:-------------:|
| Direct object calls | YES | NO |
| Shared memory / singletons | YES | NO |
| Local broadcasts (`sendBroadcast`) | YES | YES |
| `BroadcastReceiver` with `exported=false` | YES | YES |

**Strategic implication:** Migrate the transport layer to broadcasts while
still running in a single process (PR-9a). Then moving `PhoneConnectionService`
to `:callkeep_core` in `AndroidManifest.xml` is a near-mechanical step (PR-9b).

```
Direct calls  ->  Broadcasts (still 1 process)  ->  2 processes
   today           PR-9a (safe, testable)          PR-9b (manifest only)
```

**Where the principle guides each PR:**

| PR | Application |
|----|-------------|
| PR-6 | `ConnectionsApi` queries `MainProcessConnectionTracker` (cross-process-safe read path) instead of `ConnectionManager` directly |
| PR-7a/7b | `ForegroundService` and `IncomingCallService` use broadcast-compatible event replay |
| PR-9a | Replace ALL remaining direct main-process -> `PhoneConnectionService` calls with broadcasts (still single process) |
| PR-9b | One manifest line: `android:process=":callkeep_core"` — transport already ready |

---

## Changes on feature branch NOT to migrate

The feature branch deleted repo-wide files that must stay on `develop`:

| File / Dir | Decision |
|-----------|----------|
| `AGENTS.md` (root + all packages) | SKIP — keep on develop |
| `CONTRIBUTING.md` | SKIP |
| `lefthook.yml` | SKIP |
| `cspell.json` | SKIP |
| `.claude/settings.json` | SKIP |
| `.claude/hooks/` (all scripts) | SKIP |
| `tool/scripts/` | SKIP |
| `webtrit_callkeep/CLAUDE.md`, `webtrit_callkeep_platform_interface/CLAUDE.md` | SKIP |
| `webtrit_callkeep/test/webtrit_callkeep_test.dart` | SKIP — do not delete |
| `.claude/settings.local.json` (3 files) | SKIP — local only |
| `.github/workflows/` changes | EVALUATE per PR — likely skip |

---

## Migration Sequence

```
PR-1    SKIPPED — develop reverted to flutter_lints, no delta

PR-2a   fix: WakeLock cache in SignalingIsolateService
PR-2b   fix: println -> Log.d in SignalingIsolateService
PR-2c   fix: consistent context for broadcast receiver unregister
PR-2d   fix: remove force-unwrap on latestLifecycleActivityEvent
PR-2e   fix: endCall/endAllCalls callbacks after broadcast confirmation

PR-3    docs: android architecture (docs/ directory, 13 files)

PR-4a   feat: StorageDelegate persistent options (ringback, full-screen flag)
PR-4b   feat: AssetHolder.initForIsolatedProcess()
PR-4c   feat: CallMetaData new fields + CallDiagnostics cross-process support
PR-4d   fix:  IncomingCallNotificationBuilder null guards + option checks

PR-5a   test: RetryManagerTest
PR-5b   test: StorageDelegateSoundTest
PR-5c   test: IsCallPhoneSecurityExceptionTest
PR-5d   test: SignalingIsolateServiceWakeLockTest
PR-5e   test: callkeep_android_options_test.dart (Dart side)

PR-6    feat: MainProcessConnectionTracker + ConnectionManager hardening
          (includes ConnectionManagerTest, ValidateConnectionAdditionTest,
           MainProcessConnectionTrackerTest, ConnectionsApi switch)

PR-7a   feat: ForegroundService tracker integration + retry logic
          (includes FailedCallsStoreTest)
PR-7b   feat: IncomingCallService cold-start + full-screen option
          (includes IncomingCallServiceFullScreenTest, CallLifecycleHandler)

PR-8    feat: Pigeon API additions (incomingCallFullScreen option +
          CALL_ID_ALREADY_EXISTS_AND_ANSWERED enum) — regenerate bindings

PR-9a   refactor: migrate transport to broadcasts (still single process)
PR-9b   feat: android:process=":callkeep_core" (process declaration)

PR-10   feat: example app multi-line calls rewrite
```

**Total: 23 PRs**

**Parallel tracks (can open simultaneously):**
- PR-2a through PR-2e — all independent of each other
- PR-3 — independent of everything
- PR-4a through PR-4d — independent of each other
- PR-5a through PR-5e — open after their target PR-4x lands

---

## PR Specifications

---

### PR-1 — SKIPPED

**Status:** `[x] skipped`

**Reason:** `develop` was reverted to `flutter_lints`. Both branches now use
the same linter config — no delta to apply.

---

### PR-2a — fix: cache WakeLock in SignalingIsolateService

**Branch:** `fix/signaling-wakelock-cache`
**Target:** `develop`
**Source commit:** `169604d`
**Risk:** Very low

**File:** `SignalingIsolateService.kt`
**Change:** Cache `WakeLock` instance to prevent leak on repeated acquire calls.

**Validate:**
- [ ] `SignalingIsolateServiceWakeLockTest` passes (can ship with PR-5d or include here)

**Status:** `[ ] not started`

---

### PR-2b — fix: replace println with Log.d in SignalingIsolateService

**Branch:** `fix/signaling-logging`
**Target:** `develop`
**Source commit:** `56bf18e`
**Risk:** Zero

**File:** `SignalingIsolateService.kt`
**Change:** Replace `println(...)` calls with `Log.d(TAG, ...)`.

**Validate:** Build passes.

**Status:** `[ ] not started`

---

### PR-2c — fix: consistent context for broadcast receiver unregister

**Branch:** `fix/broadcast-receiver-context`
**Target:** `develop`
**Source commit:** `62a1084`
**Risk:** Low

**Files:** Service files that register `BroadcastReceiver` (verify exact files from commit diff).
**Change:** Use the same `context` instance for `registerReceiver` and
`unregisterReceiver` to avoid `IllegalArgumentException` on unregister.

**Validate:**
- [ ] No crash on service stop/restart cycle

**Status:** `[ ] not started`

---

### PR-2d — fix: remove force-unwrap on latestLifecycleActivityEvent

**Branch:** `fix/lifecycle-null-safety`
**Target:** `develop`
**Source commit:** `c33f8df`
**Risk:** Low

**File:** `ForegroundService.kt` (or wherever `latestLifecycleActivityEvent` is accessed).
**Change:** Replace `!!` with safe call / null check to prevent NPE crash.

**Validate:**
- [ ] No NPE in lifecycle event handling path

**Status:** `[ ] not started`

---

### PR-2e — fix: resolve endCall/endAllCalls callbacks after broadcast confirmation

**Branch:** `fix/endcall-callback-timing`
**Target:** `develop`
**Source commit:** `de55f32`
**Risk:** Medium — touches call end flow

**Files:** `ConnectionManager.kt`, `PhoneConnectionServiceDispatcher.kt`.
**Change:** Callbacks for `endCall`/`endAllCalls` were resolved immediately on
API call — race condition where callback fires before Telecom confirms
disconnection. Fixed with a one-shot `BroadcastReceiver` waiting for
`HungUp`/`DeclineCall` events. `endCall()` filters by `callId`;
`endAllCalls()` uses `AtomicInteger` countdown. 5-second timeout prevents
indefinite hang.

**Validate:**
- [ ] End call from Flutter side -> callback fires after Telecom confirms
- [ ] End all calls -> all callbacks resolve, no timeout fires on normal flow

**Status:** `[ ] not started`

---

### ~~PR-2e-orig~~ — commit `2620715` — SKIP

**Reason:** Contains only `.claude/settings.local.json` (3 files) and
`TODO.md` (44-item bug audit). Neither should be ported:
- `.claude/settings.local.json` — local only
- `TODO.md` — useful reading but not a deliverable PR; keep on feature branch
  as internal reference

---

### PR-3 — docs: Android architecture guide

**Branch:** `docs/android-architecture-guide`
**Target:** `develop`
**Risk:** Zero

**Files to add (`webtrit_callkeep_android/docs/`):**

```
README.md                    navigation hub
architecture.md              process model, component map
call-triggers.md             4 initiation paths
call-flows.md                end-to-end flow diagrams
ipc.md                       ConnectionPerform event table
foreground-service.md        lifecycle, retry, hot-restart
phone-connection-service.md  state machine, dispatcher
signaling-isolate-service.md boot resilience
incoming-call-service.md     push notification path
active-call-service.md       role, service types
common-utilities.md          StorageDelegate, RetryManager
notifications.md             channels, builders, permissions
pigeon-api.md                full API reference
```

**Also:** `webtrit_callkeep_android/README.md` and `CLAUDE.md` updates.

**Note:** Add header to architecture-specific docs:
> This document describes architecture being migrated via phased PRs.
> Some sections describe upcoming state not yet on develop.

**Status:** `[ ] not started`

---

### PR-4a — feat: StorageDelegate persistent options

**Branch:** `feat/android-storage-delegate-options`
**Target:** `develop`
**Risk:** Low — additive new keys, no existing keys changed

**File:** `StorageDelegate.kt`
**Change:** Persistent storage for ringback sound path and `incomingCallFullScreen` flag.

**Validate:**
- [ ] Existing StorageDelegate keys unaffected
- [ ] New keys readable after process kill/restart

**Status:** `[ ] not started`

---

### PR-4b — feat: AssetHolder.initForIsolatedProcess()

**Branch:** `feat/android-asset-holder-isolated`
**Target:** `develop`
**Risk:** Low — new static method, no existing code changed

**File:** `AssetHolder.kt`
**Change:** Add `initForIsolatedProcess(context)` — provides minimal
`FlutterAssets` using `flutter_assets/<name>` path convention for processes
without a `FlutterEngine`.

**Note:** The call site (`PhoneConnectionService.onCreate()`) arrives in PR-9b.
Adding the method now is safe — unused until then.

**Validate:**
- [ ] Existing `AssetHolder` usage unaffected
- [ ] Kotlin compiles without warnings

**Status:** `[ ] not started`

---

### PR-4c — feat: CallMetaData new fields + CallDiagnostics cross-process support

**Branch:** `feat/android-metadata-diagnostics`
**Target:** `develop`
**Risk:** Low — additive fields
**Prerequisite:** PR-4a (if new fields use StorageDelegate keys)

**Files:**
- `CallMetaData.kt` — new fields (verify exact fields: `git show 6af63bd -- '*CallMetaData.kt'`)
- `CallDiagnostics.kt` — additive methods for cross-process diagnostic queries

**Validate:**
- [ ] Existing `CallMetaData` consumers compile without changes
- [ ] `CallDiagnostics` existing methods unaffected

**Status:** `[ ] not started`

---

### PR-4d — fix: IncomingCallNotificationBuilder null guards

**Branch:** `fix/incoming-call-notification-null-safety`
**Target:** `develop`
**Risk:** Low

**File:** `IncomingCallNotificationBuilder.kt`
**Change:** Null-safe guards and option checks before accessing notification config.

**Validate:**
- [ ] Notification builds without crash when optional fields are null

**Status:** `[ ] not started`

---

### PR-5a — test: RetryManagerTest

**Branch:** `test/retry-manager-test`
**Target:** `develop`
**Risk:** Zero
**Prerequisite:** PR-4a (RetryManager may use StorageDelegate)

**File:** `RetryManagerTest.kt` (289 lines)

**build.gradle addition (once, shared across PR-5x):**
```gradle
testImplementation("androidx.test:core:1.5.0")
testImplementation("org.robolectric:robolectric:4.11.1")
```

**Status:** `[ ] not started`

---

### PR-5b — test: StorageDelegateSoundTest

**Branch:** `test/storage-delegate-sound-test`
**Target:** `develop`
**Risk:** Zero
**Prerequisite:** PR-4a merged

**File:** `StorageDelegateSoundTest.kt` (77 lines) — tests persistent flags.

**Status:** `[ ] not started`

---

### PR-5c — test: IsCallPhoneSecurityExceptionTest

**Branch:** `test/is-call-phone-security-exception-test`
**Target:** `develop`
**Risk:** Zero

**File:** `IsCallPhoneSecurityExceptionTest.kt` (50 lines)

**Status:** `[ ] not started`

---

### PR-5d — test: SignalingIsolateServiceWakeLockTest

**Branch:** `test/signaling-wakelock-test`
**Target:** `develop`
**Risk:** Zero
**Note:** Can be combined with PR-2a if preferred (test ships alongside its fix).

**File:** `SignalingIsolateServiceWakeLockTest.kt` (61 lines)

**Status:** `[ ] not started`

---

### PR-5e — test: callkeep_android_options_test (Dart)

**Branch:** `test/callkeep-android-options-dart`
**Target:** `develop`
**Risk:** Zero
**Prerequisite:** PR-4a (tests the new options fields)

**File:** `webtrit_callkeep_android/test/callkeep_android_options_test.dart` (55 lines)

**Status:** `[ ] not started`

---

### PR-6 — feat: MainProcessConnectionTracker + ConnectionManager hardening

**Branch:** `feat/android-connection-tracker`
**Target:** `develop`
**Risk:** Medium
**Prerequisite:** PR-4a, PR-4b, PR-4c merged

**Why kept together:** `MainProcessConnectionTracker` is what
`ConnectionManager.validateConnectionAddition()` reads — they form one
coherent unit of logic. Splitting them would leave the tracker with no
consumers or the manager with a missing dependency.

**New class: `MainProcessConnectionTracker.kt`**
- Thread-safe map: `callId -> (metadata, state, answered)`
- Methods: `add`, `addWithState`, `updateState`, `markAnswered`, `isAnswered`,
  `remove`, `exists`, `get`, `getAll`
- On `develop`: starts empty, nothing populates it yet — safe

**`ConnectionManager.kt` changes:**
- Atomic check-and-add for concurrent call creation
- `validateConnectionAddition()` checks `isAnswered(callId)` first ->
  returns `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` if true
- On `develop` this path never fires (nothing calls `markAnswered` yet) — safe

**`ConnectionsApi.kt`:**
- Switch from direct `ConnectionManager` queries to `MainProcessConnectionTracker`
  (cross-process-safe read path; prepares for PR-9b)

**New tests:**
- `MainProcessConnectionTrackerTest.kt` (252 lines)
- `ConnectionManagerTest.kt` (280 lines)
- `ValidateConnectionAdditionTest.kt` (167 lines)

**Validate:**
- [ ] All 3 test files pass
- [ ] Incoming call -> answer: no regression
- [ ] Cold-start not broken (new path just won't activate yet)

**Status:** `[ ] not started`

---

### PR-7a — feat: ForegroundService tracker integration + retry logic

**Branch:** `feat/android-foreground-service-tracker`
**Target:** `develop`
**Risk:** Medium
**Prerequisite:** PR-6 merged

**`ForegroundService.kt` changes:**
- On reconnect: iterate `MainProcessConnectionTracker.getAll()` and replay
  missed `ConnectionPerform` events
- Retry/timeout logic improvements from feature branch
- Note: Full IPC binding to `:callkeep_core` is NOT part of this PR.
  That arrives in PR-9a/9b. This PR covers the in-process case only.

**New tests:** `FailedCallsStoreTest.kt` (97 lines)

**Validate:**
- [ ] Hot-restart: reconnects and replays state correctly
- [ ] Retry logic does not cause duplicate events

**Status:** `[ ] not started`

---

### PR-7b — feat: IncomingCallService cold-start + full-screen option

**Branch:** `feat/android-incoming-call-cold-start`
**Target:** `develop`
**Risk:** Medium-High — touches call notification path
**Prerequisite:** PR-6, PR-7a, PR-4a merged

**`IncomingCallService.kt` changes:**
- `handleLaunch()`: pre-populate `MainProcessConnectionTracker` when
  `incomingCallFullScreen == true` (enables cold-start recovery)
- `performAnswerCall()`: call `MainProcessConnectionTracker.markAnswered(callId)`
- Null guards on `performEndCall`

**`CallLifecycleHandler.kt` changes:**
- Guards for null connection state
- Suppress notification after answer (avoid double notification)

**New tests:** `IncomingCallServiceFullScreenTest.kt` (165 lines)

**Validate:**
- [ ] Full incoming call flow (foreground + background)
- [ ] Cold-start: kill app mid-call -> relaunch -> state recovered
- [ ] Full-screen notification on lock screen still works
- [ ] No double notification after answer

**Status:** `[ ] not started`

---

### PR-8 — feat: Pigeon API additions

**Branch:** `feat/android-pigeon-api-additions`
**Target:** `develop`
**Risk:** Medium — API surface change, generated code must stay in sync
**Prerequisite:** PR-7b merged

**Always regenerate all Pigeon files together:**
```bash
cd webtrit_callkeep_android
dart run pigeon --input pigeons/callkeep.messages.dart
```

**Changes:**

| File | Change |
|------|--------|
| `pigeons/callkeep.messages.dart` | Add `incomingCallFullScreen` to `PCallkeepAndroidOptions`; add `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` enum value |
| `Generated.kt` | Regenerated |
| `callkeep.pigeon.dart` | Regenerated |
| `converters.dart` | Add conversion for new fields/enum |
| `webtrit_callkeep_android.dart` | Wire new option |
| `callkeep_options.dart` (platform_interface) | Add `incomingCallFullScreen` to `CallkeepAndroidOptions` |
| `webtrit_callkeep/lib/src/callkeep.dart` | Pass through new option |

**Validate:**
- [ ] `flutter analyze lib test` — all packages
- [ ] `dart format --line-length 80 --set-exit-if-changed lib test`
- [ ] `flutter test` — `webtrit_callkeep` and `webtrit_callkeep_android`
- [ ] Pigeon Dart <-> Kotlin types in sync

**Status:** `[ ] not started`

---

### PR-9a — refactor: migrate transport to broadcasts (single process)

**Branch:** `feat/android-broadcast-transport-migration`
**Target:** `develop`
**Risk:** Medium — replaces call paths, but single-process so fully debuggable
**Prerequisite:** PR-1 through PR-8 all merged

**The principle in action:** Replace every direct call from the main process
into `PhoneConnectionService`/`ConnectionManager` with
`ConnectionServicePerformBroadcaster.send(event)` + `BroadcastReceiver`.
`PhoneConnectionService` still runs in the main process.
Broadcasts are delivered in-process at nanosecond latency.
Behavior is identical — but transport is cross-process-ready.

**`ConnectionServicePerformBroadcaster.kt`:**
- Use `applicationContext` for all `sendBroadcast` calls (required for
  cross-process delivery in PR-9b)
- All results flow back as separate broadcast events (no synchronous returns)

**`ForegroundService.kt`:**
- Remove all direct calls to `PhoneConnectionService` methods
- Register `BroadcastReceiver` for `ConnectionPerform` result events
- Send via `ConnectionServicePerformBroadcaster` instead

**`PhoneConnectionService.kt`:**
- Register `BroadcastReceiver` for incoming command events
- Remove any direct references to main-process singletons
- Results sent back via broadcasts

**`PhoneConnection.kt`:**
- Remaining direct main-process callbacks -> broadcasts
- Endpoint change race condition guard (API 34+)

**`PhoneConnectionServiceDispatcher.kt`:**
- Full dispatch through broadcast events only

**Validate:**
- [ ] Outgoing call: identical behavior to before
- [ ] Incoming call: identical behavior
- [ ] Decline: identical behavior
- [ ] Cold-start recovery: identical behavior
- [ ] Audio routing: identical behavior
- [ ] `adb logcat`: no direct cross-object calls remain in call paths

**Status:** `[ ] not started`

---

### PR-9b — feat: android:process=":callkeep_core"

**Branch:** `feat/android-callkeep-core-process-declaration`
**Target:** `develop`
**Risk:** Medium-High — actual process split, requires thorough QA
**Prerequisite:** PR-9a merged

**This is the payoff of PR-9a.** Transport is already broadcast-based.
This PR just tells the OS to run `PhoneConnectionService` in a new process.

**`AndroidManifest.xml`:**
```xml
<service
    android:name=".services.services.connection.PhoneConnectionService"
    android:process=":callkeep_core"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
    ... />
```

**`PhoneConnectionService.kt`:**
- `onCreate()`: call `AssetHolder.initForIsolatedProcess(context)`
  (no `FlutterEngine` in `:callkeep_core`)
- Verify: zero remaining references to main-process singletons

**`WebtritCallkeepPlugin.kt`:**
- Teardown: unregister broadcast receivers on `detachFromEngine`

**`build.gradle`:**
- `flutter.jar` + `core-ktx` available for test compilation in both processes

**QA checklist:**
- [ ] Outgoing call: initiate, answer remote, end
- [ ] Incoming call: receive push, answer, end
- [ ] Incoming call: decline
- [ ] Kill main process mid-call -> relaunch -> state recovered
- [ ] Kill `:callkeep_core` process mid-call -> main handles gracefully
- [ ] Cold-start: answered call -> `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` fires
- [ ] Audio routing: speaker <-> earpiece
- [ ] DTMF, hold/resume
- [ ] Doze mode + battery saver
- [ ] Android 10, 12, 13, 14
- [ ] `adb shell ps | grep callkeep` shows two separate processes

**Status:** `[ ] not started`

---

### PR-10 — feat: example app multi-line calls rewrite

**Branch:** `feat/example-app-multi-line-calls`
**Target:** `develop`
**Risk:** Low — example only
**Prerequisite:** PR-9b merged

| File | Change |
|------|--------|
| `actions_cubit.dart` | Replace freezed -> plain state, add `CallLine` model |
| `actions_state.dart` | Call lines, connection tracking |
| `actions_screen.dart` | Full UI rewrite — grouped sections, draggable panel |
| `main_screen.dart` | Simplify to navigation hub |
| `tests_cubit.dart` | Concurrent test guard, clearLog |
| `tests_screen.dart` | Progress indicator |
| `core/event_log.dart` | NEW — `EventLogView` with auto-scroll |
| `core/log_entry.dart` | NEW — `LogEntry` model |

**Validate:**
- [ ] Example app builds and runs on Android
- [ ] All call paths demonstrable in the UI

**Status:** `[ ] not started`

---

## Progress Tracker

| PR | Title | Branch | Status | Merged |
|----|-------|--------|--------|--------|
| PR-1 | ~~analysis_options check~~ | ~~`fix/standardize-analysis-options`~~ | `skipped` | — |
| PR-2a | fix: WakeLock cache | `fix/signaling-wakelock-cache` | `not started` | — |
| PR-2b | fix: println -> Log.d | `fix/signaling-logging` | `not started` | — |
| PR-2c | fix: broadcast receiver context | `fix/broadcast-receiver-context` | `not started` | — |
| PR-2d | fix: force-unwrap null safety | `fix/lifecycle-null-safety` | `not started` | — |
| PR-2e | fix: endCall callback timing | `fix/endcall-callback-timing` | `not started` | — |
| PR-3 | docs: architecture guide | `docs/android-architecture-guide` | `not started` | — |
| PR-4a | feat: StorageDelegate options | `feat/android-storage-delegate-options` | `not started` | — |
| PR-4b | feat: AssetHolder isolated init | `feat/android-asset-holder-isolated` | `not started` | — |
| PR-4c | feat: CallMetaData + CallDiagnostics | `feat/android-metadata-diagnostics` | `not started` | — |
| PR-4d | fix: notification null guards | `fix/incoming-call-notification-null-safety` | `not started` | — |
| PR-5a | test: RetryManagerTest | `test/retry-manager-test` | `not started` | — |
| PR-5b | test: StorageDelegateSoundTest | `test/storage-delegate-sound-test` | `not started` | — |
| PR-5c | test: IsCallPhoneSecurityExceptionTest | `test/is-call-phone-security-exception-test` | `not started` | — |
| PR-5d | test: SignalingIsolateServiceWakeLockTest | `test/signaling-wakelock-test` | `not started` | — |
| PR-5e | test: callkeep_android_options_test | `test/callkeep-android-options-dart` | `not started` | — |
| PR-6 | feat: MainProcessConnectionTracker | `feat/android-connection-tracker` | `not started` | — |
| PR-7a | feat: ForegroundService tracker | `feat/android-foreground-service-tracker` | `not started` | — |
| PR-7b | feat: IncomingCallService cold-start | `feat/android-incoming-call-cold-start` | `not started` | — |
| PR-8 | feat: Pigeon API additions | `feat/android-pigeon-api-additions` | `not started` | — |
| PR-9a | refactor: broadcast transport | `feat/android-broadcast-transport-migration` | `not started` | — |
| PR-9b | feat: process declaration | `feat/android-callkeep-core-process-declaration` | `not started` | — |
| PR-10 | feat: example app rewrite | `feat/example-app-multi-line-calls` | `not started` | — |

---

## Dependency Graph

```
PR-1  ──────────────────────────────── independent
PR-2a ──────────────────────────────── independent
PR-2b ──────────────────────────────── independent
PR-2c ──────────────────────────────── independent
PR-2d ──────────────────────────────── independent
PR-2e ──────────────────────────────── independent (verify commit first)
PR-3  ──────────────────────────────── independent
PR-4a ──────────────────────────────── independent
PR-4b ──────────────────────────────── independent
PR-4c ── after PR-4a ────────────────────────────
PR-4d ──────────────────────────────── independent
PR-5a ── after PR-4a ────────────────────────────
PR-5b ── after PR-4a ────────────────────────────
PR-5c ──────────────────────────────── independent
PR-5d ── (or ship with PR-2a) ───────────────────
PR-5e ── after PR-4a ────────────────────────────
PR-6  ── after PR-4a, PR-4b, PR-4c ──────────────
PR-7a ── after PR-6 ─────────────────────────────
PR-7b ── after PR-6, PR-7a, PR-4a ──────────────
PR-8  ── after PR-7b ────────────────────────────
PR-9a ── after ALL above merged ─────────────────
PR-9b ── after PR-9a ────────────────────────────
PR-10 ── after PR-9b ────────────────────────────
```

**Critical path (sequential, cannot parallelize):**
```
PR-6 -> PR-7a -> PR-7b -> PR-8 -> PR-9a -> PR-9b
```

**Wave 1 — open all simultaneously:**
PR-1, PR-2a, PR-2b, PR-2c, PR-2d, PR-2e, PR-3, PR-4a, PR-4b, PR-4d

**Wave 2 — after Wave 1 relevant PRs land:**
PR-4c, PR-5a, PR-5b, PR-5c, PR-5d, PR-5e

**Wave 3 — critical path:**
PR-6 -> PR-7a -> PR-7b -> PR-8 -> PR-9a -> PR-9b -> PR-10

---

## Key Risks & Mitigations

| Risk | Severity | PR | Mitigation |
|------|----------|----|------------|
| `MainProcessConnectionTracker` state drift | High | PR-6 | Thread-safety review; 3 test files cover it |
| Broadcast not cross-process (missing `applicationContext`) | High | PR-9a | Catch in PR-9a while still single-process — easier to debug |
| Broadcast blocked by OS (Doze, battery saver) | High | PR-9b | Real-device QA with battery saver forced on |
| Synchronous return values removed in transport migration | Medium | PR-9a | All callbacks via broadcasts; validate every result path |
| `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` fires incorrectly | Medium | PR-6 | Only fires after explicit `markAnswered()`; `ValidateConnectionAdditionTest` covers it |
| Pigeon files out of sync | Medium | PR-8 | Always regenerate together; `flutter analyze` enforces |
| Asset resolution fails in `:callkeep_core` | Medium | PR-9b | `AssetHolder.initForIsolatedProcess()` added in PR-4b, wired in PR-9b |
| Commit `2620715` content unknown ("tmp" label) | Medium | PR-2e | Run `git show 2620715` before porting |
| PR-1 already done by `344b9d5` | Low | PR-1 | Diff first, skip if no delta |

---

## How to Work With This Document

1. **Before starting a PR:** Read its spec + check Open Questions in `MIGRATION_CONTEXT.md`.
2. **After a PR lands:** Update Status + date in Progress Tracker. Update `MIGRATION_CONTEXT.md` branch heads. Commit both files to `feat/android-callkeep-core-process-migration`.
3. **Rebase rule:** Always rebase next branch on latest `develop` before opening PR.
4. **Conflict rule:** Prefer `develop` for repo-wide files; prefer feature branch for Android-specific files.
5. **Source of truth:** This document. If plan changes, update here first.

---

## Feature Branch Commit Map

| Commit | Message | PRs |
|--------|---------|-----|
| `6af63bd` | feat: run PhoneConnectionService in isolated :callkeep_core process | PR-4 through PR-9b |
| `169604d` | fix: cache WakeLock in SignalingIsolateService | PR-2a |
| `56bf18e` | fix: replace println with Log.d | PR-2b |
| `62a1084` | fix: consistent context for broadcast receivers | PR-2c |
| `c33f8df` | fix: remove force-unwrap on latestLifecycleActivityEvent | PR-2d |
| `de55f32` | fix: resolve endCall/endAllCalls callbacks | PR-2e |
| `2620715` | tmp — .claude/settings.local.json + TODO.md only | SKIP |
