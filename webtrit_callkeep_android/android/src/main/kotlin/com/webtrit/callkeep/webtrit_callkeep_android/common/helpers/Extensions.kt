package com.webtrit.callkeep.webtrit_callkeep_android.common.helpers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable

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
        registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, intentFilter)
    }
}