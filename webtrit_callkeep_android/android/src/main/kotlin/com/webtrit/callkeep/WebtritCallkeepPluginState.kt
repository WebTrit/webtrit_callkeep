package com.webtrit.callkeep

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.callkeep.foreground.ForegroundCallServiceReceiver
import io.flutter.plugin.common.BinaryMessenger

class WebtritCallkeepPluginState(
    val context: Context, private val messenger: BinaryMessenger
) {
    /** Handles interactions with the API when the application is active and in the foreground */
    private var pigeonActivityApi: PigeonActivityApi? = null

    /** Handles interactions with the API when the application is active and in the foreground */
    private var pigeonServiceApi: PigeonServiceApi? = null

    // Handles interactions with the isolate API
    private var pigeonIsolateApi: PigeonIsolateApi? = null

    /** Handles interactions with the logs host API */
    private var logsHostApi: PDelegateLogsFlutterApi? = null

    /** Handles interactions with the permissions host API */
    private var permissionsApi: PigeonPermissionsApi? = null

    /** Handles interactions with the sound host API */
    private var soundApi: PigeonSoundApi? = null

    /** Handles interactions with the connections host API */
    private var connectionsApi: PigeonConnectionsApi? = null

    /** Handles interactions with the isolate API */
    private var foregroundCallServiceReceiver: ForegroundCallServiceReceiver? = null

    var activity: Activity? = null

    fun initIsolateApi() {
        Log.i(TAG, "initIsolateApi $this")

        // Register isolate api for all plugin instances  possibility trigger call service isolate
        pigeonIsolateApi = PigeonIsolateApi(context)
        PHostIsolateApi.setUp(messenger, pigeonIsolateApi)

        connectionsApi = PigeonConnectionsApi()
        PHostConnectionsApi.setUp(messenger, connectionsApi)

        foregroundCallServiceReceiver =
            ForegroundCallServiceReceiver(PDelegateBackgroundRegisterFlutterApi(messenger), context)

        attachLogs()
    }

    private fun attachLogs() {
        logsHostApi = PDelegateLogsFlutterApi(messenger)
        Log.add(logsHostApi!!)
    }

    fun deAttachLogs() {
        logsHostApi?.let { Log.remove(it) }
    }

    fun initMainIsolateApi(activity: Activity) {
        Log.i(TAG, "initActivity $this")
        Log.d(TAG, "onStateChanged attached activity")
        this.activity = activity
        StorageDelegate.setActivityReady(activity, false)

        ActivityHolder.setActivity(activity)

        permissionsApi = PigeonPermissionsApi(context)
        PHostPermissionsApi.setUp(messenger, permissionsApi)

        soundApi = PigeonSoundApi(context)
        PHostSoundApi.setUp(messenger, soundApi)

        val flutterDelegateApi = PDelegateFlutterApi(messenger)
        pigeonActivityApi = PigeonActivityApi(context, flutterDelegateApi)
        PHostApi.setUp(messenger, pigeonActivityApi)
    }

    fun initBackgroundIsolateApi(context: Context) {
        Log.i(TAG, "initBackgroundIsolateApi $this")

        val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
        pigeonServiceApi = PigeonServiceApi(context, delegate)
        pigeonServiceApi?.register()
        PHostBackgroundServiceApi.setUp(messenger, pigeonServiceApi)

        foregroundCallServiceReceiver?.registerReceiver(context)
    }

    fun destroyService() {
        Log.i(TAG, "destroyService $this")

        pigeonServiceApi?.unregister()
        PHostBackgroundServiceApi.setUp(messenger, null)

        foregroundCallServiceReceiver?.unregisterReceiver(context)
    }

    fun detachActivity() {
        Log.i(TAG, "detachActivity $this")
        StorageDelegate.setActivityReady(context, false)
        ActivityHolder.setActivity(null)
        pigeonActivityApi?.detachActivity()

        permissionsApi = null
    }

    fun onDetach() {
        Log.i(TAG, "onDetach $this")
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
