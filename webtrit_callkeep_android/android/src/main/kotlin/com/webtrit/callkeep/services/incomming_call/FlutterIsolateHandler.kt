package com.webtrit.callkeep.services.incomming_call

import android.app.Service
import android.content.Context
import android.os.PowerManager
import com.webtrit.callkeep.common.FlutterEngineHelper
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate

interface IsolateInitializer {
    fun start()
}

class FlutterIsolateHandler(
    private val context: Context,
    private val service: Service,
    private val onStart: (() -> Unit)? = null
) : IsolateInitializer {

    private var flutterEngineHelper: FlutterEngineHelper? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun start() {
        acquireWakeLock()

        if (flutterEngineHelper == null) {
            val callbackHandle = StorageDelegate.IncomingCallService.getCallbackDispatcher(context)
            flutterEngineHelper = FlutterEngineHelper(context, callbackHandle, service)
        }

        flutterEngineHelper?.startOrAttachEngine()

        Log.d(TAG, "Flutter engine attached: ${flutterEngineHelper?.isEngineAttached}")
        onStart?.invoke()
    }

    val isReady: Boolean
        get() = flutterEngineHelper?.isEngineAttached == true

    fun cleanup() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null

        flutterEngineHelper?.detachAndDestroyEngine()
        flutterEngineHelper = null
    }

    private fun createWakeLockIfNeeded() {
        if (wakeLock == null) {
            wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.webtrit.callkeep:Isolate.WakeLock")
                .apply { setReferenceCounted(false) }
        }
    }

    private fun acquireWakeLock() {
        createWakeLockIfNeeded()
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    companion object {
        private const val TAG = "FlutterIsolateHandler"
    }
}