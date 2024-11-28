package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.StorageDelegate
import com.webtrit.callkeep.managers.AudioManager

class PigeonSoundApi(
    private val context: Context
) : PHostSoundApi {

    private val audioManager = AudioManager(context)


    override fun playRingbackSound(callback: (Result<Unit>) -> Unit) {
        val assetPath = StorageDelegate.getRingbackPath(context)
        if (assetPath != null) audioManager.startRingback(assetPath)
        callback(Result.success(Unit))
    }

    override fun stopRingbackSound(callback: (Result<Unit>) -> Unit) {
        audioManager.stopRingback()
        callback(Result.success(Unit))
    }
}
