import 'package:equatable/equatable.dart';

import 'callkeep_lifecycle_type.dart';

class CallkeepServiceStatus extends Equatable {
  const CallkeepServiceStatus({required this.lifecycle});

  final CallkeepLifecycleType lifecycle;

  @override
  List<Object?> get props => [
        lifecycle,
      ];
}
