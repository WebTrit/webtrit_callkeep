package com.webtrit.callkeep.connection

import android.net.Uri
import android.telecom.Connection
import android.telecom.VideoProfile
import android.util.Log
import android.view.Surface

class PhoneVideoProvider : Connection.VideoProvider() {
    override fun onSetCamera(p0: String?) {
        Log.d(LOG_TAG, "onSetCamera")
    }

    override fun onSetPreviewSurface(p0: Surface?) {
        Log.d(LOG_TAG, "onSetPreviewSurface")
    }

    override fun onSetDisplaySurface(p0: Surface?) {
        Log.d(LOG_TAG, "onSetDisplaySurface")
    }

    override fun onSetDeviceOrientation(p0: Int) {
        Log.d(LOG_TAG, "onSetDeviceOrientation")
    }

    override fun onSetZoom(p0: Float) {
        Log.d(LOG_TAG, "onSetZoom")
    }

    override fun onSendSessionModifyRequest(p0: VideoProfile?, p1: VideoProfile?) {
        Log.d(LOG_TAG, "onSendSessionModifyRequest")
    }

    override fun onSendSessionModifyResponse(p0: VideoProfile?) {
        Log.d(LOG_TAG, "onSendSessionModifyResponse")
    }

    override fun onRequestCameraCapabilities() {
        Log.d(LOG_TAG, "onRequestCameraCapabilities")
    }

    override fun onRequestConnectionDataUsage() {
        Log.d(LOG_TAG, "onRequestConnectionDataUsage")
    }

    override fun onSetPauseImage(p0: Uri?) {
        Log.d(LOG_TAG, "onSetPauseImage")
    }

    companion object {
        private const val LOG_TAG = "PhoneVideoProvider"
    }
}
