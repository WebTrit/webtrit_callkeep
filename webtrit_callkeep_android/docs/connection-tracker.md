# ConnectionTracker / MainProcessConnectionTracker

**Files**:

- `kotlin/com/webtrit/callkeep/services/services/foreground/ConnectionTracker.kt` (interface)
- `kotlin/com/webtrit/callkeep/services/services/foreground/MainProcessConnectionTracker.kt` (
  implementation, companion object on `ForegroundService`)

## Responsibility

`MainProcessConnectionTracker` is a **shadow call-state registry** living in the main-process JVM.
Because `PhoneConnectionService` and its `ConnectionManager` run in the `:callkeep_core` process,
the main process cannot read their in-memory state. `MainProcessConnectionTracker` mirrors Telecom
connection state so the main process and Flutter can query call status at any time without an IPC
round-trip.

State is updated exclusively from broadcast events emitted by `PhoneConnectionService`.

## Data Structures

| Field               | Type                                                  | Description                                                             |
|---------------------|-------------------------------------------------------|-------------------------------------------------------------------------|
| `connections`       | `ConcurrentHashMap<String, CallMetadata>`             | Non-terminated calls with full metadata                                 |
| `connectionStates`  | `ConcurrentHashMap<String, PCallkeepConnectionState>` | Telecom state snapshot per call                                         |
| `pendingCallIds`    | `MutableSet<String>`                                  | Calls sent to Telecom, `PhoneConnection` not yet created                |
| `answeredCallIds`   | `MutableSet<String>`                                  | Calls that have reached STATE_ACTIVE                                    |
| `terminatedCallIds` | `MutableSet<String>`                                  | Ended calls (never removed, to detect stale events)                     |
| `pendingAnswers`    | `MutableSet<String>`                                  | Deferred answers (user pressed answer before `PhoneConnection` existed) |

## Callback Guards

Three additional sets prevent duplicate Dart notifications for the same call:

| Guard                        | Purpose                                                                                                                                                                  |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `directNotifiedCallIds`      | Calls notified directly during `tearDown()`. Suppresses a subsequent `HungUp` broadcast for the same call.                                                               |
| `endCallDispatchedCallIds`   | Calls for which `performEndCall()` has already been sent to Dart. Prevents a second dispatch.                                                                            |
| `signalingRegisteredCallIds` | Calls for which `reportNewIncomingCall()` succeeded. Suppresses the corresponding `DidPushIncomingCall` broadcast (which would result in a duplicate Dart notification). |

## State Transitions

```text
addPending(callId)
    pendingCallIds += callId

promote(callId, metadata, state)
    pendingCallIds -= callId
    connections[callId] = metadata
    connectionStates[callId] = state

markAnswered(callId)
    answeredCallIds += callId
    connectionStates[callId] = STATE_ACTIVE

markHeld(callId, onHold)
    connectionStates[callId] = STATE_HOLDING or STATE_ACTIVE

markTerminated(callId)
    connections -= callId
    connectionStates -= callId
    pendingCallIds -= callId
    answeredCallIds -= callId
    terminatedCallIds += callId
```

## Query Methods

| Method                 | Returns                                                            |
|------------------------|--------------------------------------------------------------------|
| `exists(callId)`       | True if any of pending / connections / terminated contains this id |
| `isPending(callId)`    | True if in `pendingCallIds`                                        |
| `isTerminated(callId)` | True if in `terminatedCallIds`                                     |
| `isAnswered(callId)`   | True if in `answeredCallIds`                                       |
| `get(callId)`          | `CallMetadata?` from `connections`                                 |
| `getAll()`             | All entries in `connections`                                       |
| `getState(callId)`     | `PCallkeepConnectionState?` from `connectionStates`                |

## Thread Safety

All collections are either `ConcurrentHashMap` or accessed from the main thread (broadcast
receiver callbacks run on the main looper). Guard sets are `LinkedHashSet` accessed only from the
service's main thread.

## Related Components

- [callkeep-core.md](callkeep-core.md) — exposes the query/mutation API to the rest of the main
  process
- [foreground-service.md](foreground-service.md) — drives state mutations from broadcast handlers
- [ipc-broadcasting.md](ipc-broadcasting.md) — the events that trigger state transitions
