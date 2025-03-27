package com.webtrit.callkeep.services.helpers

import android.app.Service
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.services.SignalingIsolateService

interface IsolateLaunchPolicy {
    fun shouldLaunch(): Boolean
}

class DefaultIsolateLaunchPolicy(private val service: Service) : IsolateLaunchPolicy {
    override fun shouldLaunch(): Boolean {
        val isolate = IsolateSelector.getIsolateType()
        val signalingRunning = SignalingIsolateService.isRunning
        val launchEvenIfAppIsOpen =
            StorageDelegate.IncomingCallService.isLaunchBackgroundIsolateEvenIfAppIsOpen(service)

        return launchEvenIfAppIsOpen || (isolate == IsolateType.BACKGROUND && !signalingRunning)
    }
}