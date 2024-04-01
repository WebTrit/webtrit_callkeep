package com.webtrit.callkeep.webtrit_callkeep_android

import com.webtrit.callkeep.webtrit_callkeep_android.common.ApplicationData
import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.HiddenLifecycleReference
import io.flutter.plugin.common.BinaryMessenger

/** WebtritCallkeepAndroidPlugin */
class WebtritCallkeepPlugin : FlutterPlugin, ActivityAware, LifecycleEventObserver {
    private var activityPluginBinding: ActivityPluginBinding? = null
    private var state: FlutterState? = null
    private var lifeCycle: Lifecycle? = null

    // This function will be called twice, once for Activity and once for Service (FCM).
    // ActivityAware can be used to check if it's an activity, but ServiceAware doesn't function properly for Service (FCM).
    // Therefore, a workaround is implemented to unsubscribe from the broadcast receiver initialized in initService.
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val binaryMessage = flutterPluginBinding.binaryMessenger
        val applicationContext = flutterPluginBinding.applicationContext

        this.state = FlutterState(binaryMessage, applicationContext)

        this.state?.initService()
        this.state?.attachLogs()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.state?.initActivity(binding.activity)

        this.activityPluginBinding = binding

        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onDetachedFromActivity() {
        state?.detachActivity()
        this.lifeCycle?.removeObserver(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.state?.destroyService(binding.binaryMessenger)
        this.state?.deAttachLogs()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        ApplicationData.setCurrentActivityState(event)
    }
}

private class FlutterState(val messenger: BinaryMessenger, val context: Context) {
    /** Handles interactions with the API when the application is active and in the foreground */
    var pigeonActivityApi: PigeonActivityApi? = null

    /** Handles interactions with the API when the application is active and in the foreground */
    var pigeonServiceApi: PigeonServiceApi? = null

    /** Handles interactions with the logs host API */
    var logsHostApi: PDelegateLogsFlutterApi? = null;

    init {
        ApplicationData.init(context)
    }

    fun attachLogs() {
        logsHostApi = PDelegateLogsFlutterApi(messenger)
        FlutterLog.add(logsHostApi!!)
    }

    fun deAttachLogs() {
        logsHostApi?.let { FlutterLog.remove(it) }
    }

    fun initService() {
        FlutterLog.i(TAG, "initService $this")

        val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
        pigeonServiceApi = PigeonServiceApi(context, delegate)
        PHostBackgroundServiceApi.setUp(messenger, pigeonServiceApi)
    }

    fun destroyService(messenger: BinaryMessenger) {
        FlutterLog.i(TAG, "destroyService $this")

        pigeonServiceApi?.unregister()
        PHostBackgroundServiceApi.setUp(messenger, null)
    }

    fun initActivity(activity: Activity) {
        FlutterLog.i(TAG, "initActivity $this")

        ApplicationData.attachActivity(activity)

        val flutterDelegateApi = PDelegateFlutterApi(messenger)
        pigeonActivityApi = PigeonActivityApi(activity, flutterDelegateApi)
        PHostApi.setUp(messenger, pigeonActivityApi)

        destroyService(messenger)
    }

    fun detachActivity() {
        FlutterLog.i(TAG, "detachActivity $this")

        ApplicationData.detachActivity()
        pigeonActivityApi?.detachActivity()
    }

    companion object {
        private const val TAG = "WebtritCallkeepAndroidPlugin"
    }
}
