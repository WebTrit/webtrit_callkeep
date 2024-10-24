import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';
import 'package:webtrit_callkeep_example/isolates.dart';

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
  int _counter = 0;

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
            Divider(),
            Text("Foreground service API"),
            Divider(),
            SizedBox(height: 16),
            ElevatedButton(
              child: Text("Wake up background handler"),
              onPressed: () {
                Permission.notification.request().then((value) {
                  if (value.isGranted) {
                    widget.callkeepBackgroundService.setUp(autoRestartOnTerminate: true);
                    widget.callkeepBackgroundService.startService(data: {"foo": "bar"});
                  } else {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text("Notification permission is required"),
                      ),
                    );
                  }
                });
              },
            ),
            SizedBox(height: 8),
            ElevatedButton(
              child: Text("Stop call service"),
              onPressed: () {
                widget.callkeepBackgroundService.setUp(autoRestartOnTerminate: false);
                widget.callkeepBackgroundService.stopService();
              },
            ),
            ElevatedButton(
              child: Text("Update notification call service"),
              onPressed: () {
                _counter++;
                widget.callkeepBackgroundService.setUp(
                  androidNotificationName: "Title: Updated $_counter",
                  androidNotificationDescription: "Description:  Updated $_counter",
                );
                setState(() {});
              },
            ),
          ],
        ),
      ),
    );
  }
}
