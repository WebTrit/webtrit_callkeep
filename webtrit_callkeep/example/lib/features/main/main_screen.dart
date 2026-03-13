import 'package:flutter/material.dart';

import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';

import '../../app/constants.dart';

class MainScreen extends StatefulWidget {
  const MainScreen({
    super.key,
    required this.callkeepBackgroundService,
  });

  final BackgroundPushNotificationService callkeepBackgroundService;

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Webtrit Callkeep Example'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(8.0),
        children: [
          // Section for API navigation
          _SectionCard(
            title: 'API Sections',
            children: [
              ElevatedButton(
                child: const Text('Callkeep API'),
                onPressed: () => GoRouter.of(context).pushNamed(AppRoute.actions),
              ),
              ElevatedButton(
                child: const Text('Tests API'),
                onPressed: () => GoRouter.of(context).pushNamed(AppRoute.tests),
              ),
              // New button to navigate to ActivityControlScreen
              ElevatedButton(
                child: const Text('Activity Control API'),
                onPressed: () => GoRouter.of(context).pushNamed(AppRoute.activityControl),
              ),
            ],
          ),

          // Section for basic app permissions
          _SectionCard(
            title: 'App Permissions',
            children: [
              ElevatedButton(
                child: const Text('Request All Permissions'),
                onPressed: () => _requestPermissions([
                  Permission.notification,
                  Permission.ignoreBatteryOptimizations,
                  Permission.microphone,
                  Permission.camera,
                ]),
              ),
              ElevatedButton(
                child: const Text('Check All Permissions'),
                onPressed: () {
                  // TODO: Implement permission check logic
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Check logic not implemented yet.')),
                  );
                },
              ),
            ],
          ),

          // Section for special Callkeep permissions
          _SectionCard(
            title: 'Callkeep Permissions (Android)',
            children: [
              ElevatedButton(
                child: const Text('Full Screen Intent Status'),
                onPressed: () async {
                  var status = await WebtritCallkeepPermissions().getFullScreenIntentPermissionStatus();
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Permission status: $status')),
                  );
                },
              ),
              ElevatedButton(
                child: const Text('Open Full Screen Settings'),
                onPressed: () => WebtritCallkeepPermissions().openFullScreenIntentSettings(),
              ),
              ElevatedButton(
                child: const Text('Battery Optimization Status'),
                onPressed: () async {
                  var status = await WebtritCallkeepPermissions().getBatteryMode();
                  if (!context.mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Permission status: $status')),
                  );
                },
              ),
            ],
          ),

          // Section for Signaling Isolate
          _SectionCard(
            title: 'Android Signaling Isolate API',
            children: [
              ElevatedButton(
                child: const Text('Start Foreground Service'),
                onPressed: () {
                  Permission.notification.request().then((value) {
                    if (value.isGranted) {
                      AndroidCallkeepServices.backgroundSignalingBootstrapService.startService();
                    } else {
                      if (!context.mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Notification permission is required'),
                        ),
                      );
                    }
                  });
                },
              ),
              ElevatedButton(
                child: const Text('Stop Foreground Service'),
                onPressed: () {
                  AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService();
                },
              ),
            ],
          ),

          // Section for Push Notification Isolate
          _SectionCard(
            title: 'Push Notification Isolate API',
            children: [
              ElevatedButton(
                child: const Text('Trigger Incoming Call (Push)'),
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
            ],
          ),

          // Section for Base Callkeep API
          _SectionCard(
            title: 'Base Callkeep API (Main Isolate)',
            children: [
              ElevatedButton(
                child: const Text('Report Incoming Call'),
                onPressed: () => Callkeep()
                    .reportNewIncomingCall(call1Identifier, call1Number, displayName: call1Name, hasVideo: false),
              ),
              ElevatedButton(
                child: const Text('Hangup Incoming Call'),
                onPressed: () => Callkeep().endCall(call1Identifier),
              ),
              ElevatedButton(
                child: const Text('Answer Incoming Call'),
                onPressed: () => Callkeep().answerCall(call1Identifier),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _requestPermissions(List<Permission> permissions) async {
    final statuses = await permissions.request();
    if (!mounted) return;

    statuses.forEach((permission, status) {
      debugPrint('$permission: $status');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$permission: $status')),
      );
    });
  }
}

/// A helper widget to create a consistent section card.
class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.children,
  });

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12.0),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const Divider(),
            Wrap(
              spacing: 8.0,
              runSpacing: 4.0,
              children: children,
            ),
          ],
        ),
      ),
    );
  }
}
