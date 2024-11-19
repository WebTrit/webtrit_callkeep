package com.webtrit.callkeep

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.FlutterLog
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.callkeep.foreground.ForegroundCallServiceReceiver
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

    /** Handles interactions with the permissions host API */
    var permissionsApi: PigeonPermissionsApi? = null

    /** Handles interactions with the isolate API */
    var foregroundCallServiceReceiver: ForegroundCallServiceReceiver? = null;

    var activity: Activity? = null

    fun initIsolateApi() {
        FlutterLog.i(TAG, "initIsolateApi $this")

        // Register isolate api for all plugin instances  possibility trigger call service isolate
        pigeonIsolateApi = PigeonIsolateApi(context);

        PHostIsolateApi.setUp(messenger, pigeonIsolateApi)

        foregroundCallServiceReceiver =
            ForegroundCallServiceReceiver(PDelegateBackgroundRegisterFlutterApi(messenger), context)

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

        ActivityHolder.setActivity(activity)

        permissionsApi = PigeonPermissionsApi(context)
        PHostPermissionsApi.setUp(messenger, permissionsApi)

        val flutterDelegateApi = PDelegateFlutterApi(messenger)
        pigeonActivityApi = PigeonActivityApi(activity, flutterDelegateApi)
        PHostApi.setUp(messenger, pigeonActivityApi)
    }

    fun initBackgroundIsolateApi(context: Context) {
        FlutterLog.i(TAG, "initBackgroundIsolateApi $this")

        val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
        pigeonServiceApi = PigeonServiceApi(context, delegate)
        pigeonServiceApi?.register()
        PHostBackgroundServiceApi.setUp(messenger, pigeonServiceApi)

        foregroundCallServiceReceiver?.registerReceiver(context)
    }

    fun destroyService() {
        FlutterLog.i(TAG, "destroyService $this")

        pigeonServiceApi?.unregister()
        PHostBackgroundServiceApi.setUp(messenger, null)

        foregroundCallServiceReceiver?.unregisterReceiver(context)
    }

    fun detachActivity() {
        FlutterLog.i(TAG, "detachActivity $this")
        StorageDelegate.setActivityReady(context, false)
        ActivityHolder.setActivity(null)
        pigeonActivityApi?.detachActivity()

        permissionsApi = null;
    }

    fun onDetach() {
        FlutterLog.i(TAG, "onDetach $this")
    }

    fun onStateChanged(event: Lifecycle.Event) {
        Log.d(TAG, "onStateChanged $event")
        ActivityHolder.setLifecycle(event)
        ForegroundCallServiceReceiver.changeLifecycle(context, event)
    }

    companion object {
        private const val TAG = "WebtritCallkeepPluginState"
    }
}
