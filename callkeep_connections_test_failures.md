# callkeep_connections_test failures

Branch: `fix/standalone-mode-no-telecom`
Device: HA1SVX8G (Android 13, API 33)
File: `integration_test/callkeep_connections_test.dart`
Result: **FIXED** — 12 passed, 0 failed, 1 skipped (2026-03-26)

---

## Summary of failures

All failures share one root cause: after `reportNewIncomingCall`, the
`:callkeep_core` process either does not create a Telecom connection, or
`CallkeepConnections` IPC cannot retrieve it. Every test that depends on a
connection existing in Telecom fails.

---

## Failed tests

### 1. getConnection returns stateRinging after reportNewIncomingCall

**Line:** 201-213

**What it does:**

```
reportNewIncomingCall(id, handle)
conn = _waitForConnection(id)   // polls getConnection() for 5 s
expect(conn, isNotNull)
expect(conn.state, stateRinging)
```

**Failure:**

```
Expected: not null
  Actual: <null>
```

`_waitForConnection` polls `CallkeepConnections().getConnection(callId)` every
100 ms for 5 seconds and never gets a non-null result. The connection was not
registered in `:callkeep_core`.

---

### 2. getConnection returns stateActive after answerCall

**Line:** 215-233

**What it does:**

```
reportNewIncomingCall(id, handle)
answerCall(id)
// waits for performAnswerCall callback via Completer (10 s timeout)
_waitFor(answerLatch.future, label: 'performAnswerCall')
```

**Failure:**

```
TimeoutException: performAnswerCall did not fire within timeout
```

No connection exists in Telecom, so `answerCall` has nothing to answer and the
`performAnswerCall` delegate callback is never dispatched.

---

### 3. getConnection returns stateHolding after setHeld(true)

**Line:** 235-255

**What it does:**

```
reportNewIncomingCall -> answerCall -> setHeld(true)
// waits for performAnswerCall first, then polls for stateHolding
```

**Failure:**

```
TimeoutException: performAnswerCall did not fire within timeout
```

Same as test 2 - blocks on `performAnswerCall` which never arrives.

---

### 4. getConnection returns null or stateDisconnected after endCall

**Line:** 257-276

**What it does:**

```
reportNewIncomingCall(id, handle)
endCall(id)
// waits for performEndCall callback (10 s timeout)
_waitFor(endLatch.future, label: 'performEndCall')
```

**Failure:**

```
TimeoutException: performEndCall did not fire within timeout
```

No connection exists in Telecom, so `endCall` cannot disconnect it and the
`performEndCall` delegate callback is never dispatched.

---

### 5. getConnections includes connection after reportNewIncomingCall

**Line:** 305-315

**What it does:**

```
reportNewIncomingCall(id, handle)
found = _waitForConnectionInList(id)  // polls getConnections() for 5 s
expect(found, isTrue)
```

**Failure:**

```
Expected: true
  Actual: <false>
```

`getConnections()` returns an empty list (or a list not containing `id`) even
after waiting 5 seconds post-`reportNewIncomingCall`.

---

### 6. getConnections includes both connections for two concurrent calls

**Line:** 317-340

**What it does:**

```
reportNewIncomingCall(id1, ...)
_waitForConnectionInList(id1)   // <-- blocks here, returns false
reportNewIncomingCall(id2, ...)
expect(connections.any(c => c.callId == id1), isTrue)
```

**Failure:**

```
Expected: true
  Actual: <false>
```

`id1` is not found in the connections list, for the same reason as test 5.

---

## Passing tests (for reference)

| Test | Result |
|------|--------|
| getConnection returns null for nonexistent callId | PASSED |
| getConnection returns null on non-Android (skipped) | SKIPPED |
| getConnections has no entry before any call | PASSED |
| cleanConnections completes without error on empty state | PASSED |
| cleanConnections removes all active connections | PASSED |
| updateActivitySignalingStatus completes for each enum value | PASSED |
| updateActivitySignalingStatus after tearDown does not throw | PASSED |

Note: all passing tests either operate on a non-existing callId (returns null
as expected) or do not depend on a Telecom connection being present.

---

## Fix applied (2026-03-26)

**Root cause 1**: `StandaloneCallService` was declared in `android:process=":callkeep_core"` in
`AndroidManifest.xml`. On Lenovo TB300FU (Android 13), the OEM `bringUpServiceLocked` marks
secondary processes as "bad", blocking all subsequent service starts.
**Fix**: Removed `android:process=":callkeep_core"` from `StandaloneCallService`; changed
`foregroundServiceType` from `phoneCall` to `microphone`.

**Root cause 2**: Lenovo OEM `ActivityManagerService` suppresses all `sendBroadcast` calls from
the app (`broadcastIntentWithFeature, suppress to broadcastIntent!`). Since `StandaloneCallService`
used `ConnectionServicePerformBroadcaster.handle` (which calls `sendBroadcast`), no events
(`DidPushIncomingCall`, `AnswerCall`, `HungUp`, `TearDownComplete`) ever reached
`ForegroundService.connectionServicePerformReceiver`.
**Fix**: Added `localHandle` and in-process receiver registry to `ConnectionServicePerformBroadcaster`.
`StandaloneCallService` now uses `localHandle`, which delivers events directly via `Handler.post` on
the main Looper, bypassing `ActivityManager`. `ForegroundService` registers its receivers with both
the system broadcast mechanism (for the Telecom path) and the in-process registry (for the
standalone path).

---

## Root cause hypothesis (original)

On this branch (`fix/standalone-mode-no-telecom`) the standalone mode path
bypasses `PhoneConnectionService` / Telecom entirely for some or all flows.
`CallkeepConnections.getConnection()` and `.getConnections()` query connection
state via IPC to `:callkeep_core` (which owns the `PhoneConnectionService`).
If the standalone path no longer goes through `PhoneConnectionService.onCreateIncomingConnection`,
no `PhoneConnection` object is registered, so all IPC queries return null/empty.

The specific operations affected:

- `reportNewIncomingCall` -> connection not created in `:callkeep_core`
- `answerCall` -> `performAnswerCall` never dispatched (no connection to answer)
- `endCall` (on ringing call) -> `performEndCall` never dispatched
