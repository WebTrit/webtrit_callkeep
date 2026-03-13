package com.webtrit.callkeep

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.Log
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.setShowWhenLockedCompat
import com.webtrit.callkeep.common.setTurnScreenOnCompat
import com.webtrit.callkeep.services.broadcaster.ActivityLifecycleBroadcaster
import com.webtrit.callkeep.services.services.connection.PhoneConnectionService
import com.webtrit.callkeep.services.services.foreground.ForegroundService
import com.webtrit.callkeep.services.services.incoming_call.IncomingCallService
import com.webtrit.callkeep.services.services.signaling.SignalingIsolateService
import io.flutter.embedding.engine.plugins.FlutterPlugin
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
    private lateinit var context: Context

    private var signalingIsolateService: SignalingIsolateService? = null
    private var pushNotificationIsolateService: IncomingCallService? = null

    private var foregroundService: ForegroundService? = null
    private var serviceConnection: ServiceConnection? = null

    private var delegateLogsFlutterApi: PDelegateLogsFlutterApi? = null
    private var permissionsApi: PermissionsApi? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Store binnyMessenger for later use if instance of the flutter engine belongs to main isolate OR call service isolate
        messenger = flutterPluginBinding.binaryMessenger
        context = flutterPluginBinding.applicationContext

        ContextHolder.init(context)
        AssetHolder.init(context)

        // Bootstrap isolate APIs
        BackgroundSignalingIsolateBootstrapApi(context).let {
            PHostBackgroundSignalingIsolateBootstrapApi.setUp(messenger, it)
        }
        BackgroundPushNotificationIsolateBootstrapApi(context).let {
            PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(messenger, it)
        }
        SmsReceptionConfigBootstrapApi(context).let {
            PHostSmsReceptionConfigApi.setUp(messenger, it)
        }

        // Helper APIs

        permissionsApi = PermissionsApi(context)
        permissionsApi?.let {
            PHostPermissionsApi.setUp(messenger, it)
        }

        delegateLogsFlutterApi = PDelegateLogsFlutterApi(messenger).also { Log.add(it) }
        Log.i(TAG, "onAttachedToEngine id:${flutterPluginBinding.hashCode()}")

        DiagnosticsApi(context).let {
            PHostDiagnosticsApi.setUp(messenger, it)
        }

        SoundApi(context).let {
            PHostSoundApi.setUp(messenger, it)
        }
        ConnectionsApi().let {
            PHostConnectionsApi.setUp(messenger, it)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.i(TAG, "onDetachedFromEngine id:${binding.hashCode()}")
        delegateLogsFlutterApi?.let { Log.remove(it) }
        delegateLogsFlutterApi = null

        PHostPermissionsApi.setUp(messenger, null)
        permissionsApi = null

        PHostApi.setUp(this.messenger, null)

        PHostBackgroundSignalingIsolateBootstrapApi.setUp(messenger, null)
        PHostBackgroundPushNotificationIsolateBootstrapApi.setUp(messenger, null)

        PHostDiagnosticsApi.setUp(messenger, null)
        PHostSoundApi.setUp(messenger, null)
        PHostConnectionsApi.setUp(messenger, null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.i(TAG, "onAttachedToActivity id:${binding.hashCode()}")
        this.activityPluginBinding = binding

        ActivityHolder.setActivity(binding.activity)

        ActivityControlApi(binding.activity).let {
            PHostActivityControlApi.setUp(messenger, it)
        }

        permissionsApi?.let {
            binding.addRequestPermissionsResultListener(it)
        }

        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)

        // Launch the signaling service manually on Android 15+ (API 34 / UPSIDE_DOWN_CAKE) if enabled.
        //
        // On Android 15 and above, the system no longer allows ForegroundServices of type "phone call"
        // to be started from BOOT_COMPLETED or similar system broadcasts. As a result, the service must
        // be started explicitly from the app lifecycle — in this case, from the plugin/activity attachment.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (StorageDelegate.SignalingService.isSignalingServiceEnabled(binding.activity)) {
                try {
                    val intent = Intent(binding.activity, SignalingIsolateService::class.java)
                    context.startForegroundService(intent)
                    Log.i(TAG, "SignalingIsolateService started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start SignalingIsolateService: ${e.message}")
                }
            }
        }
        bindForegroundService(binding.activity)
    }

    override fun onDetachedFromActivity() {
        Log.i(TAG, "onDetachedFromActivity id:${activityPluginBinding?.hashCode()}")
        ActivityHolder.setActivity(null)

        permissionsApi?.let {
            activityPluginBinding?.removeRequestPermissionsResultListener(it)
        }

        this.lifeCycle?.removeObserver(this)

        activityPluginBinding?.activity?.let { unbindAndStopForegroundService(it) }
        PHostApi.setUp(messenger, null)
        PHostActivityControlApi.setUp(messenger, null)

        foregroundService = null
        serviceConnection = null
    }

    override fun onAttachedToService(binding: ServicePluginBinding) {
        Log.i(TAG, "onAttachedToService id:${binding.hashCode()}")
        // Create communication bridge between the service and the push notification isolate
        if (binding.service is IncomingCallService) {
            Log.i(TAG, "IncomingCallService detected, setting up communication bridge")
            pushNotificationIsolateService = binding.service as? IncomingCallService

            pushNotificationIsolateService?.establishFlutterCommunication(
                PDelegateBackgroundServiceFlutterApi(messenger),
                PDelegateBackgroundRegisterFlutterApi(messenger)
            )

            PHostBackgroundPushNotificationIsolateApi.setUp(
                messenger, pushNotificationIsolateService?.getCallLifecycleHandler()
            )
        }

        // Create communication bridge between the service and the signaling isolate
        if (binding.service is SignalingIsolateService) {
            Log.i(TAG, "SignalingIsolateService detected, setting up communication bridge")
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
        Log.i(TAG, "onDetachedFromService id:${activityPluginBinding?.hashCode()}")
        PHostBackgroundSignalingIsolateApi.setUp(messenger, null)
        PHostBackgroundPushNotificationIsolateApi.setUp(messenger, null)

        signalingIsolateService = null
        pushNotificationIsolateService = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        Log.i(TAG, "onDetachedFromActivityForConfigChanges id:${activityPluginBinding?.hashCode()}")
        this.lifeCycle?.removeObserver(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.i(TAG, "onReattachedToActivityForConfigChanges id:${binding.hashCode()}")
        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        Log.d(
            TAG,
            "onStateChanged: Lifecycle event received - $event, activity: ${activityPluginBinding?.activity}"
        )
        ActivityLifecycleBroadcaster.setValue(context, event)

        /**
         * This block is essential for the incoming call flow on the lock screen.
         *
         * It manages the `setShowWhenLocked` and `setTurnScreenOn` permissions
         * to reliably show the Activity. On modern Android versions,
         * the `setFullScreenIntent` alone is often not enough to wake the
         * device and show the Activity; these flags are required.
         *
         * We set these flags here programmatically *only when a call is active*,
         * rather than in the `AndroidManifest.xml`. If set in the Manifest,
         * the Activity would *always* attempt to show on the lock screen,
         * which is not the desired behavior.
         *
         * `ON_START` is our only reliable "checkpoint" that fires every
         * time the Activity becomes visible. This logic handles two scenarios:
         *
         * 1. **(Activate)** If the Activity starts *during* an active call,
         * `hasActiveConnections` will be `true`, and we force
         * the Activity over the lock screen and turn the screen on.
         *
         * 2. **(Clear)** If the Activity starts *after* a call has
         * ended (or the user is just opening the app normally),
         * `hasActiveConnections` will be `false`. This guarantees
         * that we clear the flags.
         *
         * We don't use `ON_STOP` for clearing because, **on some devices**,
         * it's called almost immediately after `ON_START` on the lock screen,
         * which leads to a race condition (setting flags to `true` then
         * immediately to `false`). This `ON_START`-only approach also solves
         * the problem where flags could get "stuck" in `true` (e.g., if
         * the app was force-stopped).
         */
        if (event == Lifecycle.Event.ON_START) {
            val connections = PhoneConnectionService.connectionManager.getConnections()
            val hasActiveConnections = connections.isNotEmpty()
            Log.i(
                TAG,
                "onStateChanged: ON_START. Has active connections: $hasActiveConnections (${connections.size})"
            )
            activityPluginBinding?.activity?.setShowWhenLockedCompat(hasActiveConnections)
            activityPluginBinding?.activity?.setTurnScreenOnCompat(hasActiveConnections)
        }
    }

    private fun bindForegroundService(activity: Context) {
        val intent = Intent(activity, ForegroundService::class.java)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i(TAG, "ForegroundService connected: ${service?.javaClass?.name}")
                val binder = service as ForegroundService.LocalBinder
                foregroundService = binder.getService()
                foregroundService?.flutterDelegateApi = PDelegateFlutterApi(messenger)
                PHostApi.setUp(messenger, foregroundService)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "ForegroundService disconnected")
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
                Log.i(TAG, "unbindAndStopForegroundService: ForegroundService unbound and stopped")
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
