package com.webtrit.callkeep.services.common

import android.app.Service
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.services.signaling.SignalingIsolateService

interface IsolateLaunchPolicy {
    fun shouldLaunch(): Boolean
}

/**
 * Default implementation of [IsolateLaunchPolicy] that determines whether to launch the isolate
 * based on the current isolate type, whether the signaling is running, and the user's preference
 * for launching the background isolate even if the app is open.
 *
 * @param service The service instance used to access shared preferences.
 */
class DefaultIsolateLaunchPolicy(private val service: Service) : IsolateLaunchPolicy {
    override fun shouldLaunch(): Boolean {
        val isolate = IsolateSelector.getIsolateType()
        val signalingRunning = SignalingIsolateService.isRunning
        val launchEvenIfAppIsOpen =
            StorageDelegate.IncomingCallService.isLaunchBackgroundIsolateEvenIfAppIsOpen(service)

        return launchEvenIfAppIsOpen || (isolate == IsolateType.BACKGROUND && !signalingRunning)
    }
}