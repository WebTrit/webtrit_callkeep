import 'package:equatable/equatable.dart';
import 'package:webtrit_callkeep_platform_interface/src/models/callkeep_handle.dart';

class CallkeepOptions extends Equatable {
  const CallkeepOptions({required this.ios, required this.android});

  final CallkeepIOSOptions ios;
  final CallkeepAndroidOptions android;

  @override
  List<Object?> get props => [ios, android];
}

class CallkeepIOSOptions extends Equatable {
  const CallkeepIOSOptions({
    required this.localizedName,
    required this.maximumCallGroups,
    required this.maximumCallsPerCallGroup,
    required this.supportedHandleTypes,
    this.ringtoneSound,
    this.iconTemplateImageAssetName,
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
    required this.incomingPath,
    required this.rootPath,
    this.ringtoneSound,
    this.ringbackSound,
  });

  final String incomingPath;
  final String rootPath;
  final String? ringtoneSound;
  final String? ringbackSound;

  @override
  List<Object?> get props => [
        incomingPath,
        rootPath,
        ringtoneSound,
        ringbackSound,
      ];
}
