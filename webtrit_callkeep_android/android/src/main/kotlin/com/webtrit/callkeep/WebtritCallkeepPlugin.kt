package com.webtrit.callkeep

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.webtrit.callkeep.common.ActivityHolder
import com.webtrit.callkeep.common.AssetHolder
import com.webtrit.callkeep.common.ContextHolder
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.callkeep.foreground.ForegroundCallService

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
    var service: ForegroundCallService? = null

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
        if (binding.service !is ForegroundCallService) return

        this.service = binding.service as ForegroundCallService

        this.state.initBackgroundIsolateApi(binding.service.applicationContext)
    }

    override fun onDetachedFromService() {
        this.state.destroyService()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.lifeCycle?.removeObserver(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        lifeCycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
        lifeCycle!!.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        state.onStateChanged(event)
    }

    companion object {
        const val TAG = "WebtritCallkeepPlugin"
    }
}
