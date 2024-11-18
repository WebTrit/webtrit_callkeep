import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_type.dart';
import 'callkeep_incoming_type.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({
    required this.type,
    required this.lifecycle,
    required this.activityReady,
    required this.lockScreen,
    required this.activeCalls,
    required this.data,
  });

  final CallkeepIncomingType type;
  final CallkeepLifecycleType lifecycle;
  final bool lockScreen;
  final bool activityReady;
  final bool activeCalls;
  final Map<String, dynamic> data;

  @override
  List<Object?> get props => [
        data,
        type,
        lifecycle,
        lockScreen,
        activityReady,
        activeCalls,
      ];
}
