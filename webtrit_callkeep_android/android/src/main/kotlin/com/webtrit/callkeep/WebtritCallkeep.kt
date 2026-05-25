package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.ContextHolder
import io.flutter.plugin.common.BinaryMessenger

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
 * The call is idempotent ([ContextHolder] init is a no-op once initialized; channel registration
 * replaces any previous handler), so it is safe across host-engine restarts. Pair it with
 * [detachFromEngine] to unregister explicitly; when the host engine is destroyed the channels are
 * torn down with it.
 */
object WebtritCallkeep {
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
    }

    fun detachFromEngine(messenger: BinaryMessenger) {
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(messenger, null)
        PHostBackgroundPushNotificationIsolateApi.setUp(messenger, null)
    }
}
