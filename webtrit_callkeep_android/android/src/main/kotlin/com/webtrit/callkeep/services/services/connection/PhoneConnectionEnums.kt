package com.webtrit.callkeep.services.services.connection

enum class ServiceAction {
    HungUpCall, DeclineCall, AnswerCall, EstablishCall, Muting, Speaker, AudioDeviceSet, Holding, UpdateCall, SendDTMF, TearDown, TearDownConnections, ReserveAnswer, CleanConnections, SyncAudioState;

    companion object {
        fun from(action: String?): ServiceAction? =
            ServiceAction.entries.find { it.action == action }
    }

    // Explicit service intents target the component directly — the action string is only used
    // for routing inside onStartCommand, so a simple stable prefix is sufficient.
    // No runtime context needed, no global singleton dependency.
    val action: String
        get() = "callkeep_$name"
}
