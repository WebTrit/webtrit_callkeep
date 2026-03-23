package com.webtrit.callkeep.services.services.incoming_call

import com.webtrit.callkeep.models.CallMetadata
import com.webtrit.callkeep.services.core.CallkeepCore

interface CallConnectionController {
    fun answer(metadata: CallMetadata)

    fun decline(metadata: CallMetadata)

    fun hangUp(metadata: CallMetadata)

    fun tearDown()
}

class DefaultCallConnectionController : CallConnectionController {
    override fun answer(metadata: CallMetadata) {
        CallkeepCore.instance.startAnswerCall(metadata)
    }

    override fun decline(metadata: CallMetadata) {
        CallkeepCore.instance.startDeclineCall(metadata)
    }

    override fun hangUp(metadata: CallMetadata) {
        CallkeepCore.instance.startHungUpCall(metadata)
    }

    override fun tearDown() {
        CallkeepCore.instance.tearDownService()
    }
}
