#import <AVFoundation/AVFoundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Plays a soft, looping call-waiting beep over the active call audio.
///
/// A plain AVAudioPlayer with two mitigations for the iOS quirk where playback sources
/// started after voice processing enables play near-silent (Apple forums thread 721535):
/// 1. The player is created and pre-warmed in the CallKit `didActivateAudioSession`
///    callback - callkeep receives it natively BEFORE the app starts WebRTC's
///    voice-processing engine, so the playback source exists before VP enables.
/// 2. After every `play`, the audio session category is re-asserted idempotently
///    (the documented workaround that restores full volume for late-started sources).
///
/// The tone stays local-only: everything the device plays is part of the
/// voice-processing echo-cancellation reference and is subtracted from the mic.
@interface CallWaitingTonePlayer : NSObject

/// Starts the looping beep. Safe to call repeatedly.
- (void)play;

/// Stops the beep. Safe to call repeatedly.
- (void)stop;

/// Must be called from `CXProviderDelegate provider:didActivateAudioSession:`.
/// Pre-warms the player before the call's voice-processing engine starts.
- (void)onAudioSessionActivated;

/// Must be called from `CXProviderDelegate provider:didDeactivateAudioSession:`.
- (void)onAudioSessionDeactivated;

@end

NS_ASSUME_NONNULL_END
