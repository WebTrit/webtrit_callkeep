import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_type.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({
    required this.lifecycle,
    required this.autoRestartOnTerminate,
    required this.autoStartOnBoot,
    required this.activityReady,
    required this.lockScreen,
  });

  final CallkeepLifecycleType lifecycle;
  final bool autoRestartOnTerminate;
  final bool autoStartOnBoot;
  final bool lockScreen;
  final bool activityReady;

  @override
  List<Object?> get props => [
        lifecycle,
        autoRestartOnTerminate,
        autoStartOnBoot,
        lockScreen,
        activityReady,
      ];
}
