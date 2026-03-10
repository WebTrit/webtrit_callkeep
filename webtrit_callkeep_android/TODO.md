# Android Implementation Audit

Audit of `webtrit_callkeep_android` organised **by file**. Severity markers:
🔴 Critical · 🟠 High · 🟡 Medium · 🔵 Low/Quality




---



### `services/incoming_call/IncomingCallService.kt`

#### 🔴 C-4 · Unsafe `metadata!!` force-unwrap in broadcast receiver

**Lines:** 52 (derivation), 56–58 (unwrap)

```kotlin
val metadata = intent?.extras?.let(CallMetadata::fromBundleOrNull)  // nullable
...
ConnectionPerform.AnswerCall.name  -> performAnswerCall(metadata!!)  // 56
ConnectionPerform.DeclineCall.name -> performDeclineCall(metadata!!) // 57
ConnectionPerform.HungUp.name      -> performDeclineCall(metadata!!) // 58
```

Any malformed broadcast throws `NullPointerException` inside a `BroadcastReceiver`, terminating
the entire incoming-call handling path.

**Fix:** Guard with `metadata ?: return` before the `when` branches.

---

#### 🟡 M-10 · Conditional tracker `add` but unconditional `remove`

**Lines:** 186–188 (conditional add), 173 (unconditional remove)

```kotlin
if (StorageDelegate.Sound.isIncomingCallFullScreen(baseContext)) {
    ForegroundService.connectionTracker.add(metadata.callId, metadata)  // skipped when false
}
...
ForegroundService.connectionTracker.remove(metadata.callId)  // always runs
```

When full-screen is disabled, `remove()` targets a key that was never added — couples remove
to add logic that isn't always executed.

**Fix:** Always add to the tracker on launch, or guard `remove()` with `containsKey`.

---

### `services/connection/PhoneConnection.kt`

#### 🔴 C-5 · Endpoint-change race condition (API 34+)

**Lines:** 574–586 (`performEndpointChange`); 691–695, 700–704 (callback clears state on executor
thread)

```kotlin
synchronized(this) {
    if (pendingEndpointRequest?.identifier == endpoint.identifier) return
    pendingEndpointRequest = endpoint
}
// ← lock released; callback thread can clear pendingEndpointRequest here,
//   allowing a second concurrent call to pass the guard
requestCallEndpointChange(endpoint, audioEndpointChangeExecutor, EndpointChangeReceiver(endpoint))
```

Two concurrent `requestCallEndpointChange` calls race; audio routing is left in an undefined state.

**Fix:** Keep the lock held until the async operation is dispatched, or use a serial executor /
state machine to serialise endpoint change requests.

---

### `services/connection/PhoneConnectionService.kt`

#### 🟠 H-8 · Duplicate connection creation race

**Lines:** outgoing 115 (check) + 123 (add); incoming 174 (check) + 189 (add)

`isConnectionAlreadyExists()` and `addConnection()` are not atomic. Two concurrent Telecom
callbacks can both pass the existence check before either adds its connection, resulting in two
`PhoneConnection` objects for the same call.

**Fix:** Add a `putIfAbsent`-style atomic check-and-add in `ConnectionManager`, or synchronise
the entire check-create-add block.

---

#### 🟡 M-12 · `startService()` failure not fully propagated

**Lines:** 394–413 (`communicate()`)

When `startService()` throws (background restriction, API 31+), the catch block broadcasts
`HungUp` but does not clean up `ConnectionManager` pending state or cancel the `RetryManager`
entry in `ForegroundService`. The retry loop may continue for up to 5 seconds with no connection
to cancel.

**Fix:** Include `callId` in the failure broadcast and handle it as a distinct
`ConnectionPerform` event, or route via the existing `OutgoingFailure` path.

---

### `services/connection/ConnectionManager.kt`

#### 🟠 H-8 (related) · Check-and-add not atomic

See `PhoneConnectionService.kt` H-8 above — the race lives here.

---

#### 🟡 M-3 · Inconsistent synchronisation

**Lines:** 64 (`isConnectionDisconnected` — no lock); all other public methods hold
`connectionResourceLock`

```kotlin
fun isConnectionDisconnected(callId: String): Boolean {
    return connections[callId]?.state == Connection.STATE_DISCONNECTED  // no lock
}
```

Concurrent reads via `isConnectionDisconnected()` while other threads modify the map return
stale data.

**Fix:** Synchronise all public accessors on the same lock, or use a purely
`ConcurrentHashMap`-based design without external locking.

---

#### 🔵 L-3 · `addConnection()` visibility too broad

**Lines:** 13–24 (TODO comment on line 13)

A TODO acknowledges only `PhoneConnectionService` should add connections. Any code holding a
reference to `ConnectionManager` can corrupt the registry.

**Fix:** Restrict to `internal` visibility or a factory/controller scoped to the `connection`
package.

---

### `services/foreground/ForegroundService.kt`

#### 🟠 H-7 · `OutgoingCallbacksManager` timeout race → double invocation

**Lines:** 722–738 (`rescheduleTimeout`), 740–744 (`remove`)

`rescheduleTimeout()` posts a new runnable without atomicity. A timeout runnable already
mid-execution can overlap with the success-path `remove(callId)`, causing the callback to be
invoked twice for the same `callId`.

**Fix:** Use a sequence number or `AtomicBoolean` consumed-flag per pending callback.

---

#### 🟡 M-9 · `onDelegateSet()` restores stale connection state after hot-restart

**Lines:** 611–628

```kotlin
val tracked = connectionTracker.getAll()          // snapshot
...
Handler(Looper.getMainLooper()).post {             // async
    tracked.forEach { metadata ->
        PhoneConnectionService.forceUpdateAudioState(baseContext, metadata) // may be stale
    }
}
```

A `ConnectionRemoved` broadcast between snapshot and handler execution causes Flutter to receive
phantom audio-state updates for calls that have already ended.

**Fix:** Check `connectionTracker.getConnection(callId) != null` inside the handler post before
calling `forceUpdateAudioState`.

---

### `services/foreground/MainProcessConnectionTracker.kt`

#### 🟡 M-4 · Non-atomic `add` + `markAnswered`

**Lines:** 17–35 (two separate `ConcurrentHashMap`s)

`add(callId, metadata)` and `markAnswered(callId)` operate on separate maps. A broadcast
arriving between the two sequential calls sees a connection that exists but is not yet marked
answered.

**Fix:** Provide `addAndMarkAnswered(callId, metadata)`, or combine both maps into one entry
holding `(CallMetadata, answered: Boolean)`.

---

### `services/active_call/ActiveCallService.kt`

#### 🔵 L-5 · Missing `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`

**Line:** 49 (TODO comment)

```kotlin
// TODO: maybe FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK is needed as well
```

On Android 12+ (API 31) the OS enforces declared foreground service types; a missing type causes
the service to be killed mid-call on newer devices.

**Fix:** Verify whether media playback type is required; if so, add it to both the service class
and manifest `foregroundServiceType` attribute.

---

### `managers/NotificationManager.kt`

#### 🟠 H-1 · `activeCalls` list not thread-safe

**Lines:** 57–59 (declaration), 27–29, 35–36 (unsynchronised read-modify-write)

```kotlin
private var activeCalls = mutableListOf<CallMetadata>()  // companion object, no lock

// lines 27–29
val existPosition = activeCalls.indexOfFirst { it.callId == id }
if (existPosition != -1) activeCalls.removeAt(existPosition)
activeCalls.add(0, callMetaData)
```

`ActiveCallService` and `ForegroundService`'s broadcast receiver can call these methods
concurrently → `ConcurrentModificationException` or lost updates.

**Fix:** Replace with `ConcurrentHashMap<String, CallMetadata>` keyed by `callId`.

---

### `common/ContextHolder.kt`

#### 🟠 H-2 · `context` getter not synchronised

**Lines:** 29–31 (getter), 38 (`@Synchronized init`)

```kotlin
val context: Context
get() = applicationContext ?: throw IllegalStateException(...)  // no sync

@Synchronized
fun init(context: Context) {
    ...
}
```

A thread reading `context` while `init()` runs on another thread can observe a
partially-initialised value.

**Fix:** Declare `applicationContext` as `@Volatile`, or mark the getter `@Synchronized`.

---

### `common/ActivityHolder.kt`

#### 🟠 H-3 · Concurrent access on `activity` field and listener list

**Lines:** 26–31 (`setActivity`), 60–66 (add/remove), 68–70 (iterate)

```kotlin
fun setActivity(newActivity: Activity?) {
    if (activity != newActivity) {   // TOCTOU, no lock
        activity = newActivity
        notifyActivityChanged(newActivity)
    }
}
private fun notifyActivityChanged(...) {
    activityChangeListeners.forEach { ... }  // iterates while add/remove can modify
}
```

Two race scenarios: (1) TOCTOU on `activity`, (2) `ConcurrentModificationException` on the
listener list.

**Fix:** Synchronise `setActivity()`; use `CopyOnWriteArrayList` for `activityChangeListeners`.

---

### `common/AssetHolder.kt`

#### 🟡 M-5 · Dual initialisation paths can race

**Lines:** 20–26 (`init`), 34–46 (`initForIsolatedProcess`)

In the `:callkeep_core` process `initForIsolatedProcess()` runs first and installs a stub
`FlutterAssets`. When real `init()` is called later, it returns early because the field is
already set — the plugin serves asset paths from a stub that cannot resolve real Flutter assets.

**Fix:** Use an explicit state enum (`UNINITIALIZED`, `REAL`, `ISOLATED`) and assert on
unexpected transitions.

---

### `common/StorageDelegate.kt`

#### 🟡 M-2 · Lazy init without thread safety

**Lines:** 15–21

```kotlin
private var sharedPreferences: SharedPreferences? = null   // no @Volatile

private fun getSharedPreferences(context: Context?): SharedPreferences? {
    if (sharedPreferences == null) {   // double-checked locking without visibility guarantee
        sharedPreferences = context?.getSharedPreferences(...)
    }
    return sharedPreferences
}
```

Two threads can each create a `SharedPreferences` instance and overwrite each other's reference.

**Fix:** Add `@Volatile` to `sharedPreferences`, or initialise once in a true `init()` block.

---

#### 🔵 L-8 · Exception messages lack diagnostic context

**Lines:** 89–91, 100–103, 163–165, 167–170

```kotlin
throw Exception("OnNotificationSync not found")  // no process, package, or file context
```

In a multi-process app it is impossible to tell which process or call triggered the failure.

**Fix:** Include `context.packageName` and the preferences file name in every message.

---

### `common/TelephonyUtils.kt`

#### 🟠 H-6 · Unchecked `getSystemService()` cast

**Lines:** 31–33 (`getTelecomManager`), 20–28 (`isEmergencyNumber`); called at ~39, 43, 53

```kotlin
fun getTelecomManager(): TelecomManager {
    return context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager  // ClassCastException on null
}
```

On devices where the service is unavailable this throws `ClassCastException` instead of a
handled error. Affects `addNewIncomingCall`, `placeCall`, and `registerPhoneAccount`.

**Fix:** Use `as?` with an explicit `IllegalStateException` on null.

---

### `common/FlutterEngineHelper.kt`

#### 🟡 M-1 · Silently continues with empty engine on invalid callback handle

**Lines:** 34–63 (`initializeFlutterEngine`)

When `FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)` returns `null`
(stale handle in `StorageDelegate`), the method logs an error but leaves `backgroundEngine`
non-null. Callers check `backgroundEngine != null` and proceed, invoking Pigeon APIs into a
Dart executor that never ran any code.

**Fix:** Set `backgroundEngine = null` on lookup failure and propagate the error to the calling
service so it can stop itself or report via the delegate.

---

### `common/Log.kt`

#### 🔵 L-7 · Duplicate logic between static and instance methods; inconsistent API

**Lines:** 13–40 (instance), 42–149 (companion object)

Instance methods and companion-object statics perform the same routing independently. Also,
`d`, `i`, `v` instance methods lack the optional `Throwable` parameter that `e` and `w` have.

**Fix:** Have instance methods delegate to static methods; add `throwable: Throwable? = null`
to all levels.

---

### `models/AudioDevice.kt`

#### 🟠 H-4 · Unsafe enum deserialization → process crash

**Line:** 23

```kotlin
val type = AudioDeviceType.valueOf(it.getString("type")!!)
```

Throws `IllegalArgumentException` for any unknown or missing enum name (e.g., new SDK value).
Crashes the process wherever audio device lists are parsed, including `CallMetadata` (see M-7).

**Fix:** `AudioDeviceType.entries.find { it.name == raw } ?: AudioDeviceType.UNKNOWN`.

---

### `models/FailureMetaData.kt`

#### 🟠 H-5 · Unsafe ordinal index → `ArrayIndexOutOfBoundsException`

**Lines:** 43; called from `ForegroundService.kt` ~575

```kotlin
val outgoingFailureType = OutgoingFailureType.entries[rawOutgoingFailureType]
```

Crashes if the stored integer is out of range — when the enum changes between builds or
SharedPreferences data is corrupted.

**Fix:**
`OutgoingFailureType.entries.getOrNull(rawOutgoingFailureType) ?: OutgoingFailureType.UNENTITLED`.

---

### `models/CallHandle.kt`

#### 🔵 L-2 · `"undefined"` magic-string fallback

**Line:** 16

```kotlin
val number = bundle?.getString("number") ?: "undefined"
```

Returns `CallHandle("undefined")` instead of surfacing a parse failure. The string travels
undetected through the entire call stack to the Telecom framework or UI.

**Fix:** Return `null` from a `fromBundleOrNull()` variant; let callers decide how to handle
absence.

---

### `models/CallMetaData.kt` · `models/FailureMetaData.kt`

#### 🟡 M-7 · `CallMetadata` silently drops audio devices that fail deserialisation

**File:** `CallMetaData.kt` · **Lines:** 145–148

```kotlin
return list?.mapNotNull { AudioDevice.fromBundle(it) } ?: emptyList()
```

`mapNotNull` discards malformed `AudioDevice` entries without logging. Combined with H-4, a
single unrecognised `AudioDeviceType` silently removes the device from the list; the caller may
route audio incorrectly.

**Fix:** Log a warning for each failed deserialisation before filtering with `mapNotNull`.

---

#### 🔵 L-6 · Duplicate Bundle serialisation logic

**Files:** `CallMetaData.kt`, `FailureMetaData.kt`

Both classes implement `toBundle()` / `fromBundle()` ad-hoc with no shared interface. Key
naming changes must be applied in two places.

**Fix:** Extract a `BundleSerializable<T>` interface or a sealed `BundleCodec` helper.

---

---

## Dart / Flutter layer (webtrit_callkeep_android)

---

### `lib/src/webtrit_callkeep_android.dart`

#### 🔴 C-3 · Force-unwrap on nullable callback handle in background isolate dispatcher

**Lines:** 686, 697, 709

```dart
// line 686
final closure = PluginUtilities.getCallbackFromHandle(handle)! as ForegroundStartServiceHandle;
// line 697
final closure = PluginUtilities.getCallbackFromHandle(handle)! as ForegroundChangeLifecycleHandle;
// line 709
final closure = PluginUtilities.getCallbackFromHandle(
    handle)! as CallKeepPushNotificationSyncStatusHandle;
```

If the handle is stale (app reinstalled, hot-restart), `getCallbackFromHandle` returns `null`
and the isolate crashes silently with no recovery path.

**Fix:** Null-check the result; log and return early (or invoke an error callback) on null.

---

#### 🟠 H-9 · `Future<dynamic>` return types in public API

**Lines:** 345–347, 350–352, 355–367, 370–372, 375–379

```dart
Future<dynamic> startBackgroundSignalingService
(...) // 345
Future<dynamic> stopBackgroundSignalingService(...) // 350
Future<dynamic> incomingCallBackgroundSignalingService(…) // 355
Future<dynamic> endCallsBackgroundSignalingService(...) // 370
Future<dynamic> endCallBackgroundSignalingService(...) // 375
```

Callers cannot statically verify return values; errors are silently swallowed or cause runtime
type errors.

**Fix:** Type as `Future<void>` (or the appropriate model type) and propagate errors explicitly.

---

#### 🟡 M-6 · Uncaught exceptions in Pigeon delegate relays

**Classes:** `_CallkeepDelegateRelay`, `_CallkeepBackgroundServiceDelegateRelay`

All 15+ `@FlutterApi` callback implementations forward to the user-supplied delegate without
`try/catch`. If the delegate throws, the exception propagates through the Pigeon channel back to
`ForegroundService`, potentially corrupting Telecom state.

**Fix:** Wrap each delegate call in `try/catch`; log and return a safe default on error.

---

### `lib/src/common/converters.dart`

#### 🟡 M-8 · Incomplete bidirectional converter for `PCallkeepServiceStatus`

**Lines:** 337–344 (`toCallkeep` — correct), 346–352 (`toPigeon` — asymmetric)

`toCallkeep()` maps both `lifecycleEvent` and `mainSignalingStatus`. The reverse `toPigeon()`
maps only `lifecycleEvent` — `mainSignalingStatus` is silently dropped on every round-trip.

**Fix:** Add `mainSignalingStatus: mainSignalingStatus?.toPigeon()` to the `toPigeon()` extension.

---

### `pigeons/callkeep.messages.dart` · `android/.../Generated.kt`

#### 🔵 L-10 · No Pigeon protocol version negotiation

Pigeon-generated codec has no version field. Adding or removing a message field without
regenerating both sides silently corrupts decoding or causes index-out-of-bounds in `decode()`.

**Fix:** Add a `version` constant compared at `setUp()` time to detect mismatches early.

---

### `test/webtrit_callkeep_android_test.dart`

#### 🔵 L-9 · Insufficient test coverage

Only 3 tests exist (`registers instance`, `setUp`, `isSetUp`). Missing:

- Null/invalid callback handle in isolate dispatcher (C-3).
- `PlatformException` propagation from Pigeon.
- Delegate relay exception isolation (M-6).
- Converter round-trips (M-8 gap).

**Fix:** Add unit tests with mocked `MethodChannel` / Pigeon stubs for all error paths.

---

---

## 🔗 Integration Issues (webtrit_phone)

Issues in the consuming app that expose or amplify bugs above.
All paths relative to `/Users/serdun/Documents/work/webtrit/webtrit_phone/`.

---

### `lib/app/router/main_shell.dart`

#### 🟠 I-1 · `Callkeep.setUp()` without error handling

**Lines:** 49–65

```dart
_callkeep.setUp
(
CallkeepOptions
(
...
)
); // no try/catch
```

If `setUp()` throws (missing asset, Pigeon init failure), the exception propagates through
`initState()` and crashes the widget tree.

**Fix:** Wrap in `try/catch`; display an error state or retry on failure.

---

#### 🟡 I-2 · `CallkeepConnections` never disposed

**Lines:** 37 (instantiation), 83–88 (`dispose` — only calls `_callkeep.tearDown()`)

```dart

late final CallkeepConnections _callkeepConnections = CallkeepConnections();
// dispose() — no _callkeepConnections cleanup
```

Any native resources or channel subscriptions held by `CallkeepConnections` are never released.

**Fix:** Add explicit cleanup for `_callkeepConnections` in `dispose()`.

---

### `lib/features/call/bloc/call_bloc.dart`

#### 🟠 I-3 · `getConnections()` / `getConnection()` unguarded in critical cleanup path

**Lines:** 2391, 2400

```dart

final localConnections = await
callkeepConnections.getConnections
(); // no try/catch
...

final connection = await
callkeepConnections.getConnection
(
callEvent
.
callId
); // no try/catch
```

Both are inside `_onCallPerformEventEnded()` — the call-end cleanup path. If either throws,
the handler exits early and active call state is left dangling.

**Fix:** Wrap in `try/catch`; log and continue cleanup even when individual calls fail.

---

#### 🟡 I-4 · `didActivateAudioSession` / `didDeactivateAudioSession` fire-and-forget

**Lines:** 2745–2750, 2754–2759

```dart
void didActivateAudioSession() {
  () async {
    await AppleNativeAudioManagement.audioSessionDidActivate();
    await AppleNativeAudioManagement.setIsAudioEnabled(true);
  }
  (); // never awaited, no error handling
}
```

Any native audio management exception is silently discarded. The call continues in a broken
audio state with no user notification.

**Fix:** Propagate errors via the delegate or at minimum log to Crashlytics.

---

#### 🟡 I-7 · `continueStartCallIntent()` fire-and-forget

**Line:** 2595

```dart
void continueStartCallIntent
(...) {
_continueStartCallIntent(handle, displayName, video); // Future discarded
}
```

`_continueStartCallIntent()` has an internal `try/catch`, but late timeout or unhandled
exceptions fire without the Bloc knowing.

**Fix:** Use `unawaited()` with an error zone, or restructure to the `add(event)` pattern.

---

#### 🟡 I-8 · `updateActivitySignalingStatus()` unguarded on every state change

**Line:** 203

```dart
callkeepConnections.updateActivitySignalingStatus
(
change.nextState.callServiceState.signalingClientStatus.toCallkeepSignalingStatus(),
);
```

Called from `onChange()` on every Bloc state change with no `try/catch`. A Pigeon channel error
here terminates the `onChange()` callback and can destabilise the Bloc event loop.

**Fix:** Wrap in `try/catch` with logging; this is a best-effort sync, not a critical path.

---

### `lib/features/call/services/services_isolate.dart`

#### 🟠 I-5 · Missing `releaseResources` event for signaling isolate

**Lines:** 120–132

```dart
// push-notification has cleanup (lines 113–114):
case CallkeepPushNotificationSyncStatus
.
releaseResources
:
await
_disposeCommonDependencies
(
);

// signaling has no equivalent — acknowledged by TODO on line 128:
// TODO: Implement a deterministic cleanup path for common dependencies...
```

Database connections, loggers, and secure storage are never cleaned up in the long-running
signaling isolate.

**Fix:** Add a `releaseResources` lifecycle event to the signaling service protocol and invoke
`_disposeCommonDependencies()` when received.

---

### `lib/features/settings/features/network/bloc/network_cubit.dart`

#### 🟡 I-6 · `startService()` / `stopService()` without error handling

**Lines:** 60, 63

```dart
await
_callkeepBackgroundService.stopService
(); // no try/catch
await
_callkeepBackgroundService.startService
(); // no try/catch
```

If either call fails, `_initializeActiveIncomingType()` (line 67) is never reached, leaving UI
state inconsistent with actual service state.

**Fix:** Wrap in `try/catch`; emit an error state and re-query actual service state on failure.

---

*End of audit — 5 critical · 9 high · 12 medium · 10 low/quality · 8 integration = **44 items**.*
