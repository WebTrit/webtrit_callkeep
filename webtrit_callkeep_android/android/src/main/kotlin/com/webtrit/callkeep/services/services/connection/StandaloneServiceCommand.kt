package com.webtrit.callkeep.services.services.connection

import android.content.Intent
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.models.CallMetadata

/**
 * Typed representation of a command delivered to [StandaloneCallService.onStartCommand].
 *
 * Mirrors [PhoneServiceCommand] for the non-Telecom path. Parsing is done once in [from] so the
 * [CallMetadata] extraction never runs for the no-extras lifecycle commands
 * ([StandaloneServiceAction.TearDownConnections], [StandaloneServiceAction.CleanConnections],
 * [StandaloneServiceAction.SyncAudioState], [StandaloneServiceAction.SyncConnectionState]). This
 * closes the same latent crash as on the Telecom path: a Binder-delivered empty
 * [android.os.Bundle] would otherwise reach `CallMetadata.fromBundle` and throw an uncaught
 * `IllegalArgumentException`.
 *
 * Every call action carries non-null [CallMetadata]; [Reserve] carries a non-null `callId`.
 */
sealed class StandaloneServiceCommand {
    data object TearDown : StandaloneServiceCommand()

    data object Clean : StandaloneServiceCommand()

    data object SyncAudio : StandaloneServiceCommand()

    data object SyncConnection : StandaloneServiceCommand()

    data class Reserve(
        val callId: String,
    ) : StandaloneServiceCommand()

    data class Call(
        val action: StandaloneServiceAction,
        val metadata: CallMetadata,
    ) : StandaloneServiceCommand()

    companion object {
        /**
         * Builds a [StandaloneServiceCommand] from [intent], or returns `null` when the action is
         * unknown/missing or a command that requires a `callId`/metadata is missing it. A `null`
         * result is non-fatal: the caller logs and ignores the intent.
         */
        fun from(intent: Intent): StandaloneServiceCommand? {
            val action = StandaloneServiceAction.from(intent.action) ?: return null
            return when (action) {
                StandaloneServiceAction.TearDownConnections -> {
                    TearDown
                }

                StandaloneServiceAction.CleanConnections -> {
                    Clean
                }

                StandaloneServiceAction.SyncAudioState -> {
                    SyncAudio
                }

                StandaloneServiceAction.SyncConnectionState -> {
                    SyncConnection
                }

                StandaloneServiceAction.ReserveAnswer -> {
                    intent.extras?.getString(CallDataConst.CALL_ID)?.let { Reserve(it) }
                }

                else -> {
                    intent.extras?.let { CallMetadata.fromBundleOrNull(it) }?.let { Call(action, it) }
                }
            }
        }
    }
}
