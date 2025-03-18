package com.webtrit.callkeep

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.IncomingCallService
import com.webtrit.callkeep.services.SignalingService

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterAssets
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.HiddenLifecycleReference
import io.flutter.embedding.engine.plugins.service.ServiceAware
import io.flutter.embedding.engine.plugins.service.ServicePluginBinding
import io.flutter.plugin.common.BinaryMessenger

/** WebtritCallkeepAndroidPlugin */
class WebtritCallkeepPlugin : FlutterPlugin, ActivityAware, ServiceAware, LifecycleEventObserver {
    private var activityPluginBinding: ActivityPluginBinding? = null
    private var lifeCycle: Lifecycle? = null

    private lateinit var messenger: BinaryMessenger
    private lateinit var flutterAssets: FlutterAssets
    private lateinit var context: Context

    private var foregroundSocketService: SignalingService? = null
    private var incomingCallService: IncomingCallService? = null

    private var delegateLogsFlutterApi: PDelegateLogsFlutterApi? = null


    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Store binnyMessenger for later use if instance of the flutter engine belongs to main isolate OR call service isolate
        messenger = flutterPluginBinding.binaryMessenger
        flutterAssets = flutterPluginBinding.flutterAssets
        context = flutterPluginBinding.applicationContext

        ContextHolder.init(context)
        AssetHolder.init(context, flutterAssets)

        PHostPermissionsApi.setUp(messenger, PigeonPermissionsApi(context))
        PHostSoundApi.setUp(messenger, PigeonSoundApi(context))
        PHostIsolateApi.setUp(messenger, PigeonIsolateApi(context))
        PHostConnectionsApi.setUp(messenger, PigeonConnectionsApi())

        delegateLogsFlutterApi = PDelegateLogsFlutterApi(messenger).apply { Log.add(this) }

        val flutterDelegateApi = PDelegateFlutterApi(messenger)
        PHostApi.setUp(messenger, PigeonActivityApi(context, flutterDelegateApi))
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        delegateLogsFlutterApi?.let { Log.remove(it) }

        PHostApi.setUp(this.messenger, null)

        ActivityHolder.setActivity(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activityPluginBinding = binding

        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)

        ActivityHolder.setActivity(binding.activity)
    }

    override fun onDetachedFromActivity() {
        this.lifeCycle?.removeObserver(this)
    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        if (binding.service is IncomingCallService) {
            incomingCallService = binding.service as? IncomingCallService

            val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
            val isolateDelegate = PDelegateBackgroundRegisterFlutterApi(messenger)

            incomingCallService?.isolateCalkeepFlutterApi = delegate
            incomingCallService?.isolatePushNotificationFlutterApi = isolateDelegate

            PHostBackgroundServiceApi.setUp(messenger, incomingCallService)
        }

        if (binding.service is SignalingService) {
            this.foregroundSocketService = binding.service as SignalingService

            val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
            val isolateDelegate = PDelegateBackgroundRegisterFlutterApi(messenger)

            foregroundSocketService?.isolateCalkeepFlutterApi = delegate
            foregroundSocketService?.isolatePushNotificationFlutterApi = isolateDelegate

            PHostBackgroundServiceApi.setUp(messenger, foregroundSocketService)
        }
    }

    override fun onDetachedFromService() {
        PHostBackgroundServiceApi.setUp(messenger, null)
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
        Log.d(TAG, "onStateChanged: Lifecycle event received - $event")
        ActivityHolder.setLifecycle(event)
        if (foregroundSocketService != null) {
            SignalingService.changeLifecycle(foregroundSocketService!!, event)
        }
    }

    companion object {
        const val TAG = "WebtritCallkeepPlugin"
    }
}
