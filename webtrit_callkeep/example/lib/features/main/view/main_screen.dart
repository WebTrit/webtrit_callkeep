import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({
    Key? key,
    required this.callkeepBackgroundService,
  }) : super(key: key);

  final CallkeepBackgroundService callkeepBackgroundService;

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        width: MediaQuery.of(context).size.width,
        padding: const EdgeInsets.only(top: 48),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Text(
              "Webtrit Callkeep",
              style: Theme.of(context).textTheme.titleLarge,
              textAlign: TextAlign.center,
            ),
            const SizedBox(
              height: 32,
            ),
            Wrap(
              alignment: WrapAlignment.center,
              children: [
                ElevatedButton(
                  child: Text("Callkeep features"),
                  onPressed: () => GoRouter.of(context).pushNamed(AppRoute.actions),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
