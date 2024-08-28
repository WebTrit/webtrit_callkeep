import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class PermissionsHelper(private val context: Context) {
    fun checkFullScreenIntentPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (Upside Down Cake)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val canUseFullScreenIntent = notificationManager.canUseFullScreenIntent()
            canUseFullScreenIntent
        } else {
            false
        }
    }

    fun launchFullScreenIntentSettings(): Boolean {
        return try {
            val intent = Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
