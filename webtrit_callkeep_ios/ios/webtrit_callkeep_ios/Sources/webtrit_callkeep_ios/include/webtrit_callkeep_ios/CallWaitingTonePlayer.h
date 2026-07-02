#import <AVFoundation/AVFoundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Plays a soft, looping call-waiting beep mixed into the active call's audio playout.
///
/// A separate player (AVAudioPlayer/AudioServices) is inaudible over a live WebRTC call:
/// the voice-processing session ducks any audio that is not part of the call's own render
/// graph. The only audible path is a node inside the call's AVAudioEngine, which is owned
/// by the WebRTC audio device module. The flutter_webrtc plugin re-broadcasts that
/// engine's lifecycle as NSNotifications (plain AVFoundation payloads, no WebRTC types),
/// and this class attaches an AVAudioPlayerNode onto the engine through them.
///
/// The instance must be created early (at plugin registration) so it observes the engine
/// notifications from the very first call. The tone is audible locally only - it enters
/// the playout graph downstream of the capture path, so the remote side does not hear it.
@interface CallWaitingTonePlayer : NSObject

/// Starts the looping beep. If the engine is not running yet, playback begins
/// automatically once it starts. Safe to call repeatedly.
- (void)play;

/// Stops the beep and keeps it stopped across engine restarts. Safe to call repeatedly.
- (void)stop;

@end

NS_ASSUME_NONNULL_END
