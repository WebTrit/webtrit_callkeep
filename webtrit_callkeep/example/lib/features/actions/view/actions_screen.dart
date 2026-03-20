import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';

import 'package:webtrit_callkeep_example/app/constants.dart';
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
          appBar: AppBar(
            title: const Text('Callkeep API'),
            actions: [
              Builder(
                builder: (ctx) => IconButton(
                  icon: const Icon(Icons.tune_rounded),
                  tooltip: 'Tools & Settings',
                  onPressed: () => Scaffold.of(ctx).openEndDrawer(),
                ),
              ),
              const SizedBox(width: 4),
            ],
          ),
          endDrawer: _HelperDrawer(state: state, cubit: cubit),
          body: Stack(
            children: [
              ListView(
                padding: const EdgeInsets.only(left: 12, right: 12, top: 12, bottom: 88),
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
                      _Btn('Push Token', cubit.getPushToken),
                    ],
                  ),

                  // --- Incoming ---
                  _Section(
                    title: 'Incoming Call',
                    children: [
                      _Btn('Incoming Call', cubit.reportIncomingCall),
                      _Btn('Incoming via Push', cubit.incomingCallViaPush),
                      _Btn('Report End Call', () => _showEndCallReasonDialog(context, cubit)),
                    ],
                  ),

                  // --- Outgoing ---
                  _Section(
                    title: 'Outgoing Call',
                    children: [
                      _Btn('Start Call', () => _showStartCallDialog(context, state, cubit)),
                      _Btn('Report Connecting', cubit.reportConnectingOutgoingCall),
                      _Btn('Report Connected', cubit.reportConnectedOutgoingCall),
                      _Btn('Report Update', cubit.reportUpdateCall),
                    ],
                  ),

                  // --- In-call controls ---
                  _Section(
                    title: 'In-Call Controls  (${state.activeLineId ?? "none"})',
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
                      _Btn('DTMF', () => _showDtmfDialog(context, cubit)),
                    ],
                  ),
                ],
              ),
              DraggableScrollableSheet(
                initialChildSize: 0.13,
                minChildSize: 0.13,
                maxChildSize: 0.45,
                snap: true,
                snapSizes: const [0.13, 0.45],
                builder: (ctx, sc) => _LinesPanel(scrollController: sc, state: state, cubit: cubit),
              ),
            ],
          ),
        );
      },
    );
  }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

void _showStartCallDialog(BuildContext context, ActionsState state, ActionsCubit cubit) {
  showDialog<void>(
    context: context,
    builder: (_) => _StartCallDialog(
      callerLineId: state.activeLineId,
      lines: state.lines,
      onCall: cubit.startOutgoingCall,
    ),
  );
}

void _showEndCallReasonDialog(BuildContext context, ActionsCubit cubit) {
  showDialog<void>(
    context: context,
    builder: (ctx) => SimpleDialog(
      title: const Text('End Call Reason'),
      children: CallkeepEndCallReason.values
          .map(
            (r) => SimpleDialogOption(
              onPressed: () {
                Navigator.pop(ctx);
                cubit.reportEndCall(r);
              },
              child: Text(r.name),
            ),
          )
          .toList(),
    ),
  );
}

void _showDtmfDialog(BuildContext context, ActionsCubit cubit) {
  showDialog<void>(
    context: context,
    builder: (_) => _DtmfDialog(onSend: cubit.sendDTMF),
  );
}

class _DtmfDialog extends StatefulWidget {
  const _DtmfDialog({required this.onSend});
  final void Function(String key) onSend;

  @override
  State<_DtmfDialog> createState() => _DtmfDialogState();
}

class _DtmfDialogState extends State<_DtmfDialog> {
  static const _keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#', 'A', 'B', 'C', 'D'];

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Send DTMF'),
      content: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: _keys
            .map(
              (k) => SizedBox(
                width: 52,
                height: 44,
                child: OutlinedButton(
                  onPressed: () {
                    Navigator.pop(context);
                    widget.onSend(k);
                  },
                  child: Text(k, style: const TextStyle(fontSize: 16)),
                ),
              ),
            )
            .toList(),
      ),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel'))],
    );
  }
}

class _StartCallDialog extends StatefulWidget {
  const _StartCallDialog({required this.callerLineId, required this.lines, required this.onCall});

  final String? callerLineId;
  final List<CallLine> lines;
  final void Function(String number) onCall;

  @override
  State<_StartCallDialog> createState() => _StartCallDialogState();
}

class _StartCallDialogState extends State<_StartCallDialog> {
  late final TextEditingController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = TextEditingController(text: call1Number.value);
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Start Outgoing Call'),
      content: SizedBox(
        width: 320,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (widget.callerLineId != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Row(
                    children: [
                      const Icon(Icons.phone_forwarded, size: 16, color: Colors.grey),
                      const SizedBox(width: 6),
                      Text(
                        'From: ${widget.callerLineId}',
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 12, color: Colors.grey),
                      ),
                    ],
                  ),
                ),
              TextField(
                controller: _ctrl,
                autofocus: true,
                decoration: const InputDecoration(
                  labelText: 'Number / handle',
                  border: OutlineInputBorder(),
                ),
                onSubmitted: (_) => _submit(),
              ),
              if (widget.lines.isNotEmpty) ...[
                const SizedBox(height: 14),
                const Text('Or dial a line:', style: TextStyle(fontSize: 12, color: Colors.grey)),
                const SizedBox(height: 4),
                ...widget.lines.map(
                  (l) => ListTile(
                    dense: true,
                    leading: const Icon(Icons.phone, size: 18),
                    title: Text(l.label, style: const TextStyle(fontSize: 13)),
                    subtitle: Text(
                      l.id,
                      style: const TextStyle(fontFamily: 'monospace', fontSize: 11),
                    ),
                    selected: _ctrl.text == l.id,
                    onTap: () => setState(() => _ctrl.text = l.id),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(onPressed: _submit, child: const Text('Call')),
      ],
    );
  }

  void _submit() {
    final number = _ctrl.text.trim();
    if (number.isEmpty) return;
    Navigator.pop(context);
    widget.onCall(number);
  }
}

// ---------------------------------------------------------------------------
// Lines panel (bottom draggable sheet)
// ---------------------------------------------------------------------------

class _LinesPanel extends StatelessWidget {
  const _LinesPanel({required this.scrollController, required this.state, required this.cubit});

  final ScrollController scrollController;
  final ActionsState state;
  final ActionsCubit cubit;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: ListView(
        controller: scrollController,
        padding: EdgeInsets.zero,
        children: [
          // Drag handle
          Center(
            child: Container(
              margin: const EdgeInsets.symmetric(vertical: 8),
              width: 32,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),

          // Chips row — always visible in collapsed state
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 4, 4),
            child: Row(
              children: [
                const Text('Lines:', style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13)),
                const SizedBox(width: 8),
                Expanded(
                  child: SizedBox(
                    height: 36,
                    child: state.lines.isEmpty
                        ? const Align(
                            alignment: Alignment.centerLeft,
                            child: Text('No lines yet', style: TextStyle(color: Colors.grey, fontSize: 12)),
                          )
                        : ListView.separated(
                            scrollDirection: Axis.horizontal,
                            itemCount: state.lines.length,
                            separatorBuilder: (_, __) => const SizedBox(width: 6),
                            itemBuilder: (_, i) {
                              final line = state.lines[i];
                              return ChoiceChip(
                                label: Text(line.label, style: const TextStyle(fontSize: 12)),
                                selected: line.id == state.activeLineId,
                                onSelected: (_) => cubit.selectLine(line.id),
                              );
                            },
                          ),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.add),
                  tooltip: 'New Line',
                  onPressed: cubit.addLine,
                ),
              ],
            ),
          ),

          // Expanded list — visible when panel is dragged up
          if (state.lines.isEmpty)
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 4, 16, 16),
              child: Text(
                'Tap + to create a line. Each line has a unique call ID for multi-line testing.',
                style: TextStyle(color: Colors.grey, fontSize: 12),
              ),
            )
          else
            ...state.lines.map((line) {
              final isActive = line.id == state.activeLineId;
              return ListTile(
                dense: true,
                leading: Icon(
                  isActive ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                  color: isActive ? colorScheme.primary : Colors.grey,
                  size: 20,
                ),
                title: Text(line.label, style: const TextStyle(fontSize: 13)),
                subtitle: Text(
                  line.id,
                  style: const TextStyle(fontFamily: 'monospace', fontSize: 11, color: Colors.grey),
                ),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (line.isAnswered) _Badge('ACTV', Colors.green),
                    if (line.isHold) _Badge('HOLD', Colors.orange),
                    if (line.isMuted) _Badge('MUTE', Colors.red),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, size: 18),
                      onPressed: () => cubit.removeLine(line.id),
                      color: Colors.grey,
                    ),
                  ],
                ),
                onTap: () => cubit.selectLine(line.id),
              );
            }),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Helper drawer (right-side end drawer)
// ---------------------------------------------------------------------------

class _HelperDrawer extends StatelessWidget {
  const _HelperDrawer({required this.state, required this.cubit});

  final ActionsState state;
  final ActionsCubit cubit;

  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
              child: Row(
                children: [
                  const Icon(Icons.tune_rounded, size: 20),
                  const SizedBox(width: 8),
                  Text('Tools & Settings', style: Theme.of(context).textTheme.titleMedium),
                ],
              ),
            ),
            const Divider(height: 1),

            // Scrollable content
            Expanded(
              child: ListView(
                padding: const EdgeInsets.all(12),
                children: [
                  // --- Audio Device ---
                  _Section(
                    title: 'Audio Device',
                    children: [
                      _Btn('Earpiece', () => cubit.setAudioDevice(CallkeepAudioDeviceType.earpiece)),
                      _Btn('Speaker', () => cubit.setAudioDevice(CallkeepAudioDeviceType.speaker)),
                      _Btn('Bluetooth', () => cubit.setAudioDevice(CallkeepAudioDeviceType.bluetooth)),
                      _Btn('Wired', () => cubit.setAudioDevice(CallkeepAudioDeviceType.wiredHeadset)),
                    ],
                  ),

                  // --- Sound ---
                  _Section(
                    title: 'Sound (Ringback)',
                    children: [
                      _ToggleBtn(
                        label: state.isRingbackPlaying ? 'Stop Ringback' : 'Play Ringback',
                        active: state.isRingbackPlaying,
                        onPressed: state.isRingbackPlaying ? cubit.stopRingback : cubit.playRingback,
                      ),
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
                    children: [
                      if (state.connections.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 4),
                          child: Text(
                            'No active connections',
                            style: TextStyle(color: Colors.grey, fontSize: 13),
                          ),
                        )
                      else
                        ...state.connections.map(
                          (c) => Chip(
                            label: Text(
                              '${c.callId}  ${c.state.name}',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        ),
                      const SizedBox(width: double.infinity),
                      _Btn('Get [active]', cubit.getConnectionByCallId),
                    ],
                  ),

                  // --- Signaling Status ---
                  _Section(
                    title: 'Signaling Status',
                    children: CallkeepSignalingStatus.values
                        .map((s) => _Btn('→ ${s.name}', () => cubit.updateSignalingStatus(s)))
                        .toList(),
                  ),

                  // --- Permissions ---
                  _Section(
                    title: 'Permissions',
                    children: [
                      _Btn('Check Status', cubit.checkPermissions),
                      _Btn('Request', cubit.requestPermissions),
                      _Btn('Battery Mode', cubit.getBatteryMode),
                      _Btn('FS Intent Status', cubit.getFullScreenIntentStatus),
                      _Btn('FS Intent Settings', cubit.openFullScreenIntentSettings),
                      _Btn('Open Settings', cubit.openSettings),
                    ],
                  ),

                  // --- Native Logs ---
                  _Section(
                    title: 'Native Logs',
                    children: [
                      _ToggleBtn(
                        label: state.isLogsDelegateActive ? 'Logs: ON' : 'Logs: OFF',
                        active: state.isLogsDelegateActive,
                        onPressed: cubit.toggleLogsDelegate,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Shared widgets
// ---------------------------------------------------------------------------

class _Badge extends StatelessWidget {
  const _Badge(this.label, this.color);

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(right: 4),
      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(4),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(label, style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.w600)),
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
      style: destructive ? OutlinedButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error) : null,
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
