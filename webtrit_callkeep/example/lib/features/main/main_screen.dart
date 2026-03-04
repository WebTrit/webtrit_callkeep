import 'package:flutter/material.dart';

import 'package:go_router/go_router.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';
import 'package:webtrit_callkeep_example/app/routes.dart';

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
      appBar: AppBar(title: const Text('Webtrit Callkeep')),
      body: ListView(
        padding: const EdgeInsets.all(12),
        children: [
          // Navigation
          _SectionCard(
            title: 'Screens',
            children: [
              _NavTile(
                icon: Icons.phone,
                label: 'Callkeep API',
                subtitle: 'setUp · reportIncoming · in-call controls · connections',
                onTap: () => GoRouter.of(context).pushNamed(AppRoute.actions),
              ),
              _NavTile(
                icon: Icons.bug_report,
                label: 'Stress Tests',
                subtitle: 'Duplicate call, mixed push/direct scenarios',
                onTap: () => GoRouter.of(context).pushNamed(AppRoute.tests),
              ),
              _NavTile(
                icon: Icons.screen_lock_portrait,
                label: 'Activity Control',
                subtitle: 'Lock-screen, wake, background (Android)',
                onTap: () => GoRouter.of(context).pushNamed(AppRoute.activityControl),
              ),
            ],
          ),

          // OS permissions
          _SectionCard(
            title: 'App Permissions',
            children: [
              ListTile(
                dense: true,
                leading: const Icon(Icons.notifications),
                title: const Text('Notification'),
                trailing: _PermBtn(Permission.notification),
              ),
              ListTile(
                dense: true,
                leading: const Icon(Icons.battery_charging_full),
                title: const Text('Battery optimization'),
                trailing: _PermBtn(Permission.ignoreBatteryOptimizations),
              ),
              ListTile(
                dense: true,
                leading: const Icon(Icons.mic),
                title: const Text('Microphone'),
                trailing: _PermBtn(Permission.microphone),
              ),
            ],
          ),

          // Callkeep-specific permissions
          _SectionCard(
            title: 'Callkeep Permissions (Android)',
            children: [
              ListTile(
                dense: true,
                title: const Text('Full Screen Intent'),
                trailing: ElevatedButton(
                  onPressed: () async {
                    final status = await WebtritCallkeepPermissions().getFullScreenIntentPermissionStatus();
                    if (!mounted) return;
                    ScaffoldMessenger.of(context)
                        .showSnackBar(SnackBar(content: Text('Full-screen intent: $status')));
                  },
                  child: const Text('Check'),
                ),
              ),
              ListTile(
                dense: true,
                title: const Text('Open Full Screen Intent Settings'),
                trailing: ElevatedButton(
                  onPressed: () => WebtritCallkeepPermissions().openFullScreenIntentSettings(),
                  child: const Text('Open'),
                ),
              ),
              ListTile(
                dense: true,
                title: const Text('Battery Mode'),
                trailing: ElevatedButton(
                  onPressed: () async {
                    final mode = await WebtritCallkeepPermissions().getBatteryMode();
                    if (!mounted) return;
                    ScaffoldMessenger.of(context)
                        .showSnackBar(SnackBar(content: Text('Battery mode: $mode')));
                  },
                  child: const Text('Check'),
                ),
              ),
            ],
          ),

          // Android background services
          _SectionCard(
            title: 'Android Background Services',
            children: [
              ListTile(
                dense: true,
                title: const Text('Signaling Service'),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    OutlinedButton(
                      onPressed: () => AndroidCallkeepServices.backgroundSignalingBootstrapService.startService(),
                      child: const Text('Start'),
                    ),
                    const SizedBox(width: 6),
                    OutlinedButton(
                      onPressed: () => AndroidCallkeepServices.backgroundSignalingBootstrapService.stopService(),
                      child: const Text('Stop'),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleSmall),
            const Divider(height: 12),
            ...children,
          ],
        ),
      ),
    );
  }
}

class _NavTile extends StatelessWidget {
  const _NavTile({required this.icon, required this.label, required this.subtitle, required this.onTap});

  final IconData icon;
  final String label;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      leading: Icon(icon),
      title: Text(label),
      subtitle: Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}

class _PermBtn extends StatefulWidget {
  const _PermBtn(this.permission);

  final Permission permission;

  @override
  State<_PermBtn> createState() => _PermBtnState();
}

class _PermBtnState extends State<_PermBtn> {
  PermissionStatus? _status;

  @override
  void initState() {
    super.initState();
    widget.permission.status.then((s) {
      if (mounted) setState(() => _status = s);
    });
  }

  @override
  Widget build(BuildContext context) {
    final granted = _status?.isGranted ?? false;
    return ElevatedButton(
      onPressed: () async {
        final result = await widget.permission.request();
        if (mounted) setState(() => _status = result);
      },
      style: granted ? ElevatedButton.styleFrom(backgroundColor: Colors.green) : null,
      child: Text(granted ? 'Granted' : 'Request'),
    );
  }
}
