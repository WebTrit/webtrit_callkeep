import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';
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
            SizedBox(height: 16),
            Text(
              "Permissions ",
              style: Theme.of(context).textTheme.titleSmall,
              textAlign: TextAlign.center,
            ),
            SizedBox(height: 16),
            ElevatedButton(
              child: Text("Full screen intent permission status"),
              onPressed: () async {
                var status = await WebtritCallkeepPermissions().getFullScreenIntentPermissionStatus();
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Permission status: $status')),
                );
              },
            ),
            ElevatedButton(
              child: Text("Open Full Screen Intent Settings"),
              onPressed: () => WebtritCallkeepPermissions().openFullScreenIntentSettings(),
            ),
            ElevatedButton(
              child: Text("Battery optimization status"),
              onPressed: () async {
                var status = await WebtritCallkeepPermissions().getBatteryMode();
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text('Permission status: $status')),
                );
              },
            ),
            SizedBox(height: 16),
            Text(
              "Foreground service API ",
              style: Theme.of(context).textTheme.titleSmall,
              textAlign: TextAlign.center,
            ),
            SizedBox(height: 16),
            ElevatedButton(
              child: Text("Wake up background handler"),
              onPressed: () {
                Permission.notification.request().then((value) {
                  if (value.isGranted) {
                    widget.callkeepBackgroundService.setUp(autoRestartOnTerminate: true);
                    widget.callkeepBackgroundService.startService();
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
