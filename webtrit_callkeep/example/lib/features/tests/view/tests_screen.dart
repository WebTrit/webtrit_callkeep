import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'package:webtrit_callkeep_example/core/event_log.dart';

import '../bloc/tests_cubit.dart';

class TestsScreen extends StatelessWidget {
  const TestsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<TestsCubit, TestsState>(
      builder: (context, state) {
        final cubit = context.read<TestsCubit>();
        return Scaffold(
          appBar: AppBar(title: const Text('Stress Tests')),
          body: ListView(
            padding: const EdgeInsets.all(12),
            children: [
              EventLogView(entries: state.entries, onClear: cubit.clearLog),
              const SizedBox(height: 12),
              if (state.isRunning)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: LinearProgressIndicator(),
                ),
              _TestCard(
                title: 'Same call ID — direct',
                description: 'Calls reportNewIncomingCall 4× with the same callId to verify deduplication.',
                onPressed: state.isRunning ? null : cubit.testSpamSameIncomingCalls,
              ),
              _TestCard(
                title: 'Same call ID — via push',
                description: 'Sends 4 push-notification incoming calls with the same callId.',
                onPressed: state.isRunning ? null : cubit.testSpamPushSameIncomingCalls,
              ),
              _TestCard(
                title: 'Mixed push + direct spam',
                description: 'Alternates push and direct incoming calls for the same callId 3×.',
                onPressed: state.isRunning ? null : cubit.testMixedSpam,
              ),
              _TestCard(
                title: 'Different call IDs',
                description: 'Reports incoming calls for call1 and call2 in rapid succession.',
                onPressed: state.isRunning ? null : cubit.testSpamDifferentIncomingCalls,
              ),
              const Divider(height: 24),
              OutlinedButton.icon(
                onPressed: state.isRunning ? null : cubit.tearDown,
                icon: const Icon(Icons.refresh),
                label: const Text('Reset (tearDown)'),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _TestCard extends StatelessWidget {
  const _TestCard({required this.title, required this.description, required this.onPressed});

  final String title;
  final String description;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: ListTile(
        title: Text(title, style: Theme.of(context).textTheme.titleSmall),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(description, style: Theme.of(context).textTheme.bodySmall),
        ),
        trailing: ElevatedButton(
          onPressed: onPressed,
          child: const Text('Run'),
        ),
        isThreeLine: true,
      ),
    );
  }
}
