import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_event.dart';
import 'callkeep_signaling_status.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({
    required this.lifecycleEvent,
    this.mainSignalingStatus,
  });

  final CallkeepLifecycleEvent lifecycleEvent;
  final CallkeepSignalingStatus? mainSignalingStatus;

  @override
  List<Object?> get props => [
        lifecycleEvent,
        mainSignalingStatus,
      ];
}
