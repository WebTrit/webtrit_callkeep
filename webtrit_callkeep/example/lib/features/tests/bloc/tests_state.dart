part of 'tests_cubit.dart';

@immutable
class TestsState {
  final List<String> actions;

  const TestsState(this.actions);

  TestsUpdate get update => TestsUpdate(actions);
}

class TestsUpdate extends TestsState {
  const TestsUpdate(super.actions);

  TestsUpdate addAction({
    required String action,
  }) {
    return TestsUpdate(
      [...actions, action],
    );
  }

  String get lastAction => actions.last;
}
