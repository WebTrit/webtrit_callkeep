import 'package:flutter/material.dart';

import 'log_entry.dart';

class EventLogView extends StatefulWidget {
  const EventLogView({super.key, required this.entries, this.onClear});

  final List<LogEntry> entries;
  final VoidCallback? onClear;

  @override
  State<EventLogView> createState() => _EventLogViewState();
}

class _EventLogViewState extends State<EventLogView> {
  final _controller = ScrollController();

  @override
  void didUpdateWidget(EventLogView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.entries.length != oldWidget.entries.length) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_controller.hasClients) {
          _controller.animateTo(
            _controller.position.maxScrollExtent,
            duration: const Duration(milliseconds: 150),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            child: Row(
              children: [
                const Icon(Icons.terminal, size: 16, color: Colors.grey),
                const SizedBox(width: 6),
                Text('Event Log', style: Theme.of(context).textTheme.labelLarge),
                const Spacer(),
                if (widget.onClear != null)
                  TextButton.icon(
                    onPressed: widget.entries.isEmpty ? null : widget.onClear,
                    icon: const Icon(Icons.clear_all, size: 16),
                    label: const Text('Clear'),
                    style: TextButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                    ),
                  ),
              ],
            ),
          ),
          const Divider(height: 1),
          SizedBox(
            height: 200,
            child: widget.entries.isEmpty
                ? const Center(
                    child: Text(
                      'No events yet',
                      style: TextStyle(color: Colors.grey, fontSize: 13),
                    ),
                  )
                : ListView.builder(
                    controller: _controller,
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    itemCount: widget.entries.length,
                    itemBuilder: (_, i) {
                      final e = widget.entries[i];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 3),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              e.timeLabel,
                              style: const TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                                fontFamily: 'monospace',
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                e.message,
                                style: TextStyle(fontSize: 12, color: e.color),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
