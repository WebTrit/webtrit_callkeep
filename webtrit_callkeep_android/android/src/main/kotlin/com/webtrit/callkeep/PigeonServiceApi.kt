package com.webtrit.callkeep

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.webtrit.callkeep.api.CallkeepApiProvider
import com.webtrit.callkeep.api.background.BackgroundCallkeepApi
import com.webtrit.callkeep.common.helpers.Telecom
import com.webtrit.callkeep.common.helpers.registerCustomReceiver
import com.webtrit.callkeep.common.models.CallMetadata
import com.webtrit.callkeep.common.models.CallPaths
import com.webtrit.callkeep.common.models.toCallHandle
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.common.ContextHolder

class PigeonServiceApi(
    private val context: Context,
    private val api: PDelegateBackgroundServiceFlutterApi,
) : PHostBackgroundServiceApi, BroadcastReceiver() {
    private val connectionService: BackgroundCallkeepApi =
        CallkeepApiProvider.getBackgroundCallkeepApi(context, api)

    init {
        Telecom.registerPhoneAccount(context)
    }

    fun register() {
        FlutterLog.i(TAG, "register receiver ")

        // Register actions from notification
        val notificationsReceiverFilter = IntentFilter()
        notificationsReceiverFilter.addAction(ReportAction.Hangup.action)
        notificationsReceiverFilter.addAction(ReportAction.Answer.action)
        context.registerCustomReceiver(this, notificationsReceiverFilter)

        // Register background service
        connectionService.register()
    }


    fun unregister() {
        FlutterLog.i(TAG, "unregister receiver")
        try {
            connectionService.unregister();
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
    }


    override fun onReceive(context: Context, intent: Intent) {
        val callMetaData = CallMetadata.fromBundle(intent.extras!!)

        when (intent.action) {
            ReportAction.Hangup.action -> connectionService.endCall(callMetaData)
            ReportAction.Answer.action -> connectionService.answer(callMetaData)
        }
    }

    override fun incomingCall(
        callId: String,
        handle: PHandle,
        displayName: String?,
        hasVideo: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        FlutterLog.i(TAG, "incomingCall $callId")

        val callPath = StorageDelegate.getIncomingPath(context)
        val rootPath = StorageDelegate.getRootPath(context)
        val ringtonePath = StorageDelegate.getRingtonePath(context)

        val callMetaData = CallMetadata(
            callId = callId,
            handle = handle.toCallHandle(),
            displayName = displayName,
            hasVideo = hasVideo,
            paths = CallPaths(callPath, rootPath),
            ringtonePath = ringtonePath,
            createdTime = System.currentTimeMillis()
        )

        //TODO: Fuzzy logic between two classes, in some methods the callback is called in that class in some we delegate to the implementation. Make it more clear.
        connectionService.incomingCall(callMetaData, callback)
    }


    override fun endCall(
        callId: String, callback: (Result<Unit>) -> Unit
    ) {
        FlutterLog.i(TAG, "endCall $callId")

//        ApplicationData.getActivity()?.finish()

        val callMetaData = CallMetadata(callId = callId)
        connectionService.hungUp(callMetaData, callback)

        //TODO: Should wait until all connections are removed, because the current implementation does not guarantee this
        callback.invoke(Result.success(Unit))
    }


    override fun endAllCalls(callback: (Result<Unit>) -> Unit) {
        FlutterLog.i(TAG, "endAllCalls")

        connectionService.endAllCalls()

        //TODO: Should wait until all connections are removed, because the current implementation does not guarantee this
        callback.invoke(Result.success(Unit))
    }

    enum class ReportAction {
        Hangup, Answer;

        val action: String
            get() = ContextHolder.appUniqueKey + name
    }

    companion object {
        const val TAG = "PigeonServiceApi";
    }
}
