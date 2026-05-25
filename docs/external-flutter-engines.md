# Hosting callkeep on your own Flutter engine (Android)

This guide explains how to use callkeep from a Flutter engine that **your app creates**, instead
of the engine that callkeep manages on its own.

It is for integrators who run their own long-lived or headless engine on Android - for example a
foreground service that keeps a connection open and needs to present incoming calls. If you only
use the standard FCM push path described in the README, you do not need this.

## When you need it

callkeep's plugin (`WebtritCallkeepPlugin`) is registered automatically on engines created by the
Flutter framework (a `FlutterActivity`, a `FlutterFragment`, or callkeep's own background engine).
On those engines callkeep sets itself up transparently and you do nothing special.

If you create your own `FlutterEngine` - typically with `automaticallyRegisterPlugins = false`, so
that heavy plugins (WebRTC, audio, etc.) are not initialized on a background engine - the Flutter
framework does **not** register callkeep's plugin there. As a result:

- callkeep's application context is not initialized on that engine's process, and
- callkeep's background host channels are not bound to that engine's messenger.

Without setup, the first incoming call reported from that engine fails (the call UI is never
shown). `WebtritCallkeep.attachToEngine` is the supported way to set callkeep up on such an engine.

## API

`com.webtrit.callkeep.WebtritCallkeep`

```kotlin
object WebtritCallkeep {
    // Set callkeep up on a host-provided engine. Call once when the engine is ready,
    // on the main thread. Idempotent and safe to call again after a restart.
    fun attachToEngine(context: Context, messenger: BinaryMessenger)

    // Reverse of attachToEngine. Call when the host engine is torn down.
    fun detachFromEngine(messenger: BinaryMessenger)
}
```

You do not touch any internal Pigeon channels, core, or context classes - the whole setup is
behind these two calls.

## What attachToEngine does

`attachToEngine` is the manual counterpart of what the framework does automatically through
`GeneratedPluginRegistrant`. For the given engine it:

1. Initializes callkeep's application context (process-wide, idempotent).
2. Registers callkeep's background host channels on the engine's `BinaryMessenger`, so the Dart
   code running in that engine can report and control calls.
3. Marks callkeep as **hosted on an external engine** (see next section).

`detachFromEngine` unregisters those channels and clears the hosted mark.

## Behavior while hosted

While at least one host engine is attached, callkeep treats that engine as the owner of the
background work. When a call is reported, callkeep shows the incoming-call UI (notification,
ringtone, full-screen intent) but does **not** start its own background isolate for that call.

This matters because your host engine already owns the background work (for example an open
connection to your server). If callkeep also started its own isolate, you would get two parallel
workers for the same call.

When no host engine is attached, callkeep manages its own background isolate as usual (the FCM
push path). callkeep does not need to know which transport you use; the only thing it tracks is
whether a host engine is currently attached.

## Lifecycle

- Call `attachToEngine` once the host engine and its `BinaryMessenger` exist, and before the first
  incoming call is reported. The earliest safe place to wire this is `Application.onCreate`
  (see the example below), because it runs before any engine or service starts.
- Call `detachFromEngine` when the host engine is destroyed.
- Both calls are idempotent. Calling `attachToEngine` again after a service restart is fine.
- Call both on the main thread.
- Call them from the same process that hosts the engine (your app's main process).

## Integration example

The recommended pattern keeps your engine-owning service decoupled from callkeep: the service
exposes "engine ready" and "engine destroyed" seams, and the application wires callkeep into them.

### 1. Your engine-owning service exposes seams

```kotlin
class MyEngineService : Service() {
    override fun onCreate() {
        super.onCreate()
        // Create your own engine (no auto plugin registration on a background engine).
        val engine = FlutterEngine(applicationContext, null, /* ... */, /* automaticallyRegisterPlugins = */ false)
        engine.dartExecutor.executeDartCallback(/* your Dart entry point */)

        // Hand the engine's messenger to whoever wants to wire onto it.
        onEngineReady?.invoke(applicationContext, engine.dartExecutor.binaryMessenger)
        this.engine = engine
    }

    override fun onDestroy() {
        engine?.dartExecutor?.binaryMessenger?.let { onEngineDestroyed?.invoke(it) }
        engine?.destroy()
        super.onDestroy()
    }

    companion object {
        // Generic seams - this service knows nothing about callkeep.
        @Volatile var onEngineReady: ((Context, BinaryMessenger) -> Unit)? = null
        @Volatile var onEngineDestroyed: ((BinaryMessenger) -> Unit)? = null
    }
}
```

### 2. The application wires callkeep into those seams

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

With this in place, the host engine knows nothing about callkeep internals, and callkeep knows
nothing about your service or transport. The application is the only seam between them.

### 3. Report and control calls from the host engine (Dart)

Inside the Dart entry point that runs on your host engine, use callkeep's existing background
APIs. They reach the channels that `attachToEngine` registered:

```dart
// Report an incoming call so the OS shows the call UI:
await AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
  callId,
  CallkeepHandle.number(caller),
  displayName: displayName,
);

// Release a call (missed, declined, server hangup):
await AndroidCallkeepServices.backgroundPushNotificationService.releaseCall(callId);
```

## How it works (summary)

```text
Normal engine (Activity / Fragment / callkeep's own):
  Flutter framework -> GeneratedPluginRegistrant -> WebtritCallkeepPlugin.onAttachedToEngine
    -> init context + register channels   (automatic)

Host-owned engine (automaticallyRegisterPlugins = false):
  callkeep plugin is NOT registered automatically
  app calls WebtritCallkeep.attachToEngine(context, messenger)
    -> init context + register channels + mark "hosted on external engine"   (manual)

While hosted:
  report incoming call -> callkeep shows call UI, does NOT start its own isolate
  (the host engine owns the background work)
```

## Do and do not

- Do call `attachToEngine` before the first incoming call, on the main thread.
- Do pair `attachToEngine` with `detachFromEngine` on teardown.
- Do keep your engine-owning component decoupled: expose generic seams and wire callkeep from the
  application layer.
- Do not register callkeep's internal Pigeon channels yourself.
- Do not reference callkeep's internal classes (core, context, generated APIs) from your app.
