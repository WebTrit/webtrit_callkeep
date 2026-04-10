part of 'tests_cubit.dart';

class TestsState {
  const TestsState({this.entries = const [], this.isRunning = false});

  final List<LogEntry> entries;
  final bool isRunning;

  TestsState copyWith({List<LogEntry>? entries, bool? isRunning}) {
    return TestsState(
      entries: entries ?? this.entries,
      isRunning: isRunning ?? this.isRunning,
    );
  }

  TestsState log(LogEntry entry) => copyWith(entries: [...entries, entry]);

  TestsState clearLog() => copyWith(entries: []);
}
