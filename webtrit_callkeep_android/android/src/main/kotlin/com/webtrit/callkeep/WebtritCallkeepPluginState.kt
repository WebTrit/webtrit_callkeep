package com.webtrit.callkeep

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.ApplicationData
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.ForegroundCallServiceReceiver
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger

class WebtritCallkeepPluginState(
    val context: Context, val messenger: BinaryMessenger, val assets: FlutterPlugin.FlutterAssets
) {
    /** Handles interactions with the API when the application is active and in the foreground */
    var pigeonActivityApi: PigeonActivityApi? = null

    /** Handles interactions with the API when the application is active and in the foreground */
    var pigeonServiceApi: PigeonServiceApi? = null

    // Handles interactions with the isolate API
    var pigeonIsolateApi: PigeonIsolateApi? = null

    /** Handles interactions with the logs host API */
    var logsHostApi: PDelegateLogsFlutterApi? = null

    /** Handles interactions with the isolate API */
    var foregroundCallServiceReceiver: ForegroundCallServiceReceiver? = null;

    var activity: Activity? = null

    fun initIsolateApi() {
        FlutterLog.i(TAG, "initIsolateApi $this")

        ApplicationData.init(context, assets);

        // Register isolate api for all plugin instances  possibility trigger call service isolate
        pigeonIsolateApi = PigeonIsolateApi(context);

        PHostIsolateApi.setUp(messenger, pigeonIsolateApi)

        attachLogs()
    }


    fun attachLogs() {
        logsHostApi = PDelegateLogsFlutterApi(messenger)
        FlutterLog.add(logsHostApi!!)
    }

    fun deAttachLogs() {
        logsHostApi?.let { FlutterLog.remove(it) }
    }


    fun initMainIsolateApi(activity: Activity) {
        FlutterLog.i(TAG, "initActivity $this")
        Log.d(TAG, "onStateChanged attached activity")
        this.activity = activity;
        StorageDelegate.setActivityReady(activity, false)

        ApplicationData.attachActivity(activity)


        val flutterDelegateApi = PDelegateFlutterApi(messenger)
        pigeonActivityApi = PigeonActivityApi(activity, flutterDelegateApi)
        PHostApi.setUp(messenger, pigeonActivityApi)
    }

    fun initBackgroundIsolateApi(context: Context) {
        FlutterLog.i(TAG, "initService $this")

        // TODO(Serdun): add query to store task is service not ready
        foregroundCallServiceReceiver =
            ForegroundCallServiceReceiver(PDelegateBackgroundRegisterFlutterApi(messenger), context).apply {
                registerReceiver(context)
            }

        val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
        pigeonServiceApi = PigeonServiceApi(context, delegate)

        PHostBackgroundServiceApi.setUp(messenger, pigeonServiceApi)
    }

    fun destroyService() {
        FlutterLog.i(TAG, "destroyService $this")

        PHostBackgroundServiceApi.setUp(messenger, null)
        pigeonServiceApi?.unregister()

        foregroundCallServiceReceiver?.unregisterReceiver(context)

    }

    fun detachActivity() {
        FlutterLog.i(TAG, "detachActivity $this")
        StorageDelegate.setActivityReady(context, false)
        ApplicationData.detachActivity()
        pigeonActivityApi?.detachActivity()
    }

    fun onDetach() {
        FlutterLog.i(TAG, "onDetach $this")

    }

    fun onStateChanged(event: Lifecycle.Event) {
        Log.d(TAG, "onStateChanged $event")
        ApplicationData.setCurrentActivityState(event)
        ForegroundCallServiceReceiver.changeLifecycle(context, event)
    }

    companion object {
        private const val TAG = "WebtritCallkeepPluginState"

    }
}
