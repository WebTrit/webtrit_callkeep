# Migration Plan: `feat/android-callkeep-core-process-migration` → `develop`

> **Living document.** Update the status tables after each PR lands.
> Last updated: 2026-03-13

---

## Overview

The feature branch implements Android dual-process architecture, moving
`PhoneConnectionService` (Telecom `ConnectionService`) from the main app
process into an isolated `:callkeep_core` process. This prevents stale Binder
references and zombie Telecom states when the main process crashes.

**Total scope:** 127 files, +7 751 / −3 185 lines

**Goal:** Extract this work into a sequence of safe, independently-reviewable
PRs that never break `develop`. The most disruptive change (actual process
isolation) lands last.

---

## Core Decomposition Principle

> **Cross-process-compatible mechanisms work in-process too.
> In-process-only mechanisms do NOT work cross-process.**

This asymmetry is the key to safe decomposition:

| Mechanism | Works in single process | Works cross-process |
|-----------|------------------------|---------------------|
| Direct object calls (e.g. `connectionManager.addCall()`) | ✅ | ❌ |
| Shared memory / singletons | ✅ | ❌ |
| Local broadcasts (`sendBroadcast`) | ✅ | ✅ |
| `BroadcastReceiver` with `exported=false` | ✅ | ✅ (same app) |

**Strategic implication:** First, replace all direct in-process calls with
broadcast-based IPC **while staying in a single process**. The system continues
to work identically. Then, in a separate PR, just move
`PhoneConnectionService` to `:callkeep_core` in `AndroidManifest.xml`.
The process split becomes a near-trivial one-line change because the transport
layer is already in place.

```
Direct calls   →   Broadcasts (still 1 process)   →   2 processes
   today           PR-9a (safe, testable)            PR-9b (manifest only)
```

This principle applies to any future refactoring in this codebase: if you need
to extract a component to a separate process, migrate its transport first.

### Where the principle guides each PR

| PR | Application of principle |
|----|--------------------------|
| PR-6 | `ConnectionsApi` switches from querying `ConnectionManager` directly to querying `MainProcessConnectionTracker` — the tracker is the cross-process-compatible read path, safe to introduce while still single-process |
| PR-7 | `ForegroundService` switches from direct calls to events-via-replay — same mechanism that works cross-process |
| **PR-9a** | ALL remaining direct `ForegroundService → ConnectionManager / PhoneConnectionService` calls replaced by `ConnectionServicePerformBroadcaster` sends + `BroadcastReceiver` handling. **Still single process. Zero user-visible change.** |
| **PR-9b** | One manifest line: `android:process=":callkeep_core"`. Transport already ready. |

---

## Branch State Snapshot

### `develop` (baseline, 2026-03-13)

| Item | State |
|------|-------|
| Last commit | `344b9d5` — chore: standardize analysis_options.yaml |
| Android architecture | Single-process (`PhoneConnectionService` in main process) |
| Process isolation | Not present |
| `MainProcessConnectionTracker` | Not present |
| IPC layer | Direct in-process calls |
| `incomingCallFullScreen` option | Not present |
| Cold-start answer detection | Not present (`CALL_ID_ALREADY_EXISTS_AND_ANSWERED` enum missing) |
| Unit test count (Android) | Baseline |
| docs/ folder | Not present |

### `feat/android-callkeep-core-process-migration` (feature, 2026-03-13)

| Item | State |
|------|-------|
| Last commit | `2620715` — tmp |
| Android architecture | Dual-process (main + `:callkeep_core`) |
| Process isolation | `PhoneConnectionService` in `:callkeep_core` process |
| `MainProcessConnectionTracker` | Present — mirrors connection state in main process |
| IPC layer | `ConnectionServicePerformBroadcaster` → local broadcasts |
| `incomingCallFullScreen` option | Present — `CallkeepAndroidOptions.incomingCallFullScreen` |
| Cold-start answer detection | Present — `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` path |
| Unit test count (Android) | +10 test files, 660+ lines |
| docs/ folder | Present — 13 markdown files |

### ⚠️ Changes on feature branch NOT to migrate

The feature branch was prototyped in isolation and deleted several repo-wide
files that must stay on `develop`:

| File / Dir | Action on feature branch | Decision |
|-----------|--------------------------|----------|
| `AGENTS.md` (root) | Deleted | **SKIP** — keep on develop |
| `CONTRIBUTING.md` | Deleted | **SKIP** |
| `lefthook.yml` | Deleted | **SKIP** |
| `cspell.json` | Deleted | **SKIP** |
| `.claude/settings.json` | Deleted | **SKIP** |
| `.claude/hooks/` (all scripts) | Deleted | **SKIP** |
| `tool/scripts/` | Deleted | **SKIP** |
| `webtrit_callkeep/AGENTS.md` | Deleted | **SKIP** |
| `webtrit_callkeep_android/AGENTS.md` | Deleted | **SKIP** |
| `webtrit_callkeep_ios/AGENTS.md`, `CLAUDE.md` | Deleted | **SKIP** |
| `webtrit_callkeep_platform_interface/AGENTS.md`, `CLAUDE.md` | Deleted | **SKIP** |
| `.github/workflows/` changes | Modified | **EVALUATE** per PR — likely skip |
| `.claude/settings.local.json` (3 files) | Added | **SKIP** — local only |
| `webtrit_callkeep/test/webtrit_callkeep_test.dart` | Deleted | **SKIP** |

---

## Migration Phases

```
PR-1   Code quality (analysis_options, pubspec)          [no logic change]
PR-2   Isolated bug fixes (5 commits from feature)       [safe, independent]
PR-3   Documentation (docs/ directory)                   [additive]
PR-4   Utility improvements                              [additive, no API break]
PR-5   New unit tests for existing code                  [additive]
PR-6   MainProcessConnectionTracker + ConnectionManager  [foundational]
PR-7   IncomingCallService + ForegroundService           [integration]
PR-8   Pigeon API additions (new options + enum)         [API surface change]
PR-9a  Transport migration: direct calls → broadcasts    [single process, testable]
PR-9b  Process declaration: android:process=callkeep_core [manifest only, payoff]
PR-10  Example app rewrite                               [cosmetic, last]
```

**Key insight (PR-9a → PR-9b split):** Cross-process-compatible mechanisms
(local broadcasts) work inside a single process too. By migrating the
transport first (PR-9a), PR-9b becomes a near-mechanical manifest change
with all the hard work already tested and merged.

---

## PR Detail Specifications

---

### PR-1 — Code quality: standardize analysis_options & pubspec

**Branch:** `fix/standardize-analysis-options`
**Target:** `develop`
**Risk:** Very low — no logic changes

**What to cherry-pick / extract:**

| File | Change |
|------|--------|
| `webtrit_callkeep/analysis_options.yaml` | Switch to `very_good_analysis` |
| `webtrit_callkeep_android/analysis_options.yaml` | Switch to `very_good_analysis` |
| `webtrit_callkeep_ios/analysis_options.yaml` | Switch |
| `webtrit_callkeep_linux/analysis_options.yaml` | Switch |
| `webtrit_callkeep_macos/analysis_options.yaml` | Switch |
| `webtrit_callkeep_web/analysis_options.yaml` | Switch |
| `webtrit_callkeep_windows/analysis_options.yaml` | Switch |
| `webtrit_callkeep_platform_interface/analysis_options.yaml` | Switch |
| All `pubspec.yaml` | `flutter_lints` → `very_good_analysis` dep |

**Validate before merge:**
- [ ] `flutter analyze lib test` passes in all packages
- [ ] `dart format --line-length 80 --set-exit-if-changed lib test` passes

**Status:** `[ ] not started`

---

### PR-2 — Bug fixes from feature branch

**Branch:** `fix/android-signaling-and-callback-fixes`
**Target:** `develop`
**Risk:** Low — isolated fixes, each tested in feature branch

**Commits to port (create new commits, not cherry-pick to avoid history pollution):**

| Source commit | What it fixes | Files |
|---------------|---------------|-------|
| `169604d` | Cache WakeLock in SignalingIsolateService to prevent leak | `SignalingIsolateService.kt` |
| `56bf18e` | Replace `println` with `Log.d` in SignalingIsolateService | `SignalingIsolateService.kt` |
| `62a1084` | Use consistent context to unregister broadcast receivers | Multiple service files |
| `de55f32` | Remove force-unwrap on `latestLifecycleActivityEvent` | `ForegroundService.kt` or similar |
| `2620715` | Resolve endCall/endAllCalls callbacks after broadcast confirmation | `ConnectionManager.kt`, dispatcher |

**Note on `2620715`:** This commit is labeled "tmp" but contains `fix(android): resolve endCall/endAllCalls callbacks after broadcast confirmation (#146)` logic — verify what's actually in it before porting.

**Validate before merge:**
- [ ] Android unit tests pass: `./gradlew :callkeep:test`
- [ ] No regressions in call end flow

**Status:** `[ ] not started`

---

### PR-3 — Documentation: `docs/` directory

**Branch:** `docs/android-architecture-guide`
**Target:** `develop`
**Risk:** Zero — additive only

**Files to add:**

```
webtrit_callkeep_android/docs/
├── README.md              (navigation hub)
├── architecture.md        (process model, component map)
├── call-triggers.md       (4 initiation paths)
├── call-flows.md          (end-to-end flow diagrams)
├── ipc.md                 (ConnectionPerform event table)
├── foreground-service.md  (lifecycle, retry, hot-restart)
├── phone-connection-service.md (state machine, dispatcher)
├── signaling-isolate-service.md (boot resilience)
├── incoming-call-service.md (push notification path)
├── active-call-service.md (role, service types)
├── common-utilities.md    (StorageDelegate, RetryManager)
├── notifications.md       (channels, builders, permissions)
└── pigeon-api.md          (full API reference)
```

**Also include:** `webtrit_callkeep_android/CLAUDE.md` expanded content,
`webtrit_callkeep_android/README.md` updates.

**Note:** The `docs/` content references dual-process architecture which is not
yet on `develop`. Either:
- (a) Add a "planned / in progress" note at the top of each architectural doc, **or**
- (b) Ship docs AFTER PR-9 lands

**Decision:** → Add `> ⚠️ This document describes architecture being migrated via phased PRs. Some sections describe upcoming state.` header to architecture-specific docs.

**Status:** `[ ] not started`

---

### PR-4 — Utility improvements (additive, no API break)

**Branch:** `feat/android-utility-improvements`
**Target:** `develop`
**Risk:** Low — additive changes to utility classes

**Files and changes:**

| File | Change summary |
|------|----------------|
| `StorageDelegate.kt` | Persistent options (ringback sound path, full-screen flag) — additive new keys |
| `AssetHolder.kt` | `initForIsolatedProcess()` — new static method for cross-process asset resolution |
| `CallMetaData.kt` | New fields for metadata (verify what fields added) |
| `CallDiagnostics.kt` | Cross-process diagnostic queries — additive methods |
| `IncomingCallNotificationBuilder.kt` | Null-safe guards, option checks |
| `PhoneConnectionEnums.kt` | New enum values (ONLY the ones not requiring IPC, e.g. `CALL_ID_ALREADY_EXISTS_AND_ANSWERED`) |

**⚠️ Dependency note:** `AssetHolder.initForIsolatedProcess()` is only called
from `PhoneConnectionService.onCreate()` which doesn't exist in dual-process
form yet. Adding the method is safe; the call site comes in PR-9.

**Validate before merge:**
- [ ] Kotlin compiles with no warnings
- [ ] Existing unit tests still pass
- [ ] `StorageDelegate` keys don't conflict with existing ones

**Status:** `[ ] not started`

---

### PR-5 — New unit tests for code already on `develop`

**Branch:** `test/android-unit-test-coverage`
**Target:** `develop`
**Risk:** Zero — tests only, no production code changes

**Files to add:**

| Test file | Tests for |
|-----------|-----------|
| `RetryManagerTest.kt` | `RetryManager` (289 lines) |
| `StorageDelegateSoundTest.kt` | `StorageDelegate` persistent flags (77 lines) |
| `IsCallPhoneSecurityExceptionTest.kt` | Error classification utility (50 lines) |
| `SignalingIsolateServiceWakeLockTest.kt` | WakeLock leak prevention (61 lines) |
| `callkeep_android_options_test.dart` | Dart-side Android options (55 lines) |

**Prerequisite:** PR-4 must be merged first (some tests may test new utility
methods added in PR-4).

**build.gradle changes needed:**
```gradle
// Add to android/build.gradle — enables test compilation
testImplementation("androidx.test:core:1.5.0")
testImplementation("org.robolectric:robolectric:4.11.1")
```

**Validate before merge:**
- [ ] `./gradlew :callkeep:test` — all new tests pass
- [ ] No test-only dependencies bleed into runtime classpath

**Status:** `[ ] not started`

---

### PR-6 — `MainProcessConnectionTracker` + `ConnectionManager` hardening

**Branch:** `feat/android-connection-tracker`
**Target:** `develop`
**Risk:** Medium — new class + logic changes in `ConnectionManager`

**What this PR introduces:**

#### New class: `MainProcessConnectionTracker.kt`
```
Location: services/services/foreground/MainProcessConnectionTracker.kt
```
- Companion object on `ForegroundService` (or standalone singleton)
- Thread-safe map: `callId → (metadata, state, answered)`
- Methods: `add`, `addWithState`, `updateState`, `markAnswered`, `isAnswered`,
  `remove`, `exists`, `get`, `getAll`
- **On `develop`:** starts empty, nothing populates it yet — safe

#### `ConnectionManager.kt` changes
- Atomic check-and-add for concurrent call creation guard
- `validateConnectionAddition()` now checks `MainProcessConnectionTracker.isAnswered(callId)`
  and returns `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` if true
- All other validation logic stays the same

**New tests to add:**
| Test file | Lines |
|-----------|-------|
| `MainProcessConnectionTrackerTest.kt` | 252 |
| `ConnectionManagerTest.kt` | 280 |
| `ValidateConnectionAdditionTest.kt` | 167 |

**⚠️ On `develop`, the new `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` code path in
`validateConnectionAddition` will never fire** because nothing calls
`markAnswered()` yet. Functionally identical to current behavior. Safe.

**Validate before merge:**
- [ ] `./gradlew :callkeep:test` — all 3 new test files pass
- [ ] Manually test incoming call → answer → verify no regression
- [ ] Manually test cold-start is not broken (it just won't have the NEW behavior yet)

**Status:** `[ ] not started`

---

### PR-7 — `IncomingCallService` + `ForegroundService` integration

**Branch:** `feat/android-incoming-call-tracker-integration`
**Target:** `develop`
**Risk:** Medium-High — touches call handling and notification paths

**Prerequisites:** PR-6 merged

**Changes:**

#### `IncomingCallService.kt`
- `handleLaunch()` calls `MainProcessConnectionTracker.add(callId, metadata)`
  when `incomingCallFullScreen == true` (pre-populate for cold-start)
- `performAnswerCall()` calls `MainProcessConnectionTracker.markAnswered(callId)`
- Null guards on `performEndCall`

#### `CallLifecycleHandler.kt`
- Guards for null connection state
- Suppress notification after answer (avoid double-notification)

#### `ForegroundService.kt`
- On reconnect: iterate `MainProcessConnectionTracker.getAll()` and replay
  missed `ConnectionPerform` events
- Retry/timeout logic improvements (from feature branch)
- **⚠️ Note:** The full IPC binding to `:callkeep_core` process is NOT part
  of this PR — that's PR-9. This PR only adds the tracker integration for
  the existing in-process case.

**New tests to add:**
| Test file | Lines |
|-----------|-------|
| `IncomingCallServiceFullScreenTest.kt` | 165 |
| `FailedCallsStoreTest.kt` | 97 |

**Validate before merge:**
- [ ] Full incoming call flow (foreground + background)
- [ ] Cold-start: kill app mid-call, relaunch, verify call state recovers
- [ ] Full-screen notification still appears on lock screen

**Status:** `[ ] not started`

---

### PR-8 — Pigeon API additions

**Branch:** `feat/android-pigeon-api-additions`
**Target:** `develop`
**Risk:** Medium — API surface change, touches generated code

**Prerequisites:** PR-7 merged

**Changes:**

#### `pigeons/callkeep.messages.dart` (source)
- Add `incomingCallFullScreen` field to `PCallkeepAndroidOptions`
- Add `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` to validation enum
- Any other new enum values added on feature branch

#### Regenerate Pigeon bindings:
```bash
cd webtrit_callkeep_android
dart run pigeon --input pigeons/callkeep.messages.dart
```

#### `Generated.kt` — regenerated Kotlin bindings
#### `callkeep.pigeon.dart` — regenerated Dart bindings
#### `converters.dart` — add conversion for new fields/enum values
#### `webtrit_callkeep_android.dart` — wire new option through

#### `webtrit_callkeep_platform_interface` model changes
- `callkeep_options.dart` — add `incomingCallFullScreen` to `CallkeepAndroidOptions`
- Formatting-only changes to other model files (multi-line enum values) — **evaluate**
  whether to include or skip (they are non-breaking)

#### `webtrit_callkeep/lib/src/callkeep.dart`
- Pass through new option

**Validate before merge:**
- [ ] `flutter analyze lib test` in all affected packages
- [ ] `dart format --line-length 80 --set-exit-if-changed lib test`
- [ ] `flutter test` in `webtrit_callkeep` and `webtrit_callkeep_android`
- [ ] Pigeon bindings are in sync (Dart ↔ Kotlin)

**Status:** `[ ] not started`

---

### PR-9a — Broadcast transport migration (still single process)

**Branch:** `feat/android-broadcast-transport-migration`
**Target:** `develop`
**Risk:** Medium — replaces call paths, but single-process so fully debuggable

**Prerequisites:** PR-1 through PR-8 all merged

**The principle in action:** Replace every remaining place where
`ForegroundService` (or anyone in the main process) calls into
`PhoneConnectionService` / `ConnectionManager` directly with a
`ConnectionServicePerformBroadcaster.send(event)` + matching
`BroadcastReceiver`. **`PhoneConnectionService` still runs in the main
process.** Broadcasts are delivered in-process at nanosecond latency.
Behavior is identical to before.

**After this PR, the system is cross-process-ready without being cross-process.**

#### `ConnectionServicePerformBroadcaster.kt`
- Verify `sendBroadcast` uses `applicationContext` (required for cross-process
  delivery later)
- Remove any synchronous return values — all results must now flow back as
  separate broadcast events (callbacks)

#### `ForegroundService.kt`
- Remove all direct calls to `PhoneConnectionService` methods
- Register `BroadcastReceiver` for `ConnectionPerform` result events
- Send `ConnectionServicePerformBroadcaster` events instead of direct calls

#### `PhoneConnectionService.kt`
- Register `BroadcastReceiver` for incoming `ConnectionPerform` command events
- Remove any direct references to main-process singletons (prepare for isolation)
- Result callbacks sent back via broadcasts

#### `PhoneConnection.kt`
- State machine: any remaining direct callbacks to main process → broadcasts
- Endpoint change race condition guard (API 34+)

#### `PhoneConnectionServiceDispatcher.kt`
- Full dispatch through broadcast events only

#### `ConnectionsApi.kt`
- Switch from `ConnectionManager` direct queries to `MainProcessConnectionTracker`
  queries (already populated by PR-6/7; tracker IS the cross-process-safe read path)

**Validate before merge:**
- [ ] Outgoing call: initiate, answer remote, end — **identical behavior to before**
- [ ] Incoming call: receive push, answer, end — **identical behavior**
- [ ] Incoming call: decline — **identical behavior**
- [ ] Cold-start recovery — **identical behavior**
- [ ] Audio routing: speaker ↔ earpiece — **identical behavior**
- [ ] `adb logcat` shows no direct cross-object calls remaining in call paths
- [ ] All existing unit tests still pass

**Status:** `[ ] not started`

---

### PR-9b — Process declaration: move PhoneConnectionService to `:callkeep_core`

**Branch:** `feat/android-callkeep-core-process-declaration`
**Target:** `develop`
**Risk:** Medium-High — the actual split, but transport is already ready

**Prerequisites:** PR-9a merged

**This is the payoff of PR-9a.** The transport being broadcast-based means
this PR is nearly mechanical. The system was already "cross-process-ready" —
now we just tell the OS to use a second process.

#### `AndroidManifest.xml`
```xml
<service
    android:name=".services.services.connection.PhoneConnectionService"
    android:process=":callkeep_core"   <!-- THIS IS THE KEY LINE -->
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE"
    ... />
```

#### `PhoneConnectionService.kt`
- `onCreate()`: add `AssetHolder.initForIsolatedProcess(context)` — no
  `FlutterEngine` available in `:callkeep_core`, must resolve assets manually
- Remove any remaining singleton accesses that live in the main process
  (should be zero after PR-9a, verify)

#### `WebtritCallkeepPlugin.kt`
- Teardown: unregister broadcast receivers on `detachFromEngine` — main process
  must not try to call into `:callkeep_core` synchronously during shutdown

#### `build.gradle`
- Ensure `flutter.jar` + `core-ktx` available for test compilation in both
  processes

**QA checklist (now testing the REAL dual-process scenario):**
- [ ] Outgoing call: initiate, answer remote, end
- [ ] Incoming call: receive push, answer, end
- [ ] Incoming call: receive push, decline
- [ ] Kill **main process** during active call → relaunch → call state recovered
- [ ] Kill **`:callkeep_core` process** during active call → main process handles gracefully
- [ ] Cold-start: app killed while call was answered → relaunch → `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` fires
- [ ] Audio routing: speaker ↔ earpiece toggle
- [ ] DTMF tones
- [ ] Hold / resume
- [ ] Concurrent calls (if supported)
- [ ] Background restrictions: Doze mode, battery saver
- [ ] Android 10, 12, 13, 14 — verify full-screen intent still works
- [ ] Verify `:callkeep_core` process visible in Android Studio profiler / `adb shell ps`

**Status:** `[ ] not started`

---

### PR-10 — Example app rewrite

**Branch:** `feat/example-app-multi-line-calls`
**Target:** `develop`
**Risk:** Low — example only, doesn't affect library

**Prerequisites:** PR-9 merged (uses new options and enum values)

**Changes:**

| File | Change |
|------|--------|
| `example/lib/features/actions/bloc/actions_cubit.dart` | Replace freezed → plain state, add `CallLine` model, multi-line support |
| `example/lib/features/actions/bloc/actions_state.dart` | Add call lines, connection tracking |
| `example/lib/features/actions/view/actions_screen.dart` | Full UI rewrite — grouped sections, draggable call panel, end drawer |
| `example/lib/features/main/main_screen.dart` | Simplify to navigation hub |
| `example/lib/features/tests/bloc/tests_cubit.dart` | Concurrent test protection, clearLog |
| `example/lib/features/tests/view/tests_screen.dart` | Add progress indicator |
| `example/lib/core/event_log.dart` | NEW — `EventLogView` widget with auto-scroll |
| `example/lib/core/log_entry.dart` | NEW — `LogEntry` model |

**Validate before merge:**
- [ ] Example app builds and runs on Android
- [ ] All call paths demonstrable in the UI

**Status:** `[ ] not started`

---

## Progress Tracker

| PR | Title | Branch | Status | Merged |
|----|-------|--------|--------|--------|
| PR-1 | Code quality: analysis_options | `fix/standardize-analysis-options` | `not started` | — |
| PR-2 | Bug fixes (5 commits) | `fix/android-signaling-and-callback-fixes` | `not started` | — |
| PR-3 | Documentation: docs/ | `docs/android-architecture-guide` | `not started` | — |
| PR-4 | Utility improvements | `feat/android-utility-improvements` | `not started` | — |
| PR-5 | New unit tests | `test/android-unit-test-coverage` | `not started` | — |
| PR-6 | MainProcessConnectionTracker | `feat/android-connection-tracker` | `not started` | — |
| PR-7 | IncomingCallService integration | `feat/android-incoming-call-tracker-integration` | `not started` | — |
| PR-8 | Pigeon API additions | `feat/android-pigeon-api-additions` | `not started` | — |
| PR-9a | Transport migration: direct calls → broadcasts | `feat/android-broadcast-transport-migration` | `not started` | — |
| PR-9b | Process declaration: callkeep_core manifest | `feat/android-callkeep-core-process-declaration` | `not started` | — |
| PR-10 | Example app rewrite | `feat/example-app-multi-line-calls` | `not started` | — |

---

## Dependency Graph

```
PR-1  ──────────────────────────────────────────────────────► can start immediately
PR-2  ──────────────────────────────────────────────────────► can start immediately
PR-3  ──────────────────────────────────────────────────────► can start immediately
PR-4  ──────────────────────────────────────────────────────► can start immediately
PR-5  ─── after PR-4 ──────────────────────────────────────►
PR-6  ─── after PR-4 ──────────────────────────────────────►
PR-7  ─── after PR-6 ──────────────────────────────────────►
PR-8  ─── after PR-7 ──────────────────────────────────────►
PR-9a ─── after PR-1,2,3,4,5,6,7,8 (all merged) ──────────►  transport only, still 1 process
PR-9b ─── after PR-9a ─────────────────────────────────────►  manifest line, payoff
PR-10 ─── after PR-9b ─────────────────────────────────────►
```

**Parallel tracks possible:**
- PR-1, PR-2, PR-3, PR-4 can all be opened simultaneously
- PR-5 and PR-6 can be developed in parallel (both depend on PR-4)
- PR-9a and PR-9b must be strictly sequential — this is intentional

---

## Key Risks & Mitigations

| Risk | Severity | PR | Mitigation |
|------|----------|----|------------|
| `MainProcessConnectionTracker` state drift | High | PR-6 | Unit tests; thread-safety review |
| Broadcast not delivered cross-process (missing `applicationContext`) | High | PR-9a | Catch this in PR-9a while still single-process — same code path, easier to debug |
| Broadcast delivery blocked by OS (Doze, battery saver) | High | PR-9b | Real-device QA checklist; test with battery saver forced on |
| `CALL_ID_ALREADY_EXISTS_AND_ANSWERED` fires incorrectly | Medium | PR-6 | `isAnswered()` only true after explicit `markAnswered()`; `ValidateConnectionAdditionTest` covers it |
| Race condition: concurrent connection creation | Medium | PR-6 | Atomic check-and-add in `ConnectionManager` |
| Synchronous return values from `PhoneConnectionService` calls removed | Medium | PR-9a | All callbacks converted to broadcast events; validate every call result flows back |
| NPE: endpoint change race on API 34+ | Medium | PR-9a | Guard added in `PhoneConnection.kt` |
| Pigeon out-of-sync between Dart and Kotlin | Medium | PR-8 | Always regenerate together; `flutter analyze` enforces |
| Asset resolution fails in `:callkeep_core` process | Medium | PR-9b | `AssetHolder.initForIsolatedProcess()` added in PR-4; wired in PR-9b |
| Example app diverges from library API | Low | PR-10 | PR-10 is last; example stays on feature branch until then |

---

## How to Work With This Document

1. **Before starting a PR:** Read its specification section above.
2. **After a PR lands:** Update the `Status` → `merged` and add the merge date in the Progress Tracker table.
3. **When `develop` advances:** Rebase the next feature branch on updated `develop` before opening the PR.
4. **Resolving conflicts:** Always prefer `develop`'s version for repo-wide files (AGENTS.md, CONTRIBUTING.md, lefthook.yml). Prefer feature branch version for Android-specific files.
5. **Source of truth:** This document is the source of truth. If the plan changes, update it here first.

---

## Reference: Feature Branch Commit Map

| Commit | Message | PRs that use this |
|--------|---------|-------------------|
| `6af63bd` | feat: run PhoneConnectionService in isolated :callkeep_core process | PR-6, PR-7, PR-8, PR-9 |
| `169604d` | fix: cache WakeLock in SignalingIsolateService | PR-2 |
| `56bf18e` | fix: replace println with Log.d | PR-2 |
| `62a1084` | fix: use consistent context to unregister broadcast receivers | PR-2 |
| `de55f32` | fix: remove force-unwrap on latestLifecycleActivityEvent | PR-2 |
| `2620715` | tmp / fix: resolve endCall/endAllCalls callbacks | PR-2 (verify content) |
