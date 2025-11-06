part of 'actions_cubit.dart';

@freezed
class ActionsState with _$ActionsState {
  const ActionsState({
    required this.actions,
    this.speakerEnabled = false,
    this.isMuted = false,
    this.isHold = false,
  });

  @override
  final List<String> actions;

  @override
  final bool speakerEnabled;

  @override
  final bool isMuted;

  @override
  final bool isHold;

  /// Returns a copy with the new action appended.
  ActionsState addAction(String action) => copyWith(actions: [...actions, action]);

  /// Returns the last action or throws if empty.
  String get lastAction => actions.last;
}
