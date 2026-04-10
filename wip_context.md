# WIP Context — fix/standalone-mode-no-telecom-v2

Date: 2026-03-30
Status: COMPLETED — PR #232 merged to develop

## Meta

Fix integration test failures on device HA1SVX8G (Lenovo TB300FU, Android 13, API 33)
which has no `android.software.telecom`. All calls are routed through `StandaloneCallService`.

Branch created fresh from `develop` (which already includes PR #230 CallkeepCore facade
and PR #231 SharedFlow migration). 11 commits cherry-picked from old
`fix/standalone-mode-no-telecom` branch, adapted to the new architecture.

---

## Test Status — all PASSED on HA1SVX8G

| Script | Status |
|--------|--------|
| `tools/run_callkeep_client_scenarios.sh` | PASSED |
| `tools/run_callkeep_connections.sh` | PASSED |
| `tools/run_callkeep_delegate_edge_cases.sh` | PASSED |
| `tools/run_callkeep_foreground_service.sh` | PASSED |
| `tools/run_callkeep_lifecycle.sh` | PASSED |
| `tools/run_callkeep_reportendcall_reasons.sh` | PASSED |
| `tools/run_callkeep_state_machine.sh` | PASSED |
| `tools/run_callkeep_stress.sh` | PASSED |
| `tools/run_callkeep_call_scenarios.sh` | PASSED |
| `tools/run_callkeep_background_services.sh` | PASSED |

---

## Root causes and fixes

### Root cause 1 — StandaloneCallService in :callkeep_core process

`StandaloneCallService` was declared with `android:process=":callkeep_core"` in
`AndroidManifest.xml`. On Lenovo TB300FU the OEM `bringUpServiceLocked` marks secondary
processes as "bad", blocking all subsequent service starts.

Additionally, in a separate JVM the `InProcessCallkeepCore.instance` singleton is a
different object from the main process singleton — `listeners` is empty, so
`notifyConnectionEvent()` delivers to nobody.

**Fix**: removed `android:process=":callkeep_core"` from `StandaloneCallService`.
Service now runs in the main process alongside `ForegroundService`.

### Root cause 2 — Foreground service type mismatch

`StandaloneCallService.promoteToForeground()` called `startForeground()` with
`FOREGROUND_SERVICE_TYPE_PHONE_CALL` (0x04), but the manifest declared `microphone` (0x80).
This caused `IllegalArgumentException: foregroundServiceType 0x00000004 is not a subset of
foregroundServiceType attribute 0x00000080`.

The `phoneCall` foreground type also requires the Telecom subsystem, which is absent on
devices that use `StandaloneCallService`.

**Fix**: changed to `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` in `promoteToForeground()`.

### Root cause 3 — OEM broadcast suppression

Lenovo TB300FU has a custom `ActivityManagerService.broadcastIntentWithFeature()` that
suppresses ALL `sendBroadcast` calls from the app. `sendBroadcast` always goes through
`system_server` (PID 1191) even for components in the same process — via Binder IPC.
The OEM intercepts the call at the AMS level before the intent reaches the delivery queue.

Logcat evidence (during failing test):

```
broadcastIntentWithFeature, suppress to broadcastIntent!
// repeated ~18 times at ~512ms intervals (test polling loop)
// SignalingIsolateService.signalingStatusReceiver — never fires
// background isolate port — never registered
// timeout after 10s
```

**Fix — StandaloneCallService**: replaced `ConnectionServicePerformBroadcaster.handle.dispatch()`
with `CallkeepCore.instance.notifyConnectionEvent()`. This delivers events directly to all
registered `ConnectionEventListener`s and per-call dynamic receivers in-process, bypassing AMS.

**Fix — SignalingStatusBroadcaster / SignalingIsolateService**: already solved in `develop`
via PR #231 (SharedFlow migration). `SignalingStatusBroadcaster` now uses `StateFlow` instead
of `sendBroadcast`. `SignalingIsolateService` collects via a `CoroutineScope`, never touches AMS.
This fix was inherited for free when branching from `develop`.

---

## Architecture — final solution

### StandaloneCallService event dispatch

Before:

```kotlin
dispatcher.dispatch(baseContext, event, data)
// -> ConnectionServicePerformBroadcaster.handle.dispatch()
// -> context.sendBroadcast()  -- OEM suppresses this
```

After:

```kotlin
core.notifyConnectionEvent(event, data)
// -> CallkeepCore.instance (main process singleton)
// -> listeners.forEach { it.onConnectionEvent(event, data) }      -- ForegroundService etc.
// -> inProcessReceivers.forEach { receiver.onReceive(ctx, intent) } -- per-call receivers
// No AMS, no Binder IPC, no OEM suppression
```

### CallkeepCore.notifyConnectionEvent

Added to `CallkeepCore` interface and implemented in `InProcessCallkeepCore`:

```kotlin
override fun notifyConnectionEvent(event: ConnectionEvent, data: Bundle?) {
    val actionName = event.name
    val intent = Intent(actionName).apply { data?.let { putExtras(it) } }
    listeners.forEach { it.onConnectionEvent(event, data) }
    inProcessReceivers.entries.toList().forEach { (receiver, actions) ->
        if (actionName in actions) receiver.onReceive(context, intent)
    }
}
```

`inProcessReceivers: ConcurrentHashMap<BroadcastReceiver, List<String>>` — populated by
`registerConnectionEvents()` / `unregisterConnectionEvents()`. Per-call receivers
(OngoingCall, TearDownComplete, etc.) registered this way are delivered in-process.

---

## Files changed in this branch

| File | Change |
|------|--------|
| `AndroidManifest.xml` | removed `android:process` from StandaloneCallService |
| `StandaloneCallService.kt` | use `core.notifyConnectionEvent()` instead of broadcaster dispatch; `FOREGROUND_SERVICE_TYPE_MICROPHONE` |
| `CallkeepCore.kt` | added `notifyConnectionEvent()` to interface |
| `InProcessCallkeepCore.kt` | added `inProcessReceivers` map; implemented `notifyConnectionEvent()`; extended register/unregister |
| `tools/run_callkeep_*.sh` | per-suite test runner scripts |
| `tools/_test_lib.sh` | shared test helper library |
| `.gitignore` | added exclusion for worktrees |
| `example/run_integration_tests.sh` | updated test runner script |
