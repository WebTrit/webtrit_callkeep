package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.ContextHolder
import io.flutter.plugin.common.BinaryMessenger
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Public entry point for hosting callkeep on a Flutter engine that callkeep did not create.
 *
 * A host-owned engine (for example a foreground service or another headless engine) is usually
 * built with `automaticallyRegisterPlugins = false`, so that heavy plugins are not initialized on
 * a background engine. As a side effect [WebtritCallkeepPlugin.onAttachedToEngine] never runs for
 * such an engine, so callkeep's application context and its background host channels are not set
 * up there.
 *
 * [attachToEngine] is the manual counterpart to that automatic registration: call it once when the
 * host engine's [BinaryMessenger] is available. It performs the engine-scoped setup callkeep needs
 * in a background context and keeps callkeep's internal Pigeon channels hidden from the caller.
 *
 * While a host engine is attached, callkeep treats that engine as the owner of background work and
 * does not start its own incoming-call isolate: it only shows the call UI. Pair every
 * [attachToEngine] with a [detachFromEngine] when the host engine is torn down so callkeep can
 * resume managing its own background work.
 *
 * The calls are idempotent and safe across host-engine restarts.
 */
object WebtritCallkeep {
    // Messengers of host-provided engines currently attached via [attachToEngine]. While this set
    // is non-empty, a host engine owns the background work and callkeep skips its own isolate.
    private val hostEngines = CopyOnWriteArraySet<BinaryMessenger>()

    /**
     * True while callkeep is hosted on at least one host-provided engine. Used internally to decide
     * whether callkeep should start its own incoming-call background isolate.
     */
    internal val isHostedOnExternalEngine: Boolean
        get() = hostEngines.isNotEmpty()

    fun attachToEngine(
        context: Context,
        messenger: BinaryMessenger,
    ) {
        ContextHolder.init(context)
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(
            messenger,
            BackgroundPushNotificationIsolateBootstrapApi(context),
        )
        PHostBackgroundPushNotificationIsolateApi.setUp(
            messenger,
            ExternalEngineCallApi(),
        )
        hostEngines.add(messenger)
    }

    fun detachFromEngine(messenger: BinaryMessenger) {
        hostEngines.remove(messenger)
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(messenger, null)
        PHostBackgroundPushNotificationIsolateApi.setUp(messenger, null)
    }
}
