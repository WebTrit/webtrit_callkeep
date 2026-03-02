import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'package:webtrit_callkeep_example/core/event_log.dart';

import '../bloc/actions_cubit.dart';

class ActionsScreen extends StatelessWidget {
  const ActionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<ActionsCubit, ActionsState>(
      builder: (context, state) {
        final cubit = context.read<ActionsCubit>();
        return Scaffold(
          appBar: AppBar(title: const Text('Callkeep API')),
          body: ListView(
            padding: const EdgeInsets.all(12),
            children: [
              EventLogView(entries: state.entries, onClear: cubit.clearLog),
              const SizedBox(height: 12),

              // --- Lifecycle ---
              _Section(
                title: 'Lifecycle',
                children: [
                  _Btn('Setup', cubit.setup),
                  _Btn('Is Setup', cubit.isSetup),
                  _Btn('Tear Down', cubit.tearDown, destructive: true),
                ],
              ),

              // --- Incoming ---
              _Section(
                title: 'Incoming Call',
                children: [
                  _Btn('Incoming Call 1', cubit.reportIncomingCall1),
                  _Btn('Incoming Call 2', cubit.reportIncomingCall2),
                  _Btn('Incoming via Push', cubit.incomingCallViaPush),
                  _Btn('Report End Call', cubit.reportEndCall),
                ],
              ),

              // --- Outgoing ---
              _Section(
                title: 'Outgoing Call',
                children: [
                  _Btn('Start Call', cubit.startOutgoingCall),
                  _Btn('Report Connecting', cubit.reportConnectingOutgoingCall),
                  _Btn('Report Connected', cubit.reportConnectedOutgoingCall),
                  _Btn('Report Update', cubit.reportUpdateCall),
                ],
              ),

              // --- In-call controls ---
              _Section(
                title: 'In-Call Controls  (call 1)',
                children: [
                  _Btn('Answer', cubit.answerCall),
                  _Btn('End', cubit.endCall, destructive: true),
                  _ToggleBtn(
                    label: state.isHold ? 'Unhold' : 'Hold',
                    active: state.isHold,
                    onPressed: cubit.setHeld,
                  ),
                  _ToggleBtn(
                    label: state.isMuted ? 'Unmute' : 'Mute',
                    active: state.isMuted,
                    onPressed: cubit.setMuted,
                  ),
                  _Btn('DTMF A', cubit.sendDTMF),
                ],
              ),

              // --- Connections ---
              _Section(
                title: 'Connections',
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton.icon(
                      onPressed: cubit.refreshConnections,
                      icon: const Icon(Icons.refresh, size: 16),
                      label: const Text('Refresh'),
                    ),
                    TextButton.icon(
                      onPressed: cubit.cleanConnections,
                      icon: const Icon(Icons.delete_sweep, size: 16),
                      label: const Text('Clean'),
                    ),
                  ],
                ),
                children: state.connections.isEmpty
                    ? [
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 8),
                          child: Text(
                            'No active connections',
                            style: TextStyle(color: Colors.grey, fontSize: 13),
                          ),
                        )
                      ]
                    : state.connections
                        .map(
                          (c) => Chip(
                            label: Text(
                              '${c.callId}  ${c.state.name}',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        )
                        .toList(),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.children, this.trailing});

  final String title;
  final List<Widget> children;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(title, style: Theme.of(context).textTheme.titleSmall),
                if (trailing != null) ...[const Spacer(), trailing!],
              ],
            ),
            const Divider(height: 12),
            Wrap(spacing: 6, runSpacing: 4, children: children),
          ],
        ),
      ),
    );
  }
}

class _Btn extends StatelessWidget {
  const _Btn(this.label, this.onPressed, {this.destructive = false});

  final String label;
  final VoidCallback onPressed;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(
      onPressed: onPressed,
      style: destructive
          ? OutlinedButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error)
          : null,
      child: Text(label),
    );
  }
}

class _ToggleBtn extends StatelessWidget {
  const _ToggleBtn({required this.label, required this.active, required this.onPressed});

  final String label;
  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return active
        ? FilledButton(onPressed: onPressed, child: Text(label))
        : OutlinedButton(onPressed: onPressed, child: Text(label));
  }
}
