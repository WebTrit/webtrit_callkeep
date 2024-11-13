import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_type.dart';
import 'callkeep_incoming_type.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({
    required this.type,
    required this.lifecycle,
    required this.autoRestartOnTerminate,
    required this.autoStartOnBoot,
    required this.activityReady,
    required this.lockScreen,
    required this.activeCalls,
  });

  final CallkeepIncomingType type;
  final CallkeepLifecycleType lifecycle;
  final bool autoRestartOnTerminate;
  final bool autoStartOnBoot;
  final bool lockScreen;
  final bool activityReady;
  final bool activeCalls;

  @override
  List<Object?> get props => [
        type,
        lifecycle,
        autoRestartOnTerminate,
        autoStartOnBoot,
        lockScreen,
        activityReady,
        activeCalls,
      ];
}
