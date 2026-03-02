part of 'actions_cubit.dart';

class ActionsState {
  const ActionsState({
    this.entries = const [],
    this.isSetUp = false,
    this.isHold = false,
    this.isMuted = false,
    this.connections = const [],
  });

  final List<LogEntry> entries;
  final bool isSetUp;
  final bool isHold;
  final bool isMuted;
  final List<CallkeepConnection> connections;

  ActionsState copyWith({
    List<LogEntry>? entries,
    bool? isSetUp,
    bool? isHold,
    bool? isMuted,
    List<CallkeepConnection>? connections,
  }) {
    return ActionsState(
      entries: entries ?? this.entries,
      isSetUp: isSetUp ?? this.isSetUp,
      isHold: isHold ?? this.isHold,
      isMuted: isMuted ?? this.isMuted,
      connections: connections ?? this.connections,
    );
  }

  ActionsState log(LogEntry entry) => copyWith(entries: [...entries, entry]);

  ActionsState clearLog() => copyWith(entries: []);
}
