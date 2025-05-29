import 'package:flutter/material.dart';

import 'package:flutter_bloc/flutter_bloc.dart';

import '../bloc/tests_cubit.dart';

class TestsScreen extends StatelessWidget {
  const TestsScreen({
    Key? key,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<TestsCubit, TestsState>(
      builder: (context, state) => Scaffold(
        body: SafeArea(
          child: Container(
            padding: const EdgeInsets.only(top: 24, left: 16, right: 16),
            child: ListView(
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Card(
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
                      height: 16,
                    ),
                    Column(
                      children: [
                        ElevatedButton(
                          child: Text("Simulate repeated identical incoming calls", textAlign: TextAlign.center),
                          onPressed: () => context.read<TestsCubit>().spamSameIncomingCalls(),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton(
                          child: Text("Simulate repeated identical incoming calls (background)",
                              textAlign: TextAlign.center),
                          onPressed: () => context.read<TestsCubit>().spamSameIncomingCallsAndBackground(),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton(
                          child:
                              Text("Simulate background repeated identical incoming call", textAlign: TextAlign.center),
                          onPressed: () => context.read<TestsCubit>().spamBackgroundSameIncomingCalls(),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton(
                          child: Text("Simulate varied incoming calls", textAlign: TextAlign.center),
                          onPressed: () => context.read<TestsCubit>().spamDifferentIncomingCalls(),
                        ),
                        const SizedBox(height: 16),
                        ElevatedButton(
                          child: Text("Reset environment", textAlign: TextAlign.center),
                          onPressed: () => context.read<TestsCubit>().tearDown(),
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
}
