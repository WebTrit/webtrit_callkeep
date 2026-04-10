part of 'actions_cubit.dart';

class CallLine {
  const CallLine({
    required this.id,
    required this.label,
    this.isAnswered = false,
    this.isHold = false,
    this.isMuted = false,
  });

  final String id;
  final String label;
  final bool isAnswered;
  final bool isHold;
  final bool isMuted;

  CallLine copyWith({String? id, String? label, bool? isAnswered, bool? isHold, bool? isMuted}) {
    return CallLine(
      id: id ?? this.id,
      label: label ?? this.label,
      isAnswered: isAnswered ?? this.isAnswered,
      isHold: isHold ?? this.isHold,
      isMuted: isMuted ?? this.isMuted,
    );
  }
}

class ActionsState {
  const ActionsState({
    this.entries = const [],
    this.isSetUp = false,
    this.lines = const [],
    this.activeLineId,
    this.connections = const [],
    this.isLogsDelegateActive = false,
    this.isRingbackPlaying = false,
  });

  final List<LogEntry> entries;
  final bool isSetUp;
  final List<CallLine> lines;
  final String? activeLineId;
  final List<CallkeepConnection> connections;
  final bool isLogsDelegateActive;
  final bool isRingbackPlaying;

  CallLine? get activeLine => lines.where((l) => l.id == activeLineId).firstOrNull;
  bool get isHold => activeLine?.isHold ?? false;
  bool get isMuted => activeLine?.isMuted ?? false;
  String get currentCallId => activeLineId ?? call1Identifier;

  ActionsState copyWith({
    List<LogEntry>? entries,
    bool? isSetUp,
    List<CallLine>? lines,
    String? activeLineId,
    List<CallkeepConnection>? connections,
    bool? isLogsDelegateActive,
    bool? isRingbackPlaying,
  }) {
    return ActionsState(
      entries: entries ?? this.entries,
      isSetUp: isSetUp ?? this.isSetUp,
      lines: lines ?? this.lines,
      activeLineId: activeLineId ?? this.activeLineId,
      connections: connections ?? this.connections,
      isLogsDelegateActive: isLogsDelegateActive ?? this.isLogsDelegateActive,
      isRingbackPlaying: isRingbackPlaying ?? this.isRingbackPlaying,
    );
  }

  ActionsState withNoActiveLine() {
    return ActionsState(
      entries: entries,
      isSetUp: isSetUp,
      lines: lines,
      activeLineId: null,
      connections: connections,
      isLogsDelegateActive: isLogsDelegateActive,
      isRingbackPlaying: isRingbackPlaying,
    );
  }

  ActionsState updateLine(String id, {bool? isAnswered, bool? isHold, bool? isMuted}) {
    final updatedLines = lines.map((l) {
      if (l.id == id) return l.copyWith(isAnswered: isAnswered, isHold: isHold, isMuted: isMuted);
      return l;
    }).toList();
    return copyWith(lines: updatedLines);
  }

  ActionsState log(LogEntry entry) => copyWith(entries: [...entries, entry]);
  ActionsState clearLog() => copyWith(entries: []);
}
