part of 'actions_cubit.dart';

@freezed
class ActionsState with _$ActionsState {
  const factory ActionsState({
    required List<String> actions,
    @Default(false) bool speakerEnabled,
    @Default(false) bool isMuted,
    @Default(false) bool isHold,
  }) = _ActionsState;

  const ActionsState._();

  /// Returns a copy with the new action appended
  ActionsState addAction(String action) => copyWith(actions: [...actions, action]);

  /// Returns the last action or throws if empty
  String get lastAction => actions.last;
}
