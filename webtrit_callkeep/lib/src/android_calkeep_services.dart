import 'package:webtrit_callkeep/src/android/android.dart';

/// Provides access to various Android Callkeep-related services.
///
/// This abstract class exposes static instances for interacting with and
/// configuring background services used in call signaling and push notifications.
abstract class AndroidCallkeepServices {
  /// Provides configuration for the background signaling service.
  static final backgroundSignalingBootstrapService = BackgroundSignalingBootstrapService();

  /// Provides an interface for communication with the background signaling service.
  static final backgroundSignalingService = BackgroundSignalingService();

  /// Provides configuration for the background push notification service.
  static final backgroundPushNotificationBootstrapService = BackgroundPushNotificationBootstrapService();

  /// Provides an interface for communication with the background push notification service.
  static final backgroundPushNotificationService = BackgroundPushNotificationService();

  /// Provides configuration and initialization logic for handling incoming SMS messages.
  ///
  /// This is not a background service, but a bootstrap interface for the
  /// internal `BroadcastReceiver` used to receive specially formatted SMS messages
  /// that trigger incoming call flows (e.g. when push notifications are unavailable).
  static final smsReceptionConfig = SmsBootstrapReceptionConfig();
}
