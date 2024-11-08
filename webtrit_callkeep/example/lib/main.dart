import 'package:logging/logging.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'app/view/app.dart';
import 'bootstrap.dart';

void main() {
  hierarchicalLoggingEnabled = true;
  bootstrap(() async {
    return App(
      callkeepBackgroundService: CallkeepBackgroundService(),
    );
  });
}
