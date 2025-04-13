package com.webtrit.callkeep.models

class EmergencyNumberException(
    val metadata: FailureMetadata
) : Exception("Failed to establish outgoing connection: Emergency number")
