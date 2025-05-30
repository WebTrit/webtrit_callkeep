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
    this.ringbackSound,
    this.iconTemplateImageAssetName,
    this.supportsVideo = false,
    this.includesCallsInRecents = true,
    this.driveIdleTimerDisabled = true,
  });

  final String localizedName;
  final String? ringtoneSound;
  final String? ringbackSound;
  final String? iconTemplateImageAssetName;
  final int maximumCallGroups;
  final int maximumCallsPerCallGroup;
  final Set<CallkeepHandleType> supportedHandleTypes;
  final bool supportsVideo;
  final bool includesCallsInRecents;
  final bool driveIdleTimerDisabled;

  @override
  List<Object?> get props =>
      [
        localizedName,
        ringtoneSound,
        ringbackSound,
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
    this.ringbackSound,
  });

  final String? ringtoneSound;
  final String? ringbackSound;

  @override
  List<Object?> get props =>
      [
        ringtoneSound,
        ringbackSound,
      ];
}
