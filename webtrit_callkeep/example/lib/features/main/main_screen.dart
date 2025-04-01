import 'package:flutter/material.dart';

import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';

import '../../app/constants.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({
    Key? key,
    required this.callkeepBackgroundService,
  }) : super(key: key);

  final BackgroundPushNotificationService callkeepBackgroundService;

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.max,
            children: [
              Text(
                "Webtrit Callkeep",
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center,
              ),
              DefaultGridView(
                children: [
                  ElevatedButton(
                    child: Text("Callkeep API", textAlign: TextAlign.center),
                    onPressed: () => GoRouter.of(context).pushNamed(AppRoute.actions),
                  ),
                  ElevatedButton(
                    child: Text("Tests API"),
                    onPressed: () => GoRouter.of(context).pushNamed(AppRoute.tests),
                  ),
                ],
              ),
              Text(
                "Permissions required for Android foreground services",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              DefaultGridView(
                children: [
                  ElevatedButton(
                    child: Text("Request all permissions", textAlign: TextAlign.center),
                    onPressed: () async {},
                  ),
                  ElevatedButton(
                    child: Text("Check all permissions", textAlign: TextAlign.center),
                    onPressed: () async {},
                  ),
                ],
              ),
              Text(
                "Callkeep Permissions",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              DefaultGridView(
                children: [
                  ElevatedButton(
                    child: Text("Full screen intent permission status", textAlign: TextAlign.center),
                    onPressed: () async {
                      var status = await WebtritCallkeepPermissions().getFullScreenIntentPermissionStatus();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Permission status: $status')),
                      );
                    },
                  ),
                  ElevatedButton(
                    child: Text("Open Full Screen Intent Settings", textAlign: TextAlign.center),
                    onPressed: () => WebtritCallkeepPermissions().openFullScreenIntentSettings(),
                  ),
                  ElevatedButton(
                    child: Text("Battery optimization status", textAlign: TextAlign.center),
                    onPressed: () async {
                      var status = await WebtritCallkeepPermissions().getBatteryMode();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(content: Text('Permission status: $status')),
                      );
                    },
                  ),
                ],
              ),
              Text(
                "Android signaling isolate  API ",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              DefaultGridView(children: [
                ElevatedButton(
                  child: Text("Start foreground signaling service", textAlign: TextAlign.center),
                  onPressed: () {
                    Permission.notification.request().then((value) {
                      if (value.isGranted) {
                        AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();
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
                ElevatedButton(
                  child: Text("Stop foreground signaling service", textAlign: TextAlign.center),
                  onPressed: () {
                    AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService();
                  },
                ),
              ]),
              Text(
                "Push notification isolate API ",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              DefaultGridView(children: [
                ElevatedButton(
                  child: Text("Trigger incoming call", textAlign: TextAlign.center),
                  onPressed: () {
                    CallkeepConnections().cleanConnections();
                    AndroidCallkeepServices.backgroundPushNotificationBootstrapService.reportNewIncomingCall(
                      call1Identifier,
                      call1Number,
                      displayName: call1Name,
                      hasVideo: false,
                    );
                  },
                ),
              ]),
              Text(
                "Base callkeep API (Main isolate API)",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              DefaultGridView(
                children: [
                  ElevatedButton(
                    child: Text("Report incoming call", textAlign: TextAlign.center),
                    onPressed: () => Callkeep()
                        .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Name, hasVideo: false),
                  ),
                  ElevatedButton(
                    child: Text("Hangup incoming call"),
                    onPressed: () => Callkeep().endCall(call1Identifier),
                  ),
                  ElevatedButton(
                    child: Text("Answer incoming call", textAlign: TextAlign.center),
                    onPressed: () => Callkeep().answerCall(call1Identifier),
                  ),
                ],
              ),
              SizedBox(height: 40),
            ],
          ),
        ),
      ),
    );
  }
}

class DefaultGridView extends StatelessWidget {
  const DefaultGridView({
    super.key,
    required this.children,
    this.crossAxisCount = 2,
  });

  final int crossAxisCount;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return GridView.count(
      padding: EdgeInsets.symmetric(horizontal: 8),
      crossAxisCount: 2,
      crossAxisSpacing: 8,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      childAspectRatio: 2.5,
      children: children.map((child) {
        return Center(child: child);
      }).toList(),
    );
  }
}
