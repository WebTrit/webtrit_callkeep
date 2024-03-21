package com.webtrit.callkeep.webtrit_callkeep_android.connection

enum class VideoStateEnum {
    ACTIVE, UN_ACTIVE
}

enum class ProximityStateEnum {
    NEAR, DISTANCE
}

class PhoneConnectionConsts {
    private var video: VideoStateEnum = VideoStateEnum.UN_ACTIVE
    private var proximity: ProximityStateEnum = ProximityStateEnum.DISTANCE

    fun setVideoState(hasVideo: Boolean) {
        video = if (hasVideo) {
            VideoStateEnum.ACTIVE
        } else {
            VideoStateEnum.UN_ACTIVE
        }
    }

    fun setNearestState(isNear: Boolean) {
        val nearState = if (isNear) ProximityStateEnum.NEAR else ProximityStateEnum.DISTANCE
        if (nearState != proximity) {
            proximity = nearState
        }
    }

    fun isAudioState(): Boolean = video == VideoStateEnum.UN_ACTIVE
    fun isUserNear(): Boolean = proximity == ProximityStateEnum.NEAR
    fun isUserOnDistance(): Boolean = proximity == ProximityStateEnum.DISTANCE
}
