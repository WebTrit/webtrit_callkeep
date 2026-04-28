import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_event.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({required this.lifecycleEvent});

  final CallkeepLifecycleEvent lifecycleEvent;

  @override
  List<Object?> get props => [lifecycleEvent];
}
