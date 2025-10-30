import 'dart:io' show Platform;

import 'package:flutter/material.dart';

import 'package:webtrit_callkeep/webtrit_callkeep.dart';

/// Demo screen for testing [AndroidCallkeepServices.activityControl].
class ActivityControlScreen extends StatefulWidget {
  const ActivityControlScreen({super.key});

  @override
  State<ActivityControlScreen> createState() => _ActivityControlScreenState();
}

class _ActivityControlScreenState extends State<ActivityControlScreen> {
  final _activityControl = AndroidCallkeepUtils.activityControl;

  bool _showOverLockscreen = false;
  bool _wakeScreenOnShow = false;
  bool? _isLockedStatus;

  /// Checks the device lock state and updates the UI.
  Future<void> _checkDeviceLockState() async {
    if (!Platform.isAndroid) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('This feature is only available on Android.')),
      );
      return;
    }
    final isLocked = await _activityControl.isDeviceLocked();

    if (!mounted) return;

    setState(() {
      _isLockedStatus = isLocked;
    });
  }

  /// Calls the method to send the app to the background.
  Future<void> _sendToBackground() async {
    if (!Platform.isAndroid) return;
    await _activityControl.sendToBackground();
  }

  @override
  Widget build(BuildContext context) {
    if (!Platform.isAndroid) {
      return const Card(
        child: ListTile(
          title: Text('Activity Control'),
          subtitle: Text('These features are only available on Android.'),
        ),
      );
    }

    return Scaffold(
      body: SafeArea(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Activity Control (Android Only)',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const Divider(),
                SwitchListTile(
                  title: const Text('Show Over Lockscreen'),
                  value: _showOverLockscreen,
                  onChanged: (newValue) async {
                    await _activityControl.showOverLockscreen(newValue);
                    setState(() {
                      _showOverLockscreen = newValue;
                    });
                  },
                ),
                SwitchListTile(
                  title: const Text('Wake Screen On Show'),
                  value: _wakeScreenOnShow,
                  onChanged: (newValue) async {
                    await _activityControl.wakeScreenOnShow(newValue);
                    setState(() {
                      _wakeScreenOnShow = newValue;
                    });
                  },
                ),
                const SizedBox(height: 12),
                Center(
                  child: ElevatedButton(
                    onPressed: _sendToBackground,
                    child: const Text('Send App to Background'),
                  ),
                ),
                const SizedBox(height: 12),
                const Divider(),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    ElevatedButton(
                      onPressed: _checkDeviceLockState,
                      child: const Text('Check Lock Status'),
                    ),
                    Text(
                      _isLockedStatus == null
                          ? 'Status: Unknown'
                          : _isLockedStatus!
                              ? 'Status: LOCKED'
                              : 'Status: UNLOCKED',
                      style: TextStyle(
                        color: _isLockedStatus == true ? Colors.red : Colors.green,
                        fontWeight: FontWeight.bold,
                      ),
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
