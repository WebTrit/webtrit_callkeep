package com.webtrit.callkeep.common

import android.annotation.SuppressLint
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepLifecycleEvent
import com.webtrit.callkeep.PCallkeepSignalingStatus
import com.webtrit.callkeep.models.SignalingStatus

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

inline fun <reified T : Parcelable> Bundle.serializable(key: String): T? = when {
    SDK_INT >= 33 -> getParcelable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

inline fun <reified T : java.io.Serializable> Bundle.serializableCompat(key: String): T? = when {
    SDK_INT >= 33 -> getSerializable(key, T::class.java)
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayListExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
}

inline fun <reified T : Parcelable> Bundle.parcelableArrayList(key: String): ArrayList<T>? = when {
    SDK_INT >= 33 -> getParcelableArrayList(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayList(key)
}

fun Ringtone.setLoopingCompat(looping: Boolean) {
    if (SDK_INT >= Build.VERSION_CODES.P) {
        isLooping = looping
    }
}

fun Lifecycle.Event.toPCallkeepLifecycleType(): PCallkeepLifecycleEvent {
    return when (this) {
        Lifecycle.Event.ON_CREATE -> PCallkeepLifecycleEvent.ON_CREATE
        Lifecycle.Event.ON_START -> PCallkeepLifecycleEvent.ON_START
        Lifecycle.Event.ON_RESUME -> PCallkeepLifecycleEvent.ON_RESUME
        Lifecycle.Event.ON_PAUSE -> PCallkeepLifecycleEvent.ON_PAUSE
        Lifecycle.Event.ON_STOP -> PCallkeepLifecycleEvent.ON_STOP
        Lifecycle.Event.ON_DESTROY -> PCallkeepLifecycleEvent.ON_DESTROY
        Lifecycle.Event.ON_ANY -> PCallkeepLifecycleEvent.ON_ANY
    }
}

fun Context.startForegroundServiceCompat(
    service: android.app.Service, notificationId: Int, notification: Notification, foregroundServiceType: Int? = null
) {
    Log.d(
        "Extensions",
        "startForegroundServiceCompat: SDK_INT: $SDK_INT, foregroundServiceType: $foregroundServiceType"
    )
    if (SDK_INT >= Build.VERSION_CODES.Q && foregroundServiceType != null) {
        ServiceCompat.startForeground(service, notificationId, notification, foregroundServiceType)
    } else {
        service.startForeground(notificationId, notification)
    }
}

fun SignalingStatus.toPCallkeepSignalingStatus(): PCallkeepSignalingStatus {
    return when (this) {
        SignalingStatus.DISCONNECTING -> PCallkeepSignalingStatus.DISCONNECTING
        SignalingStatus.DISCONNECT -> PCallkeepSignalingStatus.DISCONNECT
        SignalingStatus.CONNECTING -> PCallkeepSignalingStatus.CONNECTING
        SignalingStatus.CONNECT -> PCallkeepSignalingStatus.CONNECT
        SignalingStatus.FAILURE -> PCallkeepSignalingStatus.FAILURE
    }
}

fun PCallkeepSignalingStatus.toSignalingStatus(): SignalingStatus {
    return when (this) {
        PCallkeepSignalingStatus.DISCONNECTING -> SignalingStatus.DISCONNECTING
        PCallkeepSignalingStatus.DISCONNECT -> SignalingStatus.DISCONNECT
        PCallkeepSignalingStatus.CONNECTING -> SignalingStatus.CONNECTING
        PCallkeepSignalingStatus.CONNECT -> SignalingStatus.CONNECT
        PCallkeepSignalingStatus.FAILURE -> SignalingStatus.FAILURE
    }
}


