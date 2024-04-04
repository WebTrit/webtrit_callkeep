part of 'actions_cubit.dart';

@immutable
class ActionsState {
  final List<String> actions;

  const ActionsState(this.actions);

  ActionsUpdate get update => ActionsUpdate(actions);
}

class ActionsUpdate extends ActionsState {
  const ActionsUpdate(super.actions);

  ActionsUpdate addAction({
    required String action,
  }) {
    return ActionsUpdate(
      [...actions, action],
    );
  }

  String get lastAction => actions.last;
}
