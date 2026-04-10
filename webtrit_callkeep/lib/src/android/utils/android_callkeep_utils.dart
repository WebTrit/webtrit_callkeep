import 'activity_control.dart';
import 'sms_bootstrap_reception_config.dart';
import 'callkeep_diagnostics.dart';

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

  /// Provides access to system diagnostics and reporting tools.
  ///
  /// Use this to generate diagnostic reports containing device state,
  /// service status, and recent call failures.
  static final diagnostics = CallkeepDiagnostics();
}
