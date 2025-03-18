package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.StorageDelegate
import io.flutter.plugin.common.BinaryMessenger

class WebtritCallkeepPluginState(
    val context: Context, val messenger: BinaryMessenger
) {
    /** Handles interactions with the API when the application is active and in the foreground */
    private var pigeonActivityApi: PigeonActivityApi? = null

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


    fun initIsolateApi() {
        Log.i(TAG, "initIsolateApi $this")

        permissionsApi = PigeonPermissionsApi(context)
        PHostPermissionsApi.setUp(messenger, permissionsApi)

        soundApi = PigeonSoundApi(context)
        PHostSoundApi.setUp(messenger, soundApi)

        // Register isolate api for all plugin instances  possibility trigger call service isolate
        pigeonIsolateApi = PigeonIsolateApi(context)
        PHostIsolateApi.setUp(messenger, pigeonIsolateApi)

        connectionsApi = PigeonConnectionsApi()
        PHostConnectionsApi.setUp(messenger, connectionsApi)

        logsHostApi = PDelegateLogsFlutterApi(messenger)
        Log.add(logsHostApi!!)
    }

    fun deAttachLogs() {
        logsHostApi?.let { Log.remove(it) }
    }

    fun initMainIsolateApi() {
        val flutterDelegateApi = PDelegateFlutterApi(messenger)
        pigeonActivityApi = PigeonActivityApi(context, flutterDelegateApi)
        PHostApi.setUp(messenger, pigeonActivityApi)
    }

    fun initBackgroundIsolateApi(context: Context) {
        Log.i(TAG, "initBackgroundIsolateApi $this")
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

    companion object {
        private const val TAG = "WebtritCallkeepPluginState"
    }
}
