import 'package:flutter/material.dart';

import 'package:go_router/go_router.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/features/features.dart';

import '../routes.dart';

class App extends StatefulWidget {
  const App({
    Key? key,
    required this.callkeepBackgroundService,
  }) : super(key: key);

  final CallkeepBackgroundService callkeepBackgroundService;

  @override
  State<App> createState() => _AppState();
}

class _AppState extends State<App> {
  @override
  Widget build(BuildContext context) {
    final materialApp = MaterialApp.router(
      restorationScopeId: 'App',
      title: "Sample",
      routerConfig: _router,
    );

    return materialApp;
  }

  late final GoRouter _router = GoRouter(
    routes: [
      ShellRoute(
        builder: (context, state, child) => child,
        routes: [
          GoRoute(
            name: AppRoute.main,
            path: '/main',
            builder: (context, state) => MainScreen(
              callkeepBackgroundService: widget.callkeepBackgroundService,
            ),
          ),
          GoRoute(
            name: AppRoute.actions,
            path: '/actions',
            builder: (context, state) => BlocProvider(
              create: (context) {
                return ActionsCubit(
                  Callkeep(),
                  widget.callkeepBackgroundService,
                );
              },
              child: const ActionsScreen(),
            ),
          ),
        ],
      ),
    ],
    initialLocation: '/main',
  );
}
