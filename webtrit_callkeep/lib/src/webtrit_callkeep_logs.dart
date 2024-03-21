import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

class WebtritCallkeepLogs {
  static final _instance = WebtritCallkeepLogs._();

  factory WebtritCallkeepLogs() {
    return _instance;
  }

  WebtritCallkeepLogs._();

  void setLogsDelegate(CallkeepLogsDelegate? delegate) {
    WebtritCallkeepPlatform.instance.setLogsDelegate(
      delegate,
    );
  }
}
