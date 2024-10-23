import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:webtrit_callkeep_example/widgets/widgets.dart';

import '../bloc/actions_cubit.dart';

class ActionsScreen extends StatelessWidget {
  const ActionsScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return BlocConsumer<ActionsCubit, ActionsState>(
      listener: (context, state) {
        if (state is ActionsUpdate) {
          _showMessage(context, state.lastAction);
        }
      },
      builder: (context, state) => Scaffold(
        body: SafeArea(
          child: Container(
            padding: const EdgeInsets.only(top: 24, left: 16, right: 16),
            child: ListView(
              children: [
                Text(
                  "Callkeep features",
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(
                  height: 40,
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(4),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.grey.withOpacity(0.2),
                            spreadRadius: 1,
                            blurRadius: 4,
                            offset: const Offset(0, 3),
                          ),
                        ],
                      ),
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          maxHeight: MediaQuery.of(context).size.height / 3,
                        ),
                        child: ListView(
                          children: state.actions
                              .map(
                                (e) => Text(
                                  e,
                                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.4),
                                ),
                              )
                              .toList(),
                        ),
                      ),
                    ),
                    const SizedBox(
                      height: 24,
                    ),
                    Text(
                      "General",
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(
                      height: 16,
                    ),
                    Wrap(
                      children: [
                        Button(
                          title: "Setup",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().setup(),
                        ),
                        Button(
                          title: "Is setup",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().isSetup(),
                        ),
                        Button(
                          title: "Tear down",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().tearDown(),
                        ),
                        Button(
                          title: "Report new incoming call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().reportNewIncomingCall(),
                        ),
                        Button(
                          title: "Report new incoming call another id",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().reportNewIncomingCallV2(),
                        ),
                        Button(
                          title: "Report connecting outgoing call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().reportConnectingOutgoingCall(),
                        ),
                        Button(
                          title: "Report connected outgoing call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().reportConnectedOutgoingCall(),
                        ),
                        Button(
                          title: "Report update call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().reportUpdateCall(),
                        ),
                        Button(
                          title: "Report end call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().reportEndCall(),
                        ),
                        Button(
                          title: "Start call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().startOutgoingCall(),
                        ),
                        Button(
                          title: "Answer call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().answerCall(),
                        ),
                        Button(
                          title: "End call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().endCall(),
                        ),
                        Button(
                          title: "Set held",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().setHeld(),
                        ),
                        Button(
                          title: "Set muted",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().setMuted(),
                        ),
                        Button(
                          title: "Set speaker",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().setSpeaker(),
                        ),
                        Button(
                          title: "Set dtmf",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().setDTMF(),
                        ),
                      ],
                    ),
                    const SizedBox(
                      height: 24,
                    ),
                    Text(
                      "Exclusively for android services",
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(
                      height: 16,
                    ),
                    Wrap(
                      children: [
                        Button(
                          title: "Hung up",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().hungUpAndroid(),
                        ),
                        Button(
                          title: "Incoming call",
                          padding: const EdgeInsets.all(8),
                          textAlign: TextAlign.start,
                          onClick: () => context.read<ActionsCubit>().incomingCallAndroid(),
                        ),
                      ],
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showMessage(
    BuildContext context,
    String message,
  ) {
    final snackBar = SnackBar(
      content: Text(message),
      action: SnackBarAction(
        label: 'Dismiss',
        onPressed: () => ScaffoldMessenger.of(context).clearSnackBars(),
      ),
    );

    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }
}
