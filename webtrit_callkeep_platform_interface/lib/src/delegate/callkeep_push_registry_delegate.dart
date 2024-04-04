/// Push registry delegate
abstract class PushRegistryDelegate {
  /// Push token update callback for push type VoIP
  void didUpdatePushTokenForPushTypeVoIP(String? token);
}
