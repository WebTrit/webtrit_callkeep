import '../models/models.dart';

abstract class CallkeepLogsDelegate {
  void onLog(CallkeepLogType type, String tag, String message);
}
