import 'package:webtrit_callkeep/src/android/utils/activity_control.dart';
import 'package:webtrit_callkeep/src/android/utils/sms_bootstrap_reception_config.dart';

/// Provides access to various Android Callkeep **utilities and helpers**.
///
/// This abstract class exposes static instances for one-off configurations
/// and helper methods that interact with the Android system.
abstract class AndroidCallkeepUtils {
  /// Provides configuration and initialization logic for handling incoming SMS messages.
  ///
  /// This is not a background service, but a bootstrap interface for the
  /// internal `BroadcastReceiver` used to receive specially formatted SMS messages
  /// that trigger incoming call flows (e.g. when push notifications are unavailable).
  static final smsReceptionConfig = SmsBootstrapReceptionConfig();

  /// Provides access to Android-specific Activity controls.
  ///
  /// This includes methods for managing behavior over the lock screen,
  /// waking the screen, moving the task to the back, and checking the device lock state.
  static final activityControl = ActivityControl();
}
