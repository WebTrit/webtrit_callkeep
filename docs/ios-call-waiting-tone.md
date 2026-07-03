# iOS call-waiting tone: design notes

## Purpose

When a second incoming call arrives while another call is already active, iOS gives the user no audible
indication: CallKit does not auto-play a call-waiting tone for VoIP calls (that beep on cellular calls is
a carrier feature), and it also suppresses the regular ringtone for the second call. Android handles the
same scenario natively in the connection service - a soft tone instead of the full ringtone. This document
explains how the iOS side works and, more importantly, why it is built this way, because most of the
"obvious" alternatives fail in non-obvious ways.

Implementation: `CallWaitingTonePlayer` (playback) plus the `CXCallObserverDelegate` sync in
`WebtritCallkeepPlugin` (detection). Everything is native to this plugin: no Dart API, no app involvement,
no dependency on any other plugin.

## The core problem: audio playback during a live call

During a VoIP call the audio session runs `playAndRecord` with mode `voiceChat`, and the call audio flows
through the voice-processing I/O unit (VPIO - the one doing echo cancellation and noise suppression).
Apple treats every audio stream that is not rendered through the voice-processing unit - including streams
from the SAME app - as "other audio" (WWDC23 session 10235, "What's new in voice processing").

Two distinct mechanisms then affect that "other audio":

1. **Documented ducking** (`AVAudioVoiceProcessingOtherAudioDuckingConfiguration`, iOS 17+). Mild by
   itself; the WebRTC stack used with this plugin already configures `duckingLevel = .min`. This is NOT
   what makes other audio inaudible.
2. **An undocumented, long-standing behavior** (Apple developer forums thread 721535, reproduced across
   iOS 13-26): audio sources STARTED AFTER the voice-processing unit is running play near-silent, as if
   not routed to the output. This affects `AVAudioPlayer`, `AVPlayer` and additional `AVAudioEngine`
   instances alike. Two things counter it:
   - sources set up BEFORE voice processing starts keep full volume;
   - re-issuing `setCategory` with the current values (an idempotent no-op configuration-wise) restores
     full volume for late-started sources without changing the route or the session mode.

`CallWaitingTonePlayer` is therefore a plain `AVAudioPlayer` (an in-memory synthesized WAV, looped) with
both mitigations applied:

- **Pre-warm ordering.** The player is created and `prepareToPlay`-ed inside the native
  `CXProviderDelegate provider:didActivateAudioSession:` callback. This callback is the earliest audio
  moment of a call and it reaches the plugin natively BEFORE the app-side (Dart) roundtrip that starts the
  WebRTC voice-processing engine - so the playback source predates VP by construction. Keep it that way:
  moving player creation to first-play would land in the near-silent case above.
- **Category re-assert.** After each actual playback start, the current session category and options are
  re-asserted. It is guarded to run only on a real stop-to-play transition (not on every call-state
  event), because each `setCategory` is a synchronous audio-server call.

The tone is heard locally only: everything the device plays is part of the voice-processing
echo-cancellation reference and is subtracted from the microphone signal, so the remote side does not
hear the beep.

## Rejected alternatives (and why)

| Approach | Why not |
|---|---|
| `AVAudioPlayer` without the two mitigations | Near-silent during a live call (behavior 2 above). |
| `AudioServicesPlaySystemSound` | System (UI) sounds are hard-disabled during calls by a separate mechanism; no workaround. |
| Custom `CXProviderConfiguration.ringtoneSound` | The second call's ringtone is suppressed during an active call; and if it did play, it would be ringer-volume - the exact problem the soft tone solves. |
| A player node inside WebRTC's own `AVAudioEngine` | Works and is fully duck-proof (it IS the call audio path), but the engine is owned by the WebRTC plugin and is recreated per call and on every route change - hosting the tone there either puts telephony logic into a transport plugin or requires fragile cross-plugin lifecycle contracts. Only worth revisiting if the mitigations above ever stop working. |
| A separate app-owned `AVAudioEngine` | Same "other audio" class as `AVAudioPlayer` (no ducking advantage), plus engine lifecycle/config-change handling for nothing. |
| Ducking configuration tweaks | `duckingLevel` is already `.min` in this stack; `enableAdvancedDucking` ducks MORE while speech is present. |
| `overrideOutputAudioPort(.speaker)` (also restores volume) | Moves the whole call from the earpiece to the speaker - unacceptable. |

## Detection

The plugin observes `CXCallObserver` and plays the tone while at least one call is connected (or held)
and at least one incoming call is ringing, stopping as soon as that state ends. Design points:

- **Native, not app-driven.** Mirrors the Android connection-service logic, works even when the Dart side
  is busy or not running, and requires no API surface.
- **Own calls only (default).** `CXCallObserver` reports every CallKit call on the device - cellular,
  other VoIP apps - so unfiltered detection would beep on top of foreign calls (or double up with the
  carrier's own call-waiting tone). The plugin tracks the UUIDs of calls it reported/started and counts
  only those. `CallkeepIOSOptions.callWaitingToneOwnCallsOnly = false` widens detection to all calls if a
  product ever wants the beep while the user is on a cellular call.
- **Answer suppression.** A `CXCall` keeps looking "ringing" until the answer action is fulfilled, which
  includes an app roundtrip and SIP signaling (seconds on a slow network). The UUID is excluded from the
  ringing set the moment the user accepts, so the beep does not bleed into the answered conversation.
- **No playback decisions from the provider queue.** `didActivateAudioSession` arrives on the provider's
  private queue; acting on cached state there can resume a tone that the (main-queue) call-state sync has
  already ended. The callback only pre-warms; play/stop decisions are made exclusively by the sync running
  on the observer's queue.
- **Scope boundary.** An outgoing call that is still dialing has `hasConnected == NO`, so a second
  incoming call during it produces no tone - intentionally the same as Android, where the connection
  service plays the full ringtone in that case.

## Tone pattern

A single 440 Hz, 300 ms beep on a 3-second cadence - matching what Android produces
(`ToneGenerator.TONE_SUP_CALL_WAITING` is a 440 Hz / 300 ms beep, re-fired by the connection service
every 3 s), so both platforms sound identical. The WAV is synthesized in memory (`initWithData:`); do not
switch to a temp-file URL - `AVAudioPlayer initWithContentsOfURL:` has been observed returning nil for
freshly written temp WAVs on device.

## Maintenance gotchas

- Native ObjC changes require a full rebuild; Flutter hot reload/restart swaps only Dart. When a change
  "has no effect", check the built product first (e.g. `strings <app>/Runner.debug.dylib | grep <marker>`).
- `NSLog` from the plugin is not visible in `flutter run` output - use Console.app. The detection sync
  logs `[CallWaitingTone] sync: ...` in DEBUG builds.
- The pre-warm-before-voice-processing ordering is the load-bearing invariant of the playback path. Any
  refactor that delays player creation past the start of the call's audio engine reintroduces the
  near-silence failure, and nothing will crash or log - it will just be quiet.
