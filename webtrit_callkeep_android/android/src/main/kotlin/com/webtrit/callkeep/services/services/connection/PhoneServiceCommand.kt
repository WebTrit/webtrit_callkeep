package com.webtrit.callkeep.services.services.connection

import android.content.Intent
import com.webtrit.callkeep.common.CallDataConst
import com.webtrit.callkeep.models.CallMetadata

/**
 * Typed representation of a command delivered to [PhoneConnectionService.onStartCommand].
 *
 * Parsing the [Intent] (action lookup + [CallMetadata] extraction) is performed once in [from]
 * instead of eagerly at the top of `onStartCommand`. This removes the crash where
 * `CallMetadata.fromBundle` was invoked on the bare intent extras BEFORE the surrounding
 * try/catch: Binder IPC can deliver a non-null but empty [android.os.Bundle] for the no-extras
 * lifecycle commands ([ServiceAction.TearDownConnections], [ServiceAction.CleanConnections],
 * [ServiceAction.SyncAudioState], [ServiceAction.SyncConnectionState]), and the missing-`callId`
 * `IllegalArgumentException` then propagated uncaught out of `onStartCommand`.
 *
 * With this factory each command type owns exactly the data it needs:
 *  - lifecycle commands carry nothing and never touch the extras;
 *  - [Reserve] / [Pending] carry a non-null `callId`;
 *  - [AddIncoming] carries non-null [CallMetadata];
 *  - [CallOp] carries the raw [ServiceAction] plus nullable metadata for the dispatcher path.
 */
sealed class PhoneServiceCommand {
    data object TearDown : PhoneServiceCommand()

    data object Clean : PhoneServiceCommand()

    data object SyncAudio : PhoneServiceCommand()

    data object SyncConnection : PhoneServiceCommand()

    data class Reserve(
        val callId: String,
    ) : PhoneServiceCommand()

    data class Pending(
        val callId: String,
    ) : PhoneServiceCommand()

    data class AddIncoming(
        val metadata: CallMetadata,
    ) : PhoneServiceCommand()

    data class CallOp(
        val action: ServiceAction,
        val metadata: CallMetadata?,
    ) : PhoneServiceCommand()

    companion object {
        /**
         * Builds a [PhoneServiceCommand] from [intent], or returns `null` when the action is
         * unknown/missing or a command that requires a `callId`/metadata is missing it. A `null`
         * result is non-fatal: the caller logs and ignores the intent.
         */
        fun from(intent: Intent): PhoneServiceCommand? {
            val action = ServiceAction.from(intent.action) ?: return null
            return when (action) {
                ServiceAction.TearDownConnections -> {
                    TearDown
                }

                ServiceAction.CleanConnections -> {
                    Clean
                }

                ServiceAction.SyncAudioState -> {
                    SyncAudio
                }

                ServiceAction.SyncConnectionState -> {
                    SyncConnection
                }

                ServiceAction.ReserveAnswer -> {
                    intent.extras?.getString(CallDataConst.CALL_ID)?.let { Reserve(it) }
                }

                ServiceAction.NotifyPending -> {
                    intent.extras?.getString(CallDataConst.CALL_ID)?.let { Pending(it) }
                }

                ServiceAction.AddNewIncomingCall -> {
                    intent.extras?.let { CallMetadata.fromBundleOrNull(it) }?.let { AddIncoming(it) }
                }

                else -> {
                    CallOp(action, intent.extras?.let { CallMetadata.fromBundleOrNull(it) })
                }
            }
        }
    }
}
