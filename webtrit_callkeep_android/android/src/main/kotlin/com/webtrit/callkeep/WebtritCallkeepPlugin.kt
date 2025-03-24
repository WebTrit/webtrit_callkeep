package com.webtrit.callkeep

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.services.ForegroundService
import com.webtrit.callkeep.services.PushNotificationService
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
    private var pushNotificationService: PushNotificationService? = null
    private var boundService: ForegroundService? = null
    private var serviceConnection: ServiceConnection? = null

    private var delegateLogsFlutterApi: PDelegateLogsFlutterApi? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Store binnyMessenger for later use if instance of the flutter engine belongs to main isolate OR call service isolate
        messenger = flutterPluginBinding.binaryMessenger
        flutterAssets = flutterPluginBinding.flutterAssets
        context = flutterPluginBinding.applicationContext

        ContextHolder.init(context)
        AssetHolder.init(context, flutterAssets)

        delegateLogsFlutterApi = PDelegateLogsFlutterApi(messenger)
        delegateLogsFlutterApi?.let { Log.add(it) }

        // Bootstrap isolate APIs
        PHostBackgroundSignalingIsolateBootstrapApi.setUp(messenger, BackgroundSignalingIsolateBootstrapApi(context))
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(
            messenger,
            BackgroundPushNotificationIsolateBootstrapApi(context)
        )

        PHostPermissionsApi.setUp(messenger, PermissionsApi(context))
        PHostSoundApi.setUp(messenger, SoundApi(context))
        PHostConnectionsApi.setUp(messenger, ConnectionsApi())
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        delegateLogsFlutterApi?.let { Log.remove(it) }
        delegateLogsFlutterApi = null

        PHostApi.setUp(this.messenger, null)

        ActivityHolder.setActivity(null)

        PHostBackgroundSignalingIsolateBootstrapApi.setUp(messenger, null)
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(messenger, null)

        PHostPermissionsApi.setUp(messenger, null)
        PHostSoundApi.setUp(messenger, null)
        PHostConnectionsApi.setUp(messenger, null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activityPluginBinding = binding

        Log.d(TAG, "onAttachedToActivity: Activity attached")

        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)

        ActivityHolder.setActivity(binding.activity)

        bindForegroundService(binding.activity)
    }

    override fun onDetachedFromActivity() {
        this.lifeCycle?.removeObserver(this)
        activityPluginBinding?.activity?.let {
            unbindAndStopForegroundService(it)
        }

        PHostApi.setUp(messenger, null)

        boundService = null
        serviceConnection = null
        ActivityHolder.setActivity(null)

    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        if (binding.service is PushNotificationService) {
            pushNotificationService = binding.service as? PushNotificationService

            val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
            val isolateDelegate = PDelegateBackgroundRegisterFlutterApi(messenger)

            pushNotificationService?.isolateCalkeepFlutterApi = delegate
            pushNotificationService?.isolatePushNotificationFlutterApi = isolateDelegate

            PHostBackgroundPushNotificationIsolateApi.setUp(messenger, pushNotificationService)
        }

        if (binding.service is SignalingService) {
            this.foregroundSocketService = binding.service as SignalingService

            val delegate = PDelegateBackgroundServiceFlutterApi(messenger)
            val isolateDelegate = PDelegateBackgroundRegisterFlutterApi(messenger)

            foregroundSocketService?.isolateCalkeepFlutterApi = delegate
            foregroundSocketService?.isolatePushNotificationFlutterApi = isolateDelegate

            PHostBackgroundSignalingIsolateApi.setUp(messenger, foregroundSocketService)
        }
    }

    override fun onDetachedFromService() {
        PHostBackgroundSignalingIsolateApi.setUp(messenger, null)
        foregroundSocketService = null
        pushNotificationService = null

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

        if (SignalingService.isRunning) {
            SignalingService.changeLifecycle(context, event)
        }

        when (event) {
            Lifecycle.Event.ON_STOP -> {
                activityPluginBinding?.activity?.let { unbindAndStopForegroundService(it) }
            }

            Lifecycle.Event.ON_START -> {
                if (serviceConnection == null) {
                    activityPluginBinding?.activity?.let { bindForegroundService(it) }
                }
            }

            else -> Unit
        }
    }

    private fun bindForegroundService(activity: Context) {
        val intent = Intent(activity, ForegroundService::class.java)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as ForegroundService.LocalBinder
                boundService = binder.getService()
                boundService?.flutterDelegateApi = PDelegateFlutterApi(messenger)
                PHostApi.setUp(messenger, boundService)

                Log.d(TAG, "bindForegroundService: Service connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
                Log.d(TAG, "bindForegroundService: Service disconnected")
            }
        }
        activity.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "bindForegroundService: Service binding started")
    }

    private fun unbindAndStopForegroundService(activity: Context) {
        serviceConnection?.let { conn ->
            try {
                activity.unbindService(conn)
                Log.d(TAG, "unbindAndStopForegroundService: Service unbound")

                val stopIntent = Intent(activity, ForegroundService::class.java)
                activity.stopService(stopIntent)
                Log.d(TAG, "unbindAndStopForegroundService: Service stopped")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "unbindAndStopForegroundService: Service not registered - ${e.message}")
            }
        }

        serviceConnection = null
        boundService = null
        PHostApi.setUp(messenger, null)
    }

    companion object {
        const val TAG = "WebtritCallkeepPlugin"
    }
}
