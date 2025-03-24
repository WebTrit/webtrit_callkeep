import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';

import '../../../app/constants.dart';

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
      body: Container(
        width: MediaQuery.of(context).size.width,
        padding: const EdgeInsets.only(top: 48),
        child: SingleChildScrollView(
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
              ElevatedButton(
                child: Text("Callkeep features"),
                onPressed: () => GoRouter.of(context).pushNamed(AppRoute.actions),
              ),
              ElevatedButton(
                child: Text("Simulated Call Tests"),
                onPressed: () => GoRouter.of(context).pushNamed(AppRoute.tests),
              ),
              SizedBox(height: 16),
              Text(
                "Permissions ",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              SizedBox(height: 16),
              ElevatedButton(
                  child: Text("Request microphone permission"),
                  onPressed: () async {
                    Permission.microphone.request();
                  }),
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
                "Signaling isolate  API ",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              SizedBox(height: 16),
              ElevatedButton(
                child: Text("Start foreground signaling service"),
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
              SizedBox(height: 8),
              ElevatedButton(
                child: Text("Stop foreground signaling service"),
                onPressed: () {
                  AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService();
                },
              ),
              SizedBox(height: 16),
              Text(
                "Push notification isolate API ",
                style: Theme.of(context).textTheme.titleSmall,
                textAlign: TextAlign.center,
              ),
              SizedBox(height: 16),
              ElevatedButton(
                child: Text("Trigger incoming call"),
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
              Divider(),
              ElevatedButton(
                child: Text("Hangup incoming call"),
                onPressed: () {
                  Callkeep().endCall(call1Identifier);
                },
              ),
              ElevatedButton(
                child: Text("Answer incoming call"),
                onPressed: () {
                  Callkeep().answerCall(call1Identifier);
                },
              ),
              SizedBox(height: 40),
            ],
          ),
        ),
      ),
    );
  }
}
