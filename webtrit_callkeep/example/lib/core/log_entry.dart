import 'package:flutter/material.dart';

enum LogType { info, success, error, event }

class LogEntry {
  const LogEntry({
    required this.message,
    required this.type,
    required this.timestamp,
  });

  final String message;
  final LogType type;
  final DateTime timestamp;

  factory LogEntry.info(String message) => LogEntry(message: message, type: LogType.info, timestamp: DateTime.now());

  factory LogEntry.success(String message) =>
      LogEntry(message: message, type: LogType.success, timestamp: DateTime.now());

  factory LogEntry.error(String message) => LogEntry(message: message, type: LogType.error, timestamp: DateTime.now());

  factory LogEntry.event(String message) => LogEntry(message: message, type: LogType.event, timestamp: DateTime.now());

  Color get color {
    switch (type) {
      case LogType.success:
        return Colors.green.shade700;
      case LogType.error:
        return Colors.red.shade700;
      case LogType.event:
        return Colors.blue.shade700;
      case LogType.info:
        return Colors.grey.shade800;
    }
  }

  String get timeLabel {
    final h = timestamp.hour.toString().padLeft(2, '0');
    final m = timestamp.minute.toString().padLeft(2, '0');
    final s = timestamp.second.toString().padLeft(2, '0');
    return '$h:$m:$s';
  }
}
