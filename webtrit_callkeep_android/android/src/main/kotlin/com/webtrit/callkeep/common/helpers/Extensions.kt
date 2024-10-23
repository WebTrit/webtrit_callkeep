package com.webtrit.callkeep.common.helpers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepLifecycleType

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

inline fun <reified T : Parcelable> Bundle.serializable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerCustomReceiver(receiver: BroadcastReceiver, intentFilter: IntentFilter) {
    if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // TODO: Serdun - Address the Android 14 issue with receiving actions from the connection service with the flag RECEIVER_NOT_EXPORTED.
        // Investigate alternative methods for communication that do not require opening the API to another app.
        // As a temporary solution, use RECEIVER_EXPORTED.        registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
        registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
    } else {
        registerReceiver(receiver, intentFilter)
    }
}

fun Ringtone.setLoopingCompat(looping: Boolean) {
    if (SDK_INT >= Build.VERSION_CODES.P) {
        isLooping = looping
    }
}

fun Lifecycle.Event.toPCallkeepLifecycleType(): PCallkeepLifecycleType {
    return when (this) {
        Lifecycle.Event.ON_CREATE -> PCallkeepLifecycleType.ON_CREATE
        Lifecycle.Event.ON_START -> PCallkeepLifecycleType.ON_START
        Lifecycle.Event.ON_RESUME -> PCallkeepLifecycleType.ON_RESUME
        Lifecycle.Event.ON_PAUSE -> PCallkeepLifecycleType.ON_PAUSE
        Lifecycle.Event.ON_STOP -> PCallkeepLifecycleType.ON_STOP
        Lifecycle.Event.ON_DESTROY -> PCallkeepLifecycleType.ON_DESTROY
        Lifecycle.Event.ON_ANY -> PCallkeepLifecycleType.ON_ANY
    }
}
