import 'package:equatable/equatable.dart';

import 'callkeep_handle.dart';

class CallkeepOptions extends Equatable {
  const CallkeepOptions({
    required this.ios,
    required this.android,
  });

  final CallkeepIOSOptions ios;
  final CallkeepAndroidOptions android;

  @override
  List<Object?> get props => [
        ios,
        android,
      ];
}

class CallkeepIOSOptions extends Equatable {
  const CallkeepIOSOptions({
    required this.localizedName,
    this.ringtoneSound,
    this.iconTemplateImageAssetName,
    required this.maximumCallGroups,
    required this.maximumCallsPerCallGroup,
    required this.supportedHandleTypes,
    this.supportsVideo = false,
    this.includesCallsInRecents = true,
    this.driveIdleTimerDisabled = true,
  });

  final String localizedName;
  final String? ringtoneSound;
  final String? iconTemplateImageAssetName;
  final int maximumCallGroups;
  final int maximumCallsPerCallGroup;
  final Set<CallkeepHandleType> supportedHandleTypes;
  final bool supportsVideo;
  final bool includesCallsInRecents;
  final bool driveIdleTimerDisabled;

  @override
  List<Object?> get props => [
        localizedName,
        ringtoneSound,
        iconTemplateImageAssetName,
        maximumCallGroups,
        maximumCallsPerCallGroup,
        supportedHandleTypes,
        supportsVideo,
        includesCallsInRecents,
        driveIdleTimerDisabled,
      ];
}

class CallkeepAndroidOptions extends Equatable {
  const CallkeepAndroidOptions({
    this.ringtoneSound,
    required this.incomingPath,
    required this.rootPath,
  });

  final String? ringtoneSound;
  final String incomingPath;
  final String rootPath;

  @override
  List<Object?> get props => [
        ringtoneSound,
        incomingPath,
        rootPath,
      ];
}
