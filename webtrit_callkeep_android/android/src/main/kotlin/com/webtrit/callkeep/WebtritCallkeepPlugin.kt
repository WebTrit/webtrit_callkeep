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
import com.webtrit.callkeep.services.incomming_call.IncomingCallService
import com.webtrit.callkeep.services.SignalingIsolateService
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
    private lateinit var assets: FlutterAssets
    private lateinit var context: Context

    private var signalingIsolateService: SignalingIsolateService? = null
    private var pushNotificationIsolateService: IncomingCallService? = null

    private var foregroundService: ForegroundService? = null
    private var serviceConnection: ServiceConnection? = null

    private var delegateLogsFlutterApi: PDelegateLogsFlutterApi? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Store binnyMessenger for later use if instance of the flutter engine belongs to main isolate OR call service isolate
        messenger = flutterPluginBinding.binaryMessenger
        assets = flutterPluginBinding.flutterAssets
        context = flutterPluginBinding.applicationContext

        ContextHolder.init(context)
        AssetHolder.init(context, assets)

        delegateLogsFlutterApi = PDelegateLogsFlutterApi(messenger).also { Log.add(it) }

        // Bootstrap isolate APIs
        BackgroundSignalingIsolateBootstrapApi(context).let {
            PHostBackgroundSignalingIsolateBootstrapApi.setUp(messenger, it)
        }
        BackgroundPushNotificationIsolateBootstrapApi(context).let {
            PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(messenger, it)
        }

        // Helper APIs
        PermissionsApi(context).let {
            PHostPermissionsApi.setUp(messenger, it)
        }
        SoundApi(context).let {
            PHostSoundApi.setUp(messenger, it)
        }
        ConnectionsApi().let {
            PHostConnectionsApi.setUp(messenger, it)
        }
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

        ActivityHolder.setActivity(binding.activity)

        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)

        bindForegroundService(binding.activity)
    }

    override fun onDetachedFromActivity() {
        ActivityHolder.setActivity(null)

        this.lifeCycle?.removeObserver(this)

        activityPluginBinding?.activity?.let { unbindAndStopForegroundService(it) }
        PHostApi.setUp(messenger, null)

        foregroundService = null
        serviceConnection = null
    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        // Create communication bridge between the service and the push notification isolate
        if (binding.service is IncomingCallService) {
            pushNotificationIsolateService = binding.service as? IncomingCallService

            pushNotificationIsolateService?.establishFlutterCommunication(
                PDelegateBackgroundServiceFlutterApi(messenger),
                PDelegateBackgroundRegisterFlutterApi(messenger)
            )

            PHostBackgroundPushNotificationIsolateApi.setUp(
                messenger,
                pushNotificationIsolateService?.getCallLifecycleHandler()
            )
        }

        // Create communication bridge between the service and the signaling isolate
        if (binding.service is SignalingIsolateService) {
            this.signalingIsolateService = binding.service as SignalingIsolateService

            PDelegateBackgroundServiceFlutterApi(messenger).let {
                signalingIsolateService?.isolateCalkeepFlutterApi = it
            }

            PDelegateBackgroundRegisterFlutterApi(messenger).let {
                signalingIsolateService?.isolateSignalingFlutterApi = it
            }

            PHostBackgroundSignalingIsolateApi.setUp(messenger, signalingIsolateService)
        }
    }

    override fun onDetachedFromService() {
        PHostBackgroundSignalingIsolateApi.setUp(messenger, null)
        PHostBackgroundPushNotificationIsolateApi.setUp(messenger, null)

        signalingIsolateService = null
        pushNotificationIsolateService = null
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

        // Notify the signaling service about the lifecycle event for correct handling of the signaling connection
        if (SignalingIsolateService.isRunning) {
            SignalingIsolateService.changeLifecycle(context, event)
        }

        // When the app is in the background, the service should be stopped. However, there is an unresolved issue if a call starts from the activity and the app is minimized, causing incomplete event handling.
        // To handle events when the app is minimized, we allow the service to remain active for as long as possible to manage recent events.
        // Currently, this can throw a BackgroundServiceStartNotAllowedException when the activity is in the background and the connection service emits an event.
        // To handle events when the app is in the background, we use isolates, so we do not stop the service to correctly handle recent events from the activity.

        // if (event == Lifecycle.Event.ON_STOP) {
        //     activityPluginBinding?.activity?.let { unbindAndStopForegroundService(it) }
        // }
        // if (event == Lifecycle.Event.ON_START && serviceConnection == null) {
        //     activityPluginBinding?.activity?.let {
        //         bindForegroundService(it)
        //     }
        // }
    }

    private fun bindForegroundService(activity: Context) {
        val intent = Intent(activity, ForegroundService::class.java)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as ForegroundService.LocalBinder
                foregroundService = binder.getService()
                foregroundService?.flutterDelegateApi = PDelegateFlutterApi(messenger)
                PHostApi.setUp(messenger, foregroundService)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                foregroundService = null
            }
        }
        activity.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    private fun unbindAndStopForegroundService(activity: Context) {
        serviceConnection?.let { conn ->
            try {
                activity.unbindService(conn)
                val stopIntent = Intent(activity, ForegroundService::class.java)
                activity.stopService(stopIntent)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "unbindAndStopForegroundService: Service not registered - ${e.message}")
            }
        }

        serviceConnection = null
        foregroundService = null
        PHostApi.setUp(messenger, null)
    }

    companion object {
        const val TAG = "WebtritCallkeepPlugin"
    }
}
