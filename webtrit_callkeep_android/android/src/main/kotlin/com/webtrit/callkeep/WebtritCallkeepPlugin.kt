package com.webtrit.callkeep

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.IncomingCallService
import com.webtrit.callkeep.services.SignalingService

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.HiddenLifecycleReference
import io.flutter.embedding.engine.plugins.service.ServiceAware
import io.flutter.embedding.engine.plugins.service.ServicePluginBinding

/** WebtritCallkeepAndroidPlugin */
class WebtritCallkeepPlugin : FlutterPlugin, ActivityAware, ServiceAware, LifecycleEventObserver {
    private var activityPluginBinding: ActivityPluginBinding? = null
    private var lifeCycle: Lifecycle? = null
    var foregroundSocketService: SignalingService? = null
    var incomingCallService: IncomingCallService? = null

    private lateinit var state: WebtritCallkeepPluginState

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Store binnyMessenger for later use if instance of the flutter engine belongs to main isolate OR call service isolate
        val messenger = flutterPluginBinding.binaryMessenger
        val assets = flutterPluginBinding.flutterAssets
        val context = flutterPluginBinding.applicationContext

        ContextHolder.init(context);
        AssetHolder.init(context, assets)

        state = WebtritCallkeepPluginState(context, messenger).apply {
            initIsolateApi()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.state.deAttachLogs()
        this.state.onDetach()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activityPluginBinding = binding

        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)

        StorageDelegate.setActivityReady(binding.activity, false)
        ActivityHolder.setActivity(binding.activity)

        this.state.initMainIsolateApi()
    }

    override fun onDetachedFromActivity() {
        state.detachActivity()
        this.lifeCycle?.removeObserver(this)
    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        if (binding.service is IncomingCallService) {
            incomingCallService = binding.service as? IncomingCallService
            Log.d(TAG, "onAttachedToService")

            val delegate = PDelegateBackgroundServiceFlutterApi(state?.messenger ?: return)
            val isolateDelegate = PDelegateBackgroundRegisterFlutterApi(state?.messenger!!);

            incomingCallService?.isolateCalkeepFlutterApi = delegate
            incomingCallService?.isolatePushNotificationFlutterApi = isolateDelegate;

            PHostBackgroundServiceApi.setUp(state?.messenger!!, incomingCallService)
        }

        if (binding.service is SignalingService) {
            this.foregroundSocketService = binding.service as SignalingService
            this.state.initBackgroundIsolateApi(binding.service.applicationContext)

            val delegate = PDelegateBackgroundServiceFlutterApi(state.messenger)
            val isolateDelegate = PDelegateBackgroundRegisterFlutterApi(state.messenger)

            foregroundSocketService?.isolateCalkeepFlutterApi = delegate
            foregroundSocketService?.isolatePushNotificationFlutterApi = isolateDelegate

            PHostBackgroundServiceApi.setUp(state.messenger, foregroundSocketService)
        }
    }

    override fun onDetachedFromService() {
        PHostBackgroundServiceApi.setUp(state.messenger, null)
        foregroundSocketService = null
        incomingCallService = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.lifeCycle?.removeObserver(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        ActivityHolder.setLifecycle(event)
        if (foregroundSocketService != null) {
            SignalingService.changeLifecycle(foregroundSocketService!!, event)
        }
    }

    companion object {
        const val TAG = "WebtritCallkeepPlugin"
    }
}
