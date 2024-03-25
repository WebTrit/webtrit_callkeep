import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

/// Logger delegate
abstract class CallkeepLogsDelegate {
  /// Log callback
  void onLog(CallkeepLogType type, String tag, String message);
}
