# ConnectionManager

**File**: `kotlin/com/webtrit/callkeep/services/services/connection/ConnectionManager.kt`

**Process**: `:callkeep_core`

## Responsibility

`ConnectionManager` is the **call registry** inside the `:callkeep_core` process. It holds all
live `PhoneConnection` objects and tracks ancillary state — pending calls, deferred answers, and
force-terminated calls.

It is the authoritative source of call state for the `:callkeep_core` process. The main process
has a corresponding shadow registry, `MainProcessConnectionTracker`.

## State

| Field                     | Description                                                                                                 |
|---------------------------|-------------------------------------------------------------------------------------------------------------|
| `connections`             | `Map<String, PhoneConnection>` — all `PhoneConnection` objects by callId                                    |
| `pendingCallIds`          | Calls for which `addPendingForIncomingCall()` was called but `onCreateIncomingConnection` has not yet fired |
| `pendingMetadata`         | `CallMetadata` stored during `addPendingForIncomingCall()`, consumed in `onCreateIncomingConnection`        |
| `pendingAnswers`          | Calls where `answerCall` arrived before `onCreateIncomingConnection`                                        |
| `forcedTerminatedCallIds` | Calls cleared by tearDown; stale Telecom callbacks for these are suppressed                                 |

## Key Methods

### Pending Call Registration

```kotlin
fun addPendingForIncomingCall(callId: String, metadata: CallMetadata)
```

Called via `NotifyPending` intent from the main process **before** Telecom delivers
`onCreateIncomingConnection`. Stores `callId` and `metadata` so `PhoneConnectionService` can look
them up when the connection is created.

```kotlin
fun checkAndReservePending(callId: String): Boolean
```

Atomic check: returns `true` and claims the pending slot if `callId` is in `pendingCallIds`.
Used to detect races where Telecom creates the connection before the `NotifyPending` intent
arrives (the connection is created with partial data in that case).

### Connection Lifecycle

```kotlin
fun addConnection(callId: String, connection: PhoneConnection)
fun getConnection(callId: String): PhoneConnection?
fun getConnections(): List<PhoneConnection>
fun removeConnection(callId: String)
```

### State Queries

```kotlin
fun isPending(callId: String): Boolean
fun isForcedTerminated(callId: String): Boolean
fun getPendingMetadata(callId: String): CallMetadata?
fun getPendingCallIds(): Set<String>
fun drainUnconnectedPendingCallIds(): List<String>
```

`drainUnconnectedPendingCallIds()` returns pending callIds that have no corresponding
`PhoneConnection` yet. Used during `TearDownConnections` to generate synthetic `HungUp` events
for calls Telecom never confirmed.

### Deferred Answer

```kotlin
fun reserveAnswer(callId: String)
fun consumeAnswer(callId: String): Boolean
```

`reserveAnswer` is called when the main process sends `ReserveAnswer` (user pressed answer before
the `PhoneConnection` existed). `consumeAnswer` is checked in `onCreateIncomingConnection` — if
true, the newly created connection is answered immediately.

### TearDown

```kotlin
fun forceTerminate(callId: String)
fun clearAll()
```

`forceTerminate` adds `callId` to `forcedTerminatedCallIds` and removes it from all other sets.
Subsequent Telecom callbacks for this call are ignored. `clearAll` resets all state.

## Synchronization

`checkAndReservePending()` uses `synchronized(connectionResourceLock)` to ensure atomicity when
multiple Telecom callbacks or IPC commands arrive concurrently for the same callId.

## Related Components

- [phone-connection-service.md](phone-connection-service.md) — creates this and calls its methods
- [phone-connection.md](phone-connection.md) — objects stored here
- [connection-tracker.md](connection-tracker.md) — the main-process mirror of this registry
