import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_type.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({
    required this.lifecycle,
    required this.activityReady,
    required this.lockScreen,
    required this.activeCalls,
    required this.data,
  });

  final CallkeepLifecycleType lifecycle;
  final bool lockScreen;
  final bool activityReady;
  final bool activeCalls;
  final Map<String, dynamic> data;

  @override
  List<Object?> get props => [
        data,
        lifecycle,
        lockScreen,
        activityReady,
        activeCalls,
      ];
}
