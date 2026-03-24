# Dual-Process Architecture

## Overview

The Android implementation intentionally runs in two OS processes to satisfy Android's requirement
that `ConnectionService` (which handles phone calls) can be alive and responsive even when the
application process is killed.

| Property            | `main` process                                       | `:callkeep_core` process            |
|---------------------|------------------------------------------------------|-------------------------------------|
| **Host class**      | `ForegroundService`, `SignalingIsolateService`, etc. | `PhoneConnectionService`            |
| **Flutter engine**  | Yes (foreground, signaling, push isolates)           | No                                  |
| **Android Telecom** | Indirect (via IPC)                                   | Direct (owns `Connection` objects)  |
| **Lifetime**        | Tied to app / foreground service                     | Tied to Telecom connection lifetime |

The process split is declared in `AndroidManifest.xml`:

```xml

<service android:name=".services.connection.PhoneConnectionService" android:process=":callkeep_core"
    android:exported="false"
    android:permission="android.permission.BIND_TELECOM_CONNECTION_SERVICE">
    <intent-filter>
        <action android:name="android.telecom.ConnectionService" />
    </intent-filter>
</service>
```

All other services omit `android:process` and therefore run in the default main process.

## IPC Mechanisms

Two mechanisms carry data between processes:

### 1. App-Scoped Broadcasts

Used for **event notifications** that the receiver handles asynchronously.

- Sender calls `context.sendBroadcast(intent.setPackage(packageName))`.
- Receiver registers with `registerReceiverCompat`.
- Events flow in **both directions**:
  - `:callkeep_core` → main: call lifecycle events (`AnswerCall`, `HungUp`, `DidPushIncomingCall`,
      media events, etc.)
  - main → `:callkeep_core`: ack events (`TearDownComplete`)

See [ipc-broadcasting.md](ipc-broadcasting.md) for the full event catalogue.

### 2. Explicit `startService` Intents

Used for **commands** where delivery must be guaranteed (broadcasts can be dropped if the receiver
is not yet registered).

- main → `:callkeep_core` commands: `TearDownConnections`, `ReserveAnswer`, `CleanConnections`,
  `SyncAudioState`, `SyncConnectionState`, and per-call commands (`AnswerCall`, `DeclineCall`,
  `HungUpCall`, `EstablishCall`, `UpdateCall`, `MuteCall`, `HoldCall`, `SpeakerCall`,
  `SetAudioDevice`, `SendDtmf`).
- `:callkeep_core` → main: `NotifyPending` (incoming call pending before PhoneConnection exists).

`PhoneConnectionService.onStartCommand()` routes each intent by `ServiceAction` enum.

## State Synchronization

Because the two processes have independent JVM heaps, call state must be explicitly synchronized:

- The main process maintains `MainProcessConnectionTracker` (see
  [connection-tracker.md](connection-tracker.md)) — a shadow copy of Telecom connection state.
- `:callkeep_core` maintains `ConnectionManager` (
  see [connection-manager.md](connection-manager.md))
  — the authoritative registry of live `PhoneConnection` objects.
- On app hot-restart, `ForegroundService.syncConnectionState()` sends `SyncAudioState` and
  `SyncConnectionState` commands so `:callkeep_core` re-fires its current state to a freshly
  attached Flutter engine.

## Critical Rules

1. **Never read `PhoneConnectionService.connectionManager` from the main process.** It is an empty
   object in the main-process JVM. All main-process call operations go through `CallkeepCore`.
2. **Never send global broadcasts.** Always call `.setPackage(context.packageName)` to scope
   broadcasts to the app.
3. **Prefer explicit intents for commands**, broadcasts for events.
