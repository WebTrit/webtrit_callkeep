#import "CallWaitingTonePlayer.h"

// AVAudioPlayer-based call-waiting tone - see the header for the VP-timing mitigations.

// Mirrors the Android call-waiting tone: ToneGenerator TONE_SUP_CALL_WAITING is a
// 440 Hz, 300 ms beep, and the connection service re-fires it every 3 seconds -
// so both platforms produce a single 440 Hz beep with a 3 s cadence.
static const double kToneFrequencyHz = 440.0;
static const double kToneOnSec = 0.30;
static const double kToneTailSec = 2.70;
static const double kSampleRate = 16000.0;
static const float kAmplitude = 0.4f;

@implementation CallWaitingTonePlayer {
  AVAudioPlayer *_player;
}

#pragma mark - Public

- (void)play {
  @synchronized(self) {
    [self ensurePlayer];
    [self startPlaybackLocked];
  }
}

- (void)stop {
  @synchronized(self) {
    [self haltPlaybackLocked];
  }
}

- (void)onAudioSessionActivated {
  @synchronized(self) {
    // Pre-warm BEFORE the app starts WebRTC's voice-processing engine (this callback
    // is delivered natively first; the engine starts only after the Dart roundtrip).
    // Playback itself is never resumed from here: whether the tone should play is
    // decided solely by the owner's call-state sync.
    [self ensurePlayer];
    [_player prepareToPlay];
#ifdef DEBUG
    NSLog(@"[CallWaitingTone] pre-warmed on session activation");
#endif
  }
}

- (void)onAudioSessionDeactivated {
  @synchronized(self) {
    [self haltPlaybackLocked];
  }
}

#pragma mark - Internals

- (void)startPlaybackLocked {
  if (_player == nil) {
#ifdef DEBUG
    NSLog(@"[CallWaitingTone] no player - tone skipped");
#endif
    return;
  }
  if (_player.isPlaying) {
    return;  // already looping; replaying would re-run the session re-assert for nothing
  }
  BOOL ok = [_player play];
  // Mitigation 2 (Apple forums 721535): sources started while voice processing runs play
  // near-silent; an idempotent category re-assert restores their volume. It does not
  // change the route or the session mode.
  AVAudioSession *session = [AVAudioSession sharedInstance];
  NSError *error = nil;
  BOOL reasserted = [session setCategory:session.category
                             withOptions:session.categoryOptions
                                   error:&error];
#ifdef DEBUG
  NSLog(@"[CallWaitingTone] play=%d reassert=%d category=%@ mode=%@",
        ok, reasserted, session.category, session.mode);
#else
  (void)ok;
  (void)reasserted;
#endif
}

- (void)haltPlaybackLocked {
  if (_player != nil && _player.isPlaying) {
    [_player stop];
    _player.currentTime = 0;
  }
}

- (void)ensurePlayer {
  if (_player != nil) {
    return;
  }
  NSError *error = nil;
  // initWithData (not a temp file: contents-of-URL on a temp WAV returned nil on device).
  _player = [[AVAudioPlayer alloc] initWithData:[self toneWavData] error:&error];
  if (_player == nil || error != nil) {
#ifdef DEBUG
    NSLog(@"[CallWaitingTone] player init failed: %@", error);
#endif
    _player = nil;
    return;
  }
  _player.numberOfLoops = -1;
  _player.volume = 1.0;
}

// A single 440 Hz beep then silence to a 3 s loop period: 16-bit PCM mono WAV in memory.
- (NSData *)toneWavData {
  const uint32_t sr = (uint32_t)kSampleRate;
  const uint32_t toneN = (uint32_t)(kSampleRate * kToneOnSec);
  const uint32_t tailN = (uint32_t)(kSampleRate * kToneTailSec);
  const uint32_t total = toneN + tailN;

  NSMutableData *pcm = [NSMutableData dataWithLength:total * sizeof(int16_t)];
  int16_t *samples = (int16_t *)pcm.mutableBytes;
  for (uint32_t i = 0; i < toneN; i++) {
    samples[i] = (int16_t)(kAmplitude * 32767.0f * sinf(2.0f * (float)M_PI * kToneFrequencyHz * i / sr));
  }
  // The tail is zero-initialized.

  const uint32_t dataLen = (uint32_t)pcm.length;
  const uint32_t byteRate = sr * 2;  // mono, 16-bit
  NSMutableData *wav = [NSMutableData dataWithCapacity:44 + dataLen];

  void (^appendU32)(uint32_t) = ^(uint32_t v) {
    uint32_t le = CFSwapInt32HostToLittle(v);
    [wav appendBytes:&le length:4];
  };
  void (^appendU16)(uint16_t) = ^(uint16_t v) {
    uint16_t le = CFSwapInt16HostToLittle(v);
    [wav appendBytes:&le length:2];
  };

  [wav appendBytes:"RIFF" length:4];
  appendU32(36 + dataLen);
  [wav appendBytes:"WAVE" length:4];
  [wav appendBytes:"fmt " length:4];
  appendU32(16);         // fmt chunk size
  appendU16(1);          // PCM
  appendU16(1);          // mono
  appendU32(sr);
  appendU32(byteRate);
  appendU16(2);          // block align
  appendU16(16);         // bits per sample
  [wav appendBytes:"data" length:4];
  appendU32(dataLen);
  [wav appendData:pcm];
  return wav;
}

@end
