#import "CallWaitingTonePlayer.h"

// Engine-lifecycle notifications posted by the flutter_webrtc plugin (its audio device
// module drives one AVAudioEngine per call and recreates it on route changes,
// voice-processing toggles and CallKit (de)activation). The notification object is the
// AVAudioEngine; DidConfigureOutput carries the wired mixer node and the output format in
// userInfo. Names are part of the flutter_webrtc plugin contract - keep them in sync.
static NSString *const AudioEngineDidCreateNotification = @"FlutterWebRTCAudioEngineDidCreate";
static NSString *const AudioEngineWillStartNotification = @"FlutterWebRTCAudioEngineWillStart";
static NSString *const AudioEngineDidStopNotification = @"FlutterWebRTCAudioEngineDidStop";
static NSString *const AudioEngineWillReleaseNotification = @"FlutterWebRTCAudioEngineWillRelease";
static NSString *const AudioEngineDidConfigureOutputNotification =
    @"FlutterWebRTCAudioEngineDidConfigureOutput";

static NSString *const AudioEngineSourceUserInfoKey = @"source";
static NSString *const AudioEngineFormatUserInfoKey = @"format";

@implementation CallWaitingTonePlayer {
  AVAudioPlayerNode *_player;
  AVAudioPCMBuffer *_buffer;
  __weak AVAudioEngine *_engine;
  BOOL _active;
}

- (instancetype)init {
  self = [super init];
  if (self) {
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center addObserver:self selector:@selector(onEngineDidCreate:) name:AudioEngineDidCreateNotification object:nil];
    [center addObserver:self selector:@selector(onEngineDidConfigureOutput:) name:AudioEngineDidConfigureOutputNotification object:nil];
    [center addObserver:self selector:@selector(onEngineWillStart:) name:AudioEngineWillStartNotification object:nil];
    [center addObserver:self selector:@selector(onEngineDidStop:) name:AudioEngineDidStopNotification object:nil];
    [center addObserver:self selector:@selector(onEngineWillRelease:) name:AudioEngineWillReleaseNotification object:nil];
  }
  return self;
}

- (void)dealloc {
  [[NSNotificationCenter defaultCenter] removeObserver:self];
}

#pragma mark - Public

- (void)play {
  @synchronized(self) {
    _active = YES;
    [self scheduleAndPlay];  // if the engine is not running yet, the will-start resume kicks in
  }
}

- (void)stop {
  @synchronized(self) {
    _active = NO;
    if (_player != nil && _player.isPlaying) {
      [_player stop];  // flushes the looped buffer
    }
  }
}

#pragma mark - Engine lifecycle (notifications arrive on the WebRTC worker thread)

- (void)onEngineDidCreate:(NSNotification *)notification {
  AVAudioEngine *engine = notification.object;
  if (engine == nil) {
    return;
  }
  @synchronized(self) {
    _engine = engine;
    if (_player == nil) {
      _player = [[AVAudioPlayerNode alloc] init];
    }
    @try {
      if (_player.engine != engine) {
        [engine attachNode:_player];
      }
    } @catch (NSException *e) {
      NSLog(@"[CallWaitingTonePlayer] attach failed: %@", e);
    }
  }
}

- (void)onEngineDidConfigureOutput:(NSNotification *)notification {
  // Fires after the engine's output graph is wired (source is the main mixer) with the
  // real output format - the right place and time to connect the player node.
  AVAudioEngine *engine = notification.object;
  AVAudioNode *source = notification.userInfo[AudioEngineSourceUserInfoKey];
  AVAudioFormat *format = notification.userInfo[AudioEngineFormatUserInfoKey];
  if (engine == nil || source == nil || format == nil) {
    return;
  }
  @synchronized(self) {
    if (_player == nil || _player.engine != engine) {
      return;  // attach on engine creation failed; nothing to connect
    }
    @try {
      // AVAudioPlayerNode cannot render Int16; connect with a float32 format at the
      // output sample rate and let the mixer convert.
      AVAudioFormat *playerFmt =
          [[AVAudioFormat alloc] initStandardFormatWithSampleRate:format.sampleRate channels:1];
      [engine connect:_player to:source format:playerFmt];
      _buffer = [self toneBufferForFormat:playerFmt];
    } @catch (NSException *e) {
      NSLog(@"[CallWaitingTonePlayer] connect failed: %@", e);
    }
  }
}

- (void)onEngineWillStart:(NSNotification *)notification {
  AVAudioEngine *engine = notification.object;
  @synchronized(self) {
    if (_player != nil && _player.engine == engine) {
      [_player stop];  // scheduled buffers do not survive an engine (re)start
    }
  }
  // No post-start notification exists; resume the active tone once the engine runs.
  dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.25 * NSEC_PER_SEC)),
                 dispatch_get_main_queue(), ^{
                   [self scheduleAndPlay];
                 });
}

- (void)onEngineDidStop:(NSNotification *)notification {
  AVAudioEngine *engine = notification.object;
  @synchronized(self) {
    if (_player != nil && _player.engine == engine && _player.isPlaying) {
      [_player stop];
    }
  }
}

- (void)onEngineWillRelease:(NSNotification *)notification {
  AVAudioEngine *engine = notification.object;
  @synchronized(self) {
    if (_player != nil && _player.engine == engine) {
      if (_player.isPlaying) {
        [_player stop];
      }
      [engine detachNode:_player];
    }
    _engine = nil;
    _buffer = nil;
  }
}

#pragma mark - Playback

- (void)scheduleAndPlay {
  @synchronized(self) {
    if (!_active) {
      return;  // only when explicitly requested; the will-start resume calls this blindly
    }
    if (_player == nil || _buffer == nil) {
      return;
    }
    if (_engine == nil || !_engine.isRunning) {
      return;  // resumes via the will-start hook once the engine is running
    }
    if (_player.isPlaying) {
      return;  // already looping; scheduling again would stack another buffer
    }
    [_player scheduleBuffer:_buffer
                     atTime:nil
                    options:AVAudioPlayerNodeBufferLoops
          completionHandler:nil];
    [_player play];
  }
}

// 440 Hz "beep-beep" then ~2 s silence, looped. Non-interleaved float32.
- (AVAudioPCMBuffer *)toneBufferForFormat:(AVAudioFormat *)format {
  double sr = format.sampleRate;
  double toneDur = 0.20, gapDur = 0.15, tailDur = 2.0;
  AVAudioFrameCount toneN = (AVAudioFrameCount)(sr * toneDur);
  AVAudioFrameCount gapN = (AVAudioFrameCount)(sr * gapDur);
  AVAudioFrameCount tailN = (AVAudioFrameCount)(sr * tailDur);
  AVAudioFrameCount total = toneN + gapN + toneN + tailN;
  AVAudioPCMBuffer *buf = [[AVAudioPCMBuffer alloc] initWithPCMFormat:format frameCapacity:total];
  if (buf == nil || buf.floatChannelData == NULL) {
    return nil;  // needs a non-interleaved float format
  }
  buf.frameLength = total;
  const float freq = 440.0f, amp = 0.18f;
  for (AVAudioChannelCount ch = 0; ch < format.channelCount; ch++) {
    float *p = buf.floatChannelData[ch];
    AVAudioFrameCount idx = 0;
    for (AVAudioFrameCount i = 0; i < toneN; i++, idx++) {
      p[idx] = amp * sinf(2.0f * (float)M_PI * freq * i / sr);
    }
    for (AVAudioFrameCount i = 0; i < gapN; i++, idx++) {
      p[idx] = 0.0f;
    }
    for (AVAudioFrameCount i = 0; i < toneN; i++, idx++) {
      p[idx] = amp * sinf(2.0f * (float)M_PI * freq * i / sr);
    }
    for (AVAudioFrameCount i = 0; i < tailN; i++, idx++) {
      p[idx] = 0.0f;
    }
  }
  return buf;
}

@end
