package com.webtrit.callkeep.services.telecom.connection

enum class ProximityStateEnum {
    NEAR, DISTANCE
}

class PhoneConnectionConsts {
    private var proximity: ProximityStateEnum = ProximityStateEnum.DISTANCE
    private var shouldListenProximity: Boolean = false

    fun setNearestState(isNear: Boolean) {
        val nearState = if (isNear) ProximityStateEnum.NEAR else ProximityStateEnum.DISTANCE
        if (nearState != proximity) {
            proximity = nearState
        }
    }

    fun setShouldListenProximity(shouldListen: Boolean) {
        shouldListenProximity = shouldListen
    }

    fun isUserNear(): Boolean = proximity == ProximityStateEnum.NEAR
    fun shouldListenProximity(): Boolean = shouldListenProximity
}
