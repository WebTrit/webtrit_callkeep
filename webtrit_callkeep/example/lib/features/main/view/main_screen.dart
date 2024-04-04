import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';
import 'package:webtrit_callkeep_example/widgets/widgets.dart';

class MainScreen extends StatelessWidget {
  const MainScreen({Key? key}) : super(key: key);

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
                Button(
                  title: 'Callkeep features',
                  onClick: () {
                    GoRouter.of(context).pushNamed(AppRoute.actions);
                  },
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
