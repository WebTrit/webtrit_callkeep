# IPC Broadcasting

**File**: `kotlin/com/webtrit/callkeep/services/broadcaster/ConnectionServicePerformBroadcaster.kt`

## Overview

`ConnectionServicePerformBroadcaster` is the **cross-process event bus** between `:callkeep_core`
and the main process. It uses **app-scoped local broadcasts** (the intent's package is set to the
app package name) so no third-party app can receive or inject events.

All broadcasts use a single action string:

```text
com.webtrit.callkeep.CONNECTION_SERVICE_PERFORM
```

The event type is carried as a string extra inside the intent.

## Event Catalogue

### Call Lifecycle Events (`:callkeep_core` -> main)

| Event                 | Payload                         | Meaning                                          |
|-----------------------|---------------------------------|--------------------------------------------------|
| `DidPushIncomingCall` | `callId`, `CallMetadata` bundle | Incoming `PhoneConnection` created, UI shown     |
| `AnswerCall`          | `callId`                        | Connection answered (STATE_ACTIVE)               |
| `DeclineCall`         | `callId`                        | User rejected the call                           |
| `HungUp`              | `callId`, disconnect cause      | Call disconnected from either side               |
| `OngoingCall`         | `callId`, `CallMetadata` bundle | Outgoing connection dialing                      |
| `OutgoingFailure`     | `callId`, failure info          | Outgoing call could not be created               |
| `IncomingFailure`     | `callId`, failure info          | Incoming call setup failed                       |
| `ConnectionNotFound`  | `callId`                        | `PhoneConnection` not found — synthesized HungUp |

### Call Media Events (`:callkeep_core` -> main)

| Event                | Payload                       | Meaning                             |
|----------------------|-------------------------------|-------------------------------------|
| `AudioMuting`        | `callId`, `muted: Boolean`    | Mute state changed                  |
| `AudioDeviceSet`     | `callId`, `AudioDevice`       | Active audio device changed         |
| `AudioDevicesUpdate` | `callId`, `List<AudioDevice>` | Available audio device list changed |
| `SentDTMF`           | `callId`, digit               | DTMF tone dispatched                |
| `ConnectionHolding`  | `callId`, `onHold: Boolean`   | Hold state changed                  |

### Service Command Ack Events (`:callkeep_core` -> main)

| Event              | Payload | Meaning                                        |
|--------------------|---------|------------------------------------------------|
| `TearDownComplete` | —       | All connections torn down; tearDown can finish |

### Pending Registration Commands (main -> `:callkeep_core`, via explicit intent)

| `ServiceAction` | Description                                                                  |
|-----------------|------------------------------------------------------------------------------|
| `NotifyPending` | Register callId + metadata before Telecom fires `onCreateIncomingConnection` |

Note: Most main -> `:callkeep_core` commands are `startService` intents, not broadcasts. The
`NotifyPending` action is the only one also used as a direct intent to `PhoneConnectionService`.

## Broadcast Transport

```text
Sender:
    val intent = Intent(ACTION_CONNECTION_SERVICE_PERFORM)
        .setPackage(context.packageName)
        .putExtra(EXTRA_EVENT_TYPE, event.name)
        .putExtras(payload)
    context.sendBroadcast(intent)

Receiver:
    val filter = IntentFilter(ACTION_CONNECTION_SERVICE_PERFORM)
    context.registerReceiverCompat(receiver, filter)
```

`registerReceiverCompat` is a helper that calls `registerReceiver(receiver, filter,
RECEIVER_NOT_EXPORTED)` on API 33+ and the equivalent on older versions.

## Sender Locations

| Event                             | Sender                                                                     |
|-----------------------------------|----------------------------------------------------------------------------|
| All call lifecycle / media events | `PhoneConnection` (via `performEventHandle()` in `PhoneConnectionService`) |
| `TearDownComplete`                | `PhoneConnectionService.onStartCommand` after tear-down                    |
| `TearDownConnections` (command)   | `ForegroundService` via `CallkeepCore.sendTearDownConnections()`           |

## Receiver Locations

| Receiver                                               | Listens For                                                                               |
|--------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `InProcessCallkeepCore.globalReceiver`                 | All global call lifecycle and media events; fans out to `ConnectionEventListener` subs    |
| `ForegroundService` (via `ConnectionEventListener`)    | Global events routed by `CallkeepCore`                                                    |
| `IncomingCallService` (via `ConnectionEventListener`)  | Global events routed by `CallkeepCore`                                                    |
| Per-call dynamic receivers in `ForegroundService`      | `OngoingCall`, `OutgoingFailure`, `IncomingFailure`, `TearDownComplete`                   |
| `PhoneConnectionService` broadcast receiver            | `NotifyPending` (only during setup)                                                       |

`InProcessCallkeepCore` maintains a single `globalReceiver` registered via
`ConnectionServicePerformBroadcaster`. Individual services no longer register their own receivers
for `:callkeep_core` events — they subscribe through `CallkeepCore.addConnectionEventListener()`.

## Related Components

- [phone-connection.md](phone-connection.md) — dispatches lifecycle/media events
- [phone-connection-service.md](phone-connection-service.md) — dispatches `TearDownComplete`
- [callkeep-core.md](callkeep-core.md) — routes events to `ConnectionEventListener` subscribers
- [foreground-service.md](foreground-service.md) — implements `ConnectionEventListener`
- [dual-process.md](dual-process.md) — overall IPC design
