# Hosting callkeep on an app-owned Flutter engine (Android)

## Purpose

`WebtritCallkeep.attachToEngine` enables presenting and controlling callkeep calls from a Flutter
engine that your application creates and owns - for example a foreground-service or other headless
engine created with `automaticallyRegisterPlugins = false`.

Use it when incoming calls must be reported from such an engine. Apps that only use the FCM push
path described in the README do not need it.

## API

`com.webtrit.callkeep.WebtritCallkeep`

`attachToEngine(context: Context, messenger: BinaryMessenger)`

Sets callkeep up on the given engine: initializes callkeep's application context and registers its
background host channels on the engine's messenger.

`detachFromEngine(messenger: BinaryMessenger)`

Removes the setup performed by `attachToEngine` for that engine.

## Behavior

- While at least one engine is attached, callkeep presents the incoming-call UI (notification,
  ringtone, full-screen intent) and leaves background work to the host engine. It does not start
  its own background isolate.
- While no engine is attached, callkeep manages its own background isolate (the FCM push path).

## Requirements

- Call `attachToEngine` on the main thread, after the engine and its `BinaryMessenger` exist, and
  before the first incoming call is reported.
- Call `detachFromEngine` when the engine is destroyed.
- Both methods are idempotent and safe to call again after a restart.
- Call both from the process that hosts the engine.

## Integration

Keep the engine-owning component decoupled from callkeep: expose generic seams from it and wire
callkeep from the application layer.

### Engine-owning service

```kotlin
class MyEngineService : Service() {
    private var engine: FlutterEngine? = null

    override fun onCreate() {
        super.onCreate()
        val engine = FlutterEngine(
            applicationContext, null, /* ... */, /* automaticallyRegisterPlugins = */ false,
        )
        engine.dartExecutor.executeDartCallback(/* your Dart entry point */)
        onEngineReady?.invoke(applicationContext, engine.dartExecutor.binaryMessenger)
        this.engine = engine
    }

    override fun onDestroy() {
        engine?.dartExecutor?.binaryMessenger?.let { onEngineDestroyed?.invoke(it) }
        engine?.destroy()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var onEngineReady: ((Context, BinaryMessenger) -> Unit)? = null

        @Volatile
        var onEngineDestroyed: ((BinaryMessenger) -> Unit)? = null
    }
}
```

### Application wiring

```kotlin
import com.webtrit.callkeep.WebtritCallkeep

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MyEngineService.onEngineReady = WebtritCallkeep::attachToEngine
        MyEngineService.onEngineDestroyed = WebtritCallkeep::detachFromEngine
    }
}
```

### Reporting calls from the engine (Dart)

Inside the Dart entry point running on the engine, use callkeep's background APIs. They reach the
channels registered by `attachToEngine`.

```dart
// Report an incoming call:
await AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
  callId,
  CallkeepHandle.number(caller),
  displayName: displayName,
);

// Release a call:
await AndroidCallkeepServices.backgroundPushNotificationService.releaseCall(callId);
```

## Constraints

- Do not register callkeep's internal Pigeon channels directly.
- Do not reference callkeep's internal classes from your application.
