package com.webtrit.callkeep.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.Ringtone
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.webtrit.callkeep.PCallkeepIncomingCallData
import com.webtrit.callkeep.PCallkeepLifecycleEvent
import com.webtrit.callkeep.PCallkeepPermission
import com.webtrit.callkeep.PDelegateBackgroundRegisterFlutterApi
import com.webtrit.callkeep.PPermissionResult
import com.webtrit.callkeep.PSpecialPermissionStatusTypeEnum

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
    when {
        SDK_INT >= 33 -> {
            getParcelableExtra(key, T::class.java)
        }

        else -> {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
                as? T
        }
    }

inline fun <reified T : Parcelable> Bundle.serializable(key: String): T? =
    when {
        SDK_INT >= 33 -> {
            getParcelable(key, T::class.java)
        }

        else -> {
            @Suppress("DEPRECATION")
            getParcelable(key)
                as? T
        }
    }

inline fun <reified T : java.io.Serializable> Bundle.serializableCompat(key: String): T? =
    when {
        SDK_INT >= 33 -> {
            getSerializable(key, T::class.java)
        }

        else -> {
            @Suppress("DEPRECATION")
            getSerializable(key)
                as? T
        }
    }

inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? =
    when {
        SDK_INT >= 33 -> {
            getParcelableArrayListExtra(key, T::class.java)
        }

        else -> {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(key)
        }
    }

inline fun <reified T : Parcelable> Bundle.parcelableArrayList(key: String): ArrayList<T>? =
    when {
        SDK_INT >= 33 -> {
            getParcelableArrayList(key, T::class.java)
        }

        else -> {
            @Suppress("DEPRECATION")
            getParcelableArrayList(key)
        }
    }

fun Ringtone.setLoopingCompat(looping: Boolean) {
    if (SDK_INT >= Build.VERSION_CODES.P) {
        isLooping = looping
    }
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerReceiverCompat(
    receiver: BroadcastReceiver,
    intentFilter: IntentFilter,
    exported: Boolean = true,
    permission: String? = null,
) {
    if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val flags = if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
        registerReceiver(receiver, intentFilter, permission, null, flags)
    } else {
        registerReceiver(receiver, intentFilter, permission, null)
    }
}

/**
 * Sends an internal broadcast within the application.
 *
 * @param action The action string for the broadcast intent.
 * @param extras Optional extras to include in the broadcast intent.
 */
fun Context.sendInternalBroadcast(
    action: String,
    extras: Bundle? = null,
    permission: String? = null,
) {
    Intent(action)
        .apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            extras?.let { putExtras(it) }
        }.also { sendBroadcast(it, permission) }
}

fun Lifecycle.Event.toPCallkeepLifecycleType(): PCallkeepLifecycleEvent =
    when (this) {
        Lifecycle.Event.ON_CREATE -> PCallkeepLifecycleEvent.ON_CREATE
        Lifecycle.Event.ON_START -> PCallkeepLifecycleEvent.ON_START
        Lifecycle.Event.ON_RESUME -> PCallkeepLifecycleEvent.ON_RESUME
        Lifecycle.Event.ON_PAUSE -> PCallkeepLifecycleEvent.ON_PAUSE
        Lifecycle.Event.ON_STOP -> PCallkeepLifecycleEvent.ON_STOP
        Lifecycle.Event.ON_DESTROY -> PCallkeepLifecycleEvent.ON_DESTROY
        Lifecycle.Event.ON_ANY -> PCallkeepLifecycleEvent.ON_ANY
    }

fun Lifecycle.Event.toBundle(): Bundle =
    Bundle().apply {
        putString("LifecycleEvent", this@toBundle.name)
    }

fun Lifecycle.Event.Companion.fromBundle(bundle: Bundle?): Lifecycle.Event? {
    val name = bundle?.getString("LifecycleEvent") ?: return null
    return try {
        Lifecycle.Event.valueOf(name)
    } catch (_: IllegalArgumentException) {
        null
    }
}

fun Context.startForegroundServiceCompat(
    service: android.app.Service,
    notificationId: Int,
    notification: Notification,
    foregroundServiceType: Int? = null,
) {
    Log.d(
        "Extensions",
        "startForegroundServiceCompat: SDK_INT: $SDK_INT, foregroundServiceType: $foregroundServiceType",
    )
    if (SDK_INT >= Build.VERSION_CODES.Q && foregroundServiceType != null) {
        ServiceCompat.startForeground(service, notificationId, notification, foregroundServiceType)
    } else {
        service.startForeground(notificationId, notification)
    }
}

fun PDelegateBackgroundRegisterFlutterApi.syncPushIsolate(
    context: Context,
    callData: PCallkeepIncomingCallData?,
    callback: (Result<Unit>) -> Unit,
) {
    this.onNotificationSync(
        StorageDelegate.IncomingCallService.getOnNotificationSync(context),
        callData,
        callback = callback,
    )
}

inline fun Result<Unit>.handle(
    successAction: () -> Unit,
    failureAction: (Throwable) -> Unit,
) {
    onSuccess { successAction() }
    onFailure { failureAction(it) }
}

/**
 * Compatibility extension to show the Activity over the lock screen.
 */
fun Activity.setShowWhenLockedCompat(enable: Boolean) {
    if (SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(enable)
    } else {
        @Suppress("DEPRECATION")
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }
}

/**
 * Compatibility extension to turn the screen on when the Activity is shown.
 */
fun Activity.setTurnScreenOnCompat(enable: Boolean) {
    if (SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setTurnScreenOn(enable)
    } else {
        @Suppress("DEPRECATION")
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }
}

/**
 * Compatibility wrapper for moving the task to the back.
 *
 * Using `moveTaskToBack(true)` instead of `finish()`, because `finish()`
 * may cause the error "Error broadcast intent callback: result=CANCELLED".
 * This happens when the activity is finished while handling
 * a notification or a BroadcastReceiver, which cancels relevant operations.
 *
 * `moveTaskToBack(true)` simply moves the app to the background,
 * preserving all active processes.
 *
 * Reference: https://stackoverflow.com/questions/39480931/error-broadcast-intent-callback-result-cancelled-forintent-act-com-google-and
 */
fun Activity.moveTaskToBackCompat(): Boolean = moveTaskToBack(true)

/**
 * Checks if the device keyguard is currently locked.
 */
fun Context.isDeviceLockedCompat(): Boolean {
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return keyguardManager.isKeyguardLocked
}

/**
 * Converts a raw Android permission string to the Pigeon enum [PCallkeepPermission].
 */
fun String.toPCallkeepPermission(): PCallkeepPermission? =
    when (this) {
        Manifest.permission.READ_PHONE_STATE -> PCallkeepPermission.READ_PHONE_STATE
        Manifest.permission.READ_PHONE_NUMBERS -> PCallkeepPermission.READ_PHONE_NUMBERS
        else -> null
    }

/**
 * Converts a Pigeon enum [PCallkeepPermission] to the Android Manifest string.
 * Returns null if the permission is not relevant for the current SDK level (e.g. READ_PHONE_NUMBERS on old Android).
 */
fun PCallkeepPermission.toAndroidPermission(): String? =
    when (this) {
        PCallkeepPermission.READ_PHONE_STATE -> {
            Manifest.permission.READ_PHONE_STATE
        }

        PCallkeepPermission.READ_PHONE_NUMBERS -> {
            if (SDK_INT >= Build.VERSION_CODES.O) {
                Manifest.permission.READ_PHONE_NUMBERS
            } else {
                null
            }
        }
    }

/**
 * Converts a list of Pigeon permissions to a list of valid Android permission strings.
 */
fun List<PCallkeepPermission>.toAndroidPermissions(): List<String> = this.mapNotNull { it.toAndroidPermission() }

/**
 * Creates a list of [PPermissionResult] based on the current grant status of the provided permissions.
 */
fun List<String>.toPPermissionResults(context: Context): List<PPermissionResult> {
    return this.mapNotNull { permString ->
        val pType = permString.toPCallkeepPermission() ?: return@mapNotNull null
        val isGranted =
            ContextCompat.checkSelfPermission(
                context,
                permString,
            ) == PackageManager.PERMISSION_GRANTED

        PPermissionResult(
            permission = pType,
            status = if (isGranted) PSpecialPermissionStatusTypeEnum.GRANTED else PSpecialPermissionStatusTypeEnum.DENIED,
        )
    }
}
