package com.webtrit.callkeep.services.incomming_call

import android.content.Context
import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.telecom.connection.PhoneConnectionService

interface CallConnectionController {
    fun answer(metadata: CallMetadata)
    fun hangUp(metadata: CallMetadata)
    fun tearDown()
}

class DefaultCallConnectionController(private val context: Context) : CallConnectionController {
    override fun answer(metadata: CallMetadata) {
        PhoneConnectionService.startAnswerCall(context, metadata)
    }

    override fun hangUp(metadata: CallMetadata) {
        PhoneConnectionService.startHungUpCall(context, metadata)
    }

    override fun tearDown() {
        PhoneConnectionService.tearDown(context)
    }
}