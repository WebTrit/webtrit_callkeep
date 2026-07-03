#import "CallWaitingTonePlayer.h"

// AVAudioPlayer-based call-waiting tone - see the header for the VP-timing mitigations.

static const double kToneFrequencyHz = 440.0;
static const double kToneOnSec = 0.20;
static const double kToneGapSec = 0.15;
static const double kToneTailSec = 2.0;
static const double kSampleRate = 16000.0;
static const float kAmplitude = 0.4f;

@implementation CallWaitingTonePlayer {
  AVAudioPlayer *_player;
  BOOL _active;
}

#pragma mark - Public

- (void)play {
  @synchronized(self) {
    _active = YES;
    [self ensurePlayer];
    [self startPlaybackLocked];
  }
}

- (void)stop {
  @synchronized(self) {
    _active = NO;
    if (_player != nil && _player.isPlaying) {
      [_player stop];
      _player.currentTime = 0;
    }
  }
}

- (void)onAudioSessionActivated {
  @synchronized(self) {
    // Pre-warm BEFORE the app starts WebRTC's voice-processing engine (this callback
    // is delivered natively first; the engine starts only after the Dart roundtrip).
    [self ensurePlayer];
    [_player prepareToPlay];
    NSLog(@"[CallWaitingTone] pre-warmed on session activation (active=%d)", _active);
    if (_active) {
      [self startPlaybackLocked];
    }
  }
}

- (void)onAudioSessionDeactivated {
  @synchronized(self) {
    if (_player != nil && _player.isPlaying) {
      [_player stop];
      _player.currentTime = 0;
    }
  }
}

#pragma mark - Internals

- (void)startPlaybackLocked {
  if (_player == nil) {
    NSLog(@"[CallWaitingTone] no player - tone skipped");
    return;
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
  if (!reasserted || error != nil) {
    NSLog(@"[CallWaitingTone] category re-assert failed: %@", error);
  }
  NSLog(@"[CallWaitingTone] play=%d reassert=%d category=%@ mode=%@",
        ok, reasserted, session.category, session.mode);
}

- (void)ensurePlayer {
  if (_player != nil) {
    return;
  }
  NSError *error = nil;
  // initWithData (not a temp file: contents-of-URL on a temp WAV returned nil on device).
  _player = [[AVAudioPlayer alloc] initWithData:[self toneWavData] error:&error];
  if (_player == nil || error != nil) {
    NSLog(@"[CallWaitingTone] player init failed: %@", error);
    _player = nil;
    return;
  }
  _player.numberOfLoops = -1;
  _player.volume = 1.0;
}

// 440 Hz "beep-beep" then ~2 s silence, looped: 16-bit PCM mono WAV built in memory.
- (NSData *)toneWavData {
  const uint32_t sr = (uint32_t)kSampleRate;
  const uint32_t toneN = (uint32_t)(kSampleRate * kToneOnSec);
  const uint32_t gapN = (uint32_t)(kSampleRate * kToneGapSec);
  const uint32_t tailN = (uint32_t)(kSampleRate * kToneTailSec);
  const uint32_t total = toneN + gapN + toneN + tailN;

  NSMutableData *pcm = [NSMutableData dataWithLength:total * sizeof(int16_t)];
  int16_t *samples = (int16_t *)pcm.mutableBytes;
  uint32_t idx = 0;
  for (uint32_t i = 0; i < toneN; i++, idx++) {
    samples[idx] = (int16_t)(kAmplitude * 32767.0f * sinf(2.0f * (float)M_PI * kToneFrequencyHz * i / sr));
  }
  idx += gapN;  // zero-initialized
  for (uint32_t i = 0; i < toneN; i++, idx++) {
    samples[(uint32_t)(toneN + gapN) + i] = (int16_t)(kAmplitude * 32767.0f * sinf(2.0f * (float)M_PI * kToneFrequencyHz * i / sr));
  }

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
