import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

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
                    Card(
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          maxHeight: MediaQuery.of(context).size.height / 3,
                        ),
                        child: Padding(
                            padding: const EdgeInsets.all(8),
                            child: ListView(
                              children: state.actions
                                  .map((e) =>
                                      Text(e, style: Theme.of(context).textTheme.bodyMedium?.copyWith(height: 1.4)))
                                  .toList(),
                            )),
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
                        OutlinedButton(
                          child: Text("Is Setup"),
                          onPressed: () => context.read<ActionsCubit>().setup(),
                        ),
                        OutlinedButton(
                          child: Text("Is setup"),
                          onPressed: () => context.read<ActionsCubit>().isSetup(),
                        ),
                        OutlinedButton(
                          child: Text("Tear down"),
                          onPressed: () => context.read<ActionsCubit>().tearDown(),
                        ),
                        OutlinedButton(
                          child: Text("Report new incoming call"),
                          onPressed: () => context.read<ActionsCubit>().reportNewIncomingCall(),
                        ),
                        OutlinedButton(
                          child: Text("Report new incoming call another id"),
                          onPressed: () => context.read<ActionsCubit>().reportNewIncomingCallV2(),
                        ),
                        OutlinedButton(
                          child: Text("Report connecting outgoing call"),
                          onPressed: () => context.read<ActionsCubit>().reportConnectingOutgoingCall(),
                        ),
                        OutlinedButton(
                          child: Text("Report connected outgoing call"),
                          onPressed: () => context.read<ActionsCubit>().reportConnectedOutgoingCall(),
                        ),
                        OutlinedButton(
                          child: Text("Report update call"),
                          onPressed: () => context.read<ActionsCubit>().reportUpdateCall(),
                        ),
                        OutlinedButton(
                          child: Text("Report end call"),
                          onPressed: () => context.read<ActionsCubit>().reportEndCall(),
                        ),
                        OutlinedButton(
                          child: Text("Start call"),
                          onPressed: () => context.read<ActionsCubit>().startOutgoingCall(),
                        ),
                        OutlinedButton(
                          child: Text("Answer call"),
                          onPressed: () => context.read<ActionsCubit>().answerCall(),
                        ),
                        OutlinedButton(
                          child: Text("End call"),
                          onPressed: () => context.read<ActionsCubit>().endCall(),
                        ),
                        OutlinedButton(
                          child: Text("Set held"),
                          onPressed: () => context.read<ActionsCubit>().setHeld(),
                        ),
                        OutlinedButton(
                          child: Text("Set muted"),
                          onPressed: () => context.read<ActionsCubit>().setMuted(),
                        ),
                        OutlinedButton(
                          child: Text("Set speaker"),
                          onPressed: () => context.read<ActionsCubit>().setSpeaker(),
                        ),
                        OutlinedButton(
                          child: Text("Set DTMF"),
                          onPressed: () => context.read<ActionsCubit>().setDTMF(),
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
