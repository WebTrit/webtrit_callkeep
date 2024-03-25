import 'package:webtrit_callkeep_platform_interface/src/models/models.dart';

abstract class CallkeepLogsDelegate {
  void onLog(CallkeepLogType type, String tag, String message);
}
