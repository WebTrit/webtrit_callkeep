#import "WebtritCallkeepPlugin.h"

#import <AVFoundation/AVFoundation.h>
#import <PushKit/PushKit.h>
#import <CallKit/CallKit.h>
#import <Intents/Intents.h>
#import <UserNotifications/UserNotifications.h>

#import "Generated.h"
#import "Converters.h"
#import "NSUUID+v5.h"
#import "CallWaitingTonePlayer.h"

static NSString *const OptionsKey = @"WebtritCallkeepPluginOptions";

@interface WebtritCallkeepPlugin ()<PKPushRegistryDelegate, CXProviderDelegate, CXCallObserverDelegate, WTPPushRegistryHostApi, WTPHostApi, WTPHostSoundApi>
@end

@implementation WebtritCallkeepPlugin {
  NSObject<FlutterPluginRegistrar> *_registrar;
  WTPPushRegistryDelegateFlutterApi *_pushRegistryDelegateFlutterApi;
  PKPushRegistry *_pushRegistry;
  WTPDelegateFlutterApi *_delegateFlutterApi;
  CXProvider *_provider;
  AVAudioPlayer *_ringback;
  CallWaitingTonePlayer *_callWaitingTone;
  NSMutableSet<NSUUID *> *_ownCallUuids;
  NSMutableSet<NSUUID *> *_answeringCallUuids;
  BOOL _callWaitingToneOwnCallsOnly;
  CXCallController *_callController;
  BOOL _driveIdleTimerDisabled;
  // Deferred CallKit registration: when one incoming call is answered, the other
  // still-ringing own calls are taken out of CallKit (reported ended) so the
  // "active + ringing" state never forms and the system call-waiting screen does
  // not cover the app. A deferred call keeps living on the Flutter side; it
  // re-enters CallKit the moment it is answered (report + answer in one go).
  NSMutableSet<NSUUID *> *_deferredCallUuids;
  // Last CXCallUpdate per incoming call, kept so a deferred call can be
  // re-reported with its original handle/name when answered.
  NSMutableDictionary<NSUUID *, CXCallUpdate *> *_incomingCallUpdates;
  // A deferred call re-enters CallKit under a FRESH UUID: re-reporting the
  // original (already reported-ended) UUID makes CallKit accept the report but
  // yield a zombie call whose answer transaction fails (InvalidAction) about
  // half the time. The Flutter side keeps addressing the call by its stable
  // original UUID; these two maps translate at the plugin boundary.
  NSMutableDictionary<NSUUID *, NSUUID *> *_currentUuidByOriginal;
  NSMutableDictionary<NSUUID *, NSUUID *> *_originalUuidByCurrent;
  // Deferred calls re-enter CallKit as OUTGOING (CXStartCallAction): an
  // incoming re-report raises the system incoming UI over the app and iOS
  // keeps the user on its own in-call screen after the answer. The start
  // action is fulfilled natively and surfaces to Flutter as a plain
  // performAnswerCall; this set marks the in-flight ones.
  NSMutableSet<NSUUID *> *_outgoingReattachUuids;
  // UUIDs this plugin has reported ended but whose end CXCallObserver has not
  // confirmed yet. The observer's list lags a reportCall:ended by a beat, so
  // right after a defer (or the report+instant-end a push mandates for a
  // deferred call) the ended entry still reads as "live". Liveness predicates
  // must treat these as gone, or they mistake a just-ended flash for a real
  // registration and un-defer a legitimately deferred call.
  NSMutableSet<NSUUID *> *_pendingCallKitEndUuids;
  // Originals deferred AT ANSWER TIME: unlike arrival-deferred calls they
  // already had a CallKit life and were journaled (answeredElsewhere) when
  // taken out, so a later missed-call journal entry would duplicate them.
  NSMutableSet<NSUUID *> *_answerDeferredUuids;
  // Missed deferred calls waiting for their journal entry. The entry is a
  // report+instant-end flash, which must not run next to a live call (the
  // ringing flash would summon the call-waiting prompt) - so it queues here
  // and flushes when the registry is empty.
  NSMutableArray<CXCallUpdate *> *_pendingMissedJournalUpdates;
  // Throwaway UUIDs of in-flight journal flashes: never answerable, and their
  // CX actions must not reach the Flutter side.
  NSMutableSet<NSUUID *> *_journalFlashUuids;
  // Master switch of the deferred-registration mechanism (an app opts in via
  // CallkeepIOSOptions). Off by default: every incoming call is registered
  // with CallKit as before and the system call-waiting behavior applies.
  BOOL _deferredCallKitRegistration;
}

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
  WebtritCallkeepPlugin *instance = [[WebtritCallkeepPlugin alloc] initWithRegistrar:registrar];
  [instance restoreSetUp];
  [registrar addApplicationDelegate:instance];
  [registrar publish:instance];
}

- (instancetype)initWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
#ifdef DEBUG
  NSLog(@"[Callkeep][initWithRegistrar:]");
#endif
  self = [super init];
  if (self) {
    _registrar = registrar;
    NSObject<FlutterBinaryMessenger> *binaryMessenger = [_registrar messenger];
    _pushRegistryDelegateFlutterApi = [[WTPPushRegistryDelegateFlutterApi alloc] initWithBinaryMessenger:binaryMessenger];
    SetUpWTPPushRegistryHostApi(binaryMessenger, self);
    _delegateFlutterApi = [[WTPDelegateFlutterApi alloc] initWithBinaryMessenger:binaryMessenger];
    SetUpWTPHostApi(binaryMessenger, self);
    SetUpWTPHostSoundApi(binaryMessenger, self);
    // Created eagerly so it can be pre-warmed from the very first
    // didActivateAudioSession callback, not from the first play request.
    _callWaitingTone = [[CallWaitingTonePlayer alloc] init];
    _ownCallUuids = [NSMutableSet set];
    _answeringCallUuids = [NSMutableSet set];
    _deferredCallUuids = [NSMutableSet set];
    _incomingCallUpdates = [NSMutableDictionary dictionary];
    _currentUuidByOriginal = [NSMutableDictionary dictionary];
    _originalUuidByCurrent = [NSMutableDictionary dictionary];
    _outgoingReattachUuids = [NSMutableSet set];
    _pendingCallKitEndUuids = [NSMutableSet set];
    _answerDeferredUuids = [NSMutableSet set];
    _pendingMissedJournalUpdates = [NSMutableArray array];
    _journalFlashUuids = [NSMutableSet set];
    _callWaitingToneOwnCallsOnly = YES;
  }
  return self;
}

- (void)dealloc {
#ifdef DEBUG
  NSLog(@"[Callkeep][dealloc]");
#endif
  NSObject<FlutterBinaryMessenger> *binaryMessenger = [_registrar messenger];
  SetUpWTPHostApi(binaryMessenger, nil);
  SetUpWTPPushRegistryHostApi(binaryMessenger, nil);
  SetUpWTPHostSoundApi(binaryMessenger, nil);
}

- (BOOL)isSetUp {
  if (_provider != nil) {
#ifdef DEBUG
    NSLog(@"[Callkeep][isSetUp] YES");
#endif
    return YES;
  } else {
#ifdef DEBUG
    NSLog(@"[Callkeep][isSetUp] NO");
#endif
    return NO;
  }
}

- (void)restoreSetUp {
  WTPIOSOptions *iosOptions = [self getUserDefaultsIosOptions];
  if (iosOptions != nil) {
#ifdef DEBUG
    NSLog(@"[Callkeep][restoreSetUp] processed");
#endif
    _pushRegistry = [[PKPushRegistry alloc] initWithQueue:nil];
    _pushRegistry.delegate = self;
    _pushRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];

    _provider = [[CXProvider alloc] initWithConfiguration:[iosOptions toCallKitWithRegistrar:_registrar]];
    [_provider setDelegate:self queue:nil];

    _callController = [[CXCallController alloc] init];
    [_callController.callObserver setDelegate:self queue:dispatch_get_main_queue()];
    [self syncCallWaitingTone:_callController.callObserver];

    _callWaitingToneOwnCallsOnly =
        iosOptions.callWaitingToneOwnCallsOnly == nil || iosOptions.callWaitingToneOwnCallsOnly.boolValue;
    _deferredCallKitRegistration =
        iosOptions.deferredCallKitRegistration != nil && iosOptions.deferredCallKitRegistration.boolValue;

    if (iosOptions.ringbackSound != nil) {
      _ringback = [self createRingbackPlayer:iosOptions.ringbackSound];
    }
 
    _driveIdleTimerDisabled = iosOptions.driveIdleTimerDisabled;
  } else {
#ifdef DEBUG
    NSLog(@"[Callkeep][restoreSetUp] skipped");
#endif
  }
}

#pragma mark - WTPPushRegistryHostApi

- (nullable NSString *)pushTokenForPushTypeVoIP:(FlutterError **)error {
  if (_pushRegistry != nil) {
#ifdef DEBUG
    NSLog(@"[Callkeep][pushTokenForPushTypeVoIP] processed");
#endif
    return [[_pushRegistry pushTokenForType:PKPushTypeVoIP] toHexString];
  } else {
#ifdef DEBUG
    NSLog(@"[Callkeep][pushTokenForPushTypeVoIP] skipped");
#endif
    return nil;
  }
}

#pragma mark - WTPHostApi

- (nullable NSNumber *)isSetUp:(FlutterError **)error {
  return @([self isSetUp]);
}

- (void)setUp:(WTPOptions *)options
   completion:(void (^)(FlutterError *))completion {
  WTPIOSOptions *iosOptions = options.ios;
  if ([self setUserDefaultsIosOptions:iosOptions] == YES) {
#ifdef DEBUG
    NSLog(@"[Callkeep][setUp] processed");
#endif
    // apply new options
    if (_pushRegistry == nil) {
      _pushRegistry = [[PKPushRegistry alloc] initWithQueue:nil];
      _pushRegistry.delegate = self;
      _pushRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
    }
    if (_provider == nil) {
      _provider = [[CXProvider alloc] initWithConfiguration:[iosOptions toCallKitWithRegistrar:_registrar]];
      [_provider setDelegate:self queue:nil];
    } else {
      _provider.configuration = [iosOptions toCallKitWithRegistrar:_registrar];
    }
    if (_callController == nil) {
      _callController = [[CXCallController alloc] init];
      [_callController.callObserver setDelegate:self queue:dispatch_get_main_queue()];
      [self syncCallWaitingTone:_callController.callObserver];
    }
    _callWaitingToneOwnCallsOnly =
        iosOptions.callWaitingToneOwnCallsOnly == nil || iosOptions.callWaitingToneOwnCallsOnly.boolValue;
    _deferredCallKitRegistration =
        iosOptions.deferredCallKitRegistration != nil && iosOptions.deferredCallKitRegistration.boolValue;
    
    if (_ringback == nil && iosOptions.ringbackSound != nil) {
      _ringback = [self createRingbackPlayer:iosOptions.ringbackSound];
    }
    
    _driveIdleTimerDisabled = iosOptions.driveIdleTimerDisabled;
  } else {
#ifdef DEBUG
    NSLog(@"[Callkeep][setUp] skipped");
#endif
  }
  completion(nil);
}

- (void)tearDown:(void (^)(FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][tearDown]");
#endif
  if (_callController != nil) {
    [_callController.callObserver setDelegate:nil queue:nil];
    _callController = nil;
  }
  [_callWaitingTone stop];
  [_ownCallUuids removeAllObjects];
  [_answeringCallUuids removeAllObjects];
  [_pendingCallKitEndUuids removeAllObjects];
  [_deferredCallUuids removeAllObjects];
  [_incomingCallUpdates removeAllObjects];
  [_currentUuidByOriginal removeAllObjects];
  [_originalUuidByCurrent removeAllObjects];
  [_outgoingReattachUuids removeAllObjects];
  [_answerDeferredUuids removeAllObjects];
  [_pendingMissedJournalUpdates removeAllObjects];
  [_journalFlashUuids removeAllObjects];
  if (_provider != nil) {
    [_provider invalidate];
    _provider = nil;
  }
  if (_pushRegistry != nil) {
    _pushRegistry.desiredPushTypes = [NSSet set];
    _pushRegistry = nil;
  }
  [self removeUserDefaultsIosOptions];
  completion(nil);
}

- (void)reportNewIncomingCall:(NSString *)uuidString
                       handle:(WTPHandle *)handle
                  displayName:(NSString *)displayName
                     hasVideo:(BOOL)hasVideo
                   completion:(void (^)(WTPIncomingCallError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][reportNewIncomingCall] uuidString = %@", uuidString);
#endif
  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
  callUpdate.remoteHandle = [handle toCallKit];
  callUpdate.localizedCallerName = displayName;
  callUpdate.hasVideo = hasVideo;
  callUpdate.supportsGrouping = NO;
  callUpdate.supportsUngrouping = NO;
  callUpdate.supportsHolding = YES;
  callUpdate.supportsDTMF = YES;
  NSUUID *callUuid = [[NSUUID alloc] initWithUUIDString:uuidString];
  // This very call may already live in CallKit, registered by the racing push
  // path. Then it must go down the normal-report branch (the provider answers
  // with a callUUIDAlreadyExists dedup error the Dart side tolerates) - the
  // deferral checks below would otherwise mistake the call for one arriving
  // next to a live call and mis-defer it against itself.
  BOOL alreadyLive = callUuid != nil && [self isLiveCallKitCallWithUuid:[self currentUuidFor:callUuid]];
  if (alreadyLive) {
    [self healStaleDeferredMark:callUuid];
  }
  if (!alreadyLive && callUuid != nil && [_deferredCallUuids containsObject:callUuid]) {
    // The call is deferred (kept out of CallKit while another call was being
    // answered). Swallow the registration so it does not resurrect the system
    // UI, but remember the freshest metadata for the re-report at answer time.
    // The caller is told "success" - on the Flutter side the call is alive and
    // proceeds exactly as if it were registered.
    NSLog(@"[Callkeep][reportNewIncomingCall] suppressed for deferred call %@", uuidString);
    _incomingCallUpdates[callUuid] = callUpdate;
    completion(nil, nil);
    return;
  }
  if (_deferredCallKitRegistration && !alreadyLive && callUuid != nil && [self hasOwnLiveCallKitCall]) {
    // At most one call is represented in CallKit: an incoming call arriving
    // while any own call already lives there (ringing or active) is deferred
    // right away - it rings app-side only and enters CallKit when answered.
    // Registering it would raise the system call-waiting screen over the app.
    NSLog(@"[Callkeep][reportNewIncomingCall] deferring on arrival %@", uuidString);
    [_deferredCallUuids addObject:callUuid];
    _incomingCallUpdates[callUuid] = callUpdate;
    completion(nil, nil);
    return;
  }
  // When the call already lives in CallKit its registry entry may be a
  // promoted ALIAS: the dedup report must target that alias, or CallKit sees
  // a brand-new UUID and registers a duplicate ringing entry.
  NSUUID *reportUuid = alreadyLive ? [self currentUuidFor:callUuid] : callUuid;
  if (callUuid != nil) {
    [_ownCallUuids addObject:reportUuid];
    _incomingCallUpdates[callUuid] = callUpdate;
  }
  [_provider reportNewIncomingCallWithUUID:reportUuid
                                    update:callUpdate
                                completion:^(NSError *error) {
                                  if (error == nil) {
                                    dispatch_async(dispatch_get_main_queue(), ^{
                                      // A fresh registration supersedes any unconfirmed end of
                                      // this UUID from an earlier call with the same callId.
                                      [self->_pendingCallKitEndUuids removeObject:reportUuid];
                                    });
                                    [self assignIdleTimerDisabled:callUpdate.hasVideo];
                                    completion(nil, nil);
                                  } else if ([error.domain isEqualToString:CXErrorDomainIncomingCall]) {
                                    completion([WTPIncomingCallError makeWithValue:CXErrorCodeIncomingCallErrorToPigeon((CXErrorCodeIncomingCallError) error.code)], nil);
                                  } else {
                                    completion(nil, [FlutterError errorWithCode:error.domain
                                                                        message:[error description]
                                                                        details:nil]);
                                  }
                                }];
}

- (void)reportConnectingOutgoingCall:(NSString *)uuidString
                          completion:(void (^)(FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][reportConnectingOutgoingCall] uuidString = %@", uuidString);
#endif
  [_provider reportOutgoingCallWithUUID:[[NSUUID alloc] initWithUUIDString:uuidString]
                startedConnectingAtDate:nil];
  completion(nil);
}

- (void)reportConnectedOutgoingCall:(NSString *)uuidString
                         completion:(void (^)(FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][reportConnectedOutgoingCall] uuidString = %@", uuidString);
#endif
  [_provider reportOutgoingCallWithUUID:[[NSUUID alloc] initWithUUIDString:uuidString]
                        connectedAtDate:nil];
  completion(nil);
}

- (void)reportUpdateCall:(NSString *)uuidString
                  handle:(nullable WTPHandle *)handle
             displayName:(nullable NSString *)displayName
                hasVideo:(nullable NSNumber *)hasVideo
        proximityEnabled:(nullable NSNumber *)proximityEnabled
              completion:(void (^)(FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][reportUpdateCall] uuidString = %@", uuidString);
#endif
  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
  if (handle != nil) {
    callUpdate.remoteHandle = [handle toCallKit];
  }
  if (displayName != nil) {
    callUpdate.localizedCallerName = displayName;
  }
  if (hasVideo != nil) {
    callUpdate.hasVideo = [hasVideo boolValue];
  }
  if (proximityEnabled != nil) {
     if ([proximityEnabled boolValue]) {
          [[AVAudioSession sharedInstance] setMode: AVAudioSessionModeVoiceChat error:nil];
     } else {
//          Can cause bug when the speaker automatically turns on during audio calls at the moment when the user declines an active call
//          needs additional testing
          [[AVAudioSession sharedInstance] setMode: AVAudioSessionModeVideoChat error:nil];
     }
  }
    
  NSUUID *updateUuid = [[NSUUID alloc] initWithUUIDString:uuidString];
  // Keep the cached registration metadata in sync: deferred-call re-entry
  // (answer re-attach, promotion, missed-call journaling) re-reports from the
  // cache, and without the merge it would show arrival-time data (e.g. a bare
  // number instead of the later-resolved contact name).
  CXCallUpdate *cached = updateUuid != nil ? _incomingCallUpdates[updateUuid] : nil;
  if (cached != nil) {
    if (handle != nil) cached.remoteHandle = callUpdate.remoteHandle;
    if (displayName != nil) cached.localizedCallerName = displayName;
    if (hasVideo != nil) cached.hasVideo = callUpdate.hasVideo;
  }
  [_provider reportCallWithUUID:[self currentUuidFor:updateUuid]
                        updated:callUpdate];
  [self assignIdleTimerDisabled:callUpdate.hasVideo];
  completion(nil);
}

- (void)reportEndCall:(NSString *)uuidString
                displayName:(NSString *)displayName
               reason:(WTPEndCallReason *)reason
           completion:(void (^)(FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][reportEndCall] uuidString = %@", uuidString);
#endif
  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:uuidString];
  // A stale deferred mark on a call that really lives in CallKit (a lost
  // report race) must not skip the provider report below - that would leave
  // the registry entry ringing as a ghost.
  [self healStaleDeferredMark:uuid];
  if (uuid != nil && [_deferredCallUuids containsObject:uuid]) {
    // The deferred call is not in CallKit, so there is no entry to end. A call
    // the user never got to answer must still leave a missed-call trace in the
    // system journal - but only an ARRIVAL-deferred one: a call deferred at
    // answer time was already journaled (answeredElsewhere) when its CallKit
    // entry was ended, and a second entry would contradict it. The journal
    // flash itself is queued and runs only once the registry is empty (see
    // flushPendingMissedJournalIfIdle) - flashing a ringing entry next to a
    // live call would summon the very call-waiting prompt this mechanism
    // exists to prevent. The shared missed-call notification below fires
    // either way.
    NSLog(@"[Callkeep][reportEndCall] clearing deferred call %@", uuidString);
    [_deferredCallUuids removeObject:uuid];
    BOOL journaledAlready = [_answerDeferredUuids containsObject:uuid];
    [_answerDeferredUuids removeObject:uuid];
    CXCallUpdate *lastUpdate = _incomingCallUpdates[uuid];
    [_incomingCallUpdates removeObjectForKey:uuid];
    CXCallEndedReason endReason = [reason toCallKit];
    BOOL missed = endReason == CXCallEndedReasonUnanswered || endReason == CXCallEndedReasonRemoteEnded;
    if (missed && !journaledAlready && lastUpdate != nil) {
      [_pendingMissedJournalUpdates addObject:lastUpdate];
      [self flushPendingMissedJournalIfIdle];
    }
  } else {
    if (uuid != nil) {
      [_incomingCallUpdates removeObjectForKey:uuid];
    }
    [self reportCallKitEndOf:[self currentUuidFor:uuid] reason:[reason toCallKit]];
  }
  [self assignIdleTimerDisabled:NO];
    
    if ([reason toCallKit] == CXCallEndedReasonUnanswered) {
        UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];

        UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
        content.title = @"Missed Call";
        content.body = displayName;
        content.sound = [UNNotificationSound defaultSound];
        
        NSString *identifier = [NSString stringWithFormat:@"missed call-%@", displayName];

        UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:identifier content:content trigger:nil];

        [center addNotificationRequest:request withCompletionHandler:^(NSError * _Nullable error) {
          if (error != nil) {
            NSLog(@"[Callkeep][reportEndCall] Error adding notification: %@", error);
          }
        }];
    }

  completion(nil);
}

- (void)             startCall:(NSString *)uuidString
                        handle:(WTPHandle *)handle
displayNameOrContactIdentifier:(NSString *)displayNameOrContactIdentifier
                         video:(BOOL)video
              proximityEnabled:(BOOL)proximityEnabled
                    completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][startCall] uuidString = %@", uuidString);
#endif
  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:uuidString];
    
// Can be ignored, coz webrtc doing same on getusermedia before call
// and needs to edit package to override this behavior, so we can let it go
// if (proximityEnabled) {
//     [[AVAudioSession sharedInstance] setMode: AVAudioSessionModeVoiceChat error:nil];
// } else {
//     [[AVAudioSession sharedInstance] setMode: AVAudioSessionModeVideoChat error:nil];
// }

  CXStartCallAction *action = [[CXStartCallAction alloc] initWithCallUUID:uuid
                                                                   handle:[handle toCallKit]];
  if (displayNameOrContactIdentifier != nil) {
    action.contactIdentifier = displayNameOrContactIdentifier;
  }
  action.video = video;
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  [self requestTransaction:transaction completion:^(WTPCallRequestError *pigeonError, FlutterError *flutterError) {
    if (pigeonError == nil && flutterError == nil) {
      CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
      callUpdate.remoteHandle = action.handle;
      callUpdate.localizedCallerName = action.contactIdentifier;
      callUpdate.hasVideo = action.video;
      callUpdate.supportsGrouping = NO;
      callUpdate.supportsUngrouping = NO;
      callUpdate.supportsHolding = YES;
      callUpdate.supportsDTMF = YES;
      [self->_provider reportCallWithUUID:uuid
                                  updated:callUpdate];

      completion(nil, nil);
    } else {
      completion(pigeonError, flutterError);
    }
  }];
}

/// The CallKit-side UUID for a call the Flutter side addresses by [uuid]:
/// the fresh alias when the call re-entered CallKit after a deferral, the
/// same UUID otherwise.
- (NSUUID *)currentUuidFor:(NSUUID *)uuid {
  if (uuid == nil) return nil;
  return _currentUuidByOriginal[uuid] ?: uuid;
}

/// The stable Flutter-side UUID for a CallKit call [uuid] (reverse of
/// [currentUuidFor:]).
- (NSUUID *)originalUuidFor:(NSUUID *)uuid {
  if (uuid == nil) return nil;
  return _originalUuidByCurrent[uuid] ?: uuid;
}

/// Whether any own call currently lives in CallKit. Decided from the call
/// observer, not from the bookkeeping set: the observer may drop an ended
/// call from its list before the cleanup pass sees it, so a stale entry in
/// the set would keep deferring every new incoming call forever.
- (BOOL)hasOwnLiveCallKitCall {
  for (CXCall *call in _callController.callObserver.calls) {
    if (call.hasEnded) continue;
    if ([_pendingCallKitEndUuids containsObject:call.UUID]) continue;
    if ([_ownCallUuids containsObject:call.UUID]) return YES;
  }
  return NO;
}

/// The CallKit-side UUID of any own live call, or nil when none exists.
/// Same observer-based liveness rules as [hasOwnLiveCallKitCall].
- (NSUUID *)anyOwnLiveCallKitCallUuid {
  for (CXCall *call in _callController.callObserver.calls) {
    if (call.hasEnded) continue;
    if ([_pendingCallKitEndUuids containsObject:call.UUID]) continue;
    if ([_ownCallUuids containsObject:call.UUID]) return call.UUID;
  }
  return nil;
}

/// Reports [uuid] ended to CallKit and remembers it as pending-end until the
/// observer confirms, so the liveness predicates stop counting it immediately
/// instead of a beat later.
- (void)reportCallKitEndOf:(NSUUID *)uuid reason:(CXCallEndedReason)reason {
  if (uuid == nil) return;
  [_pendingCallKitEndUuids addObject:uuid];
  [_provider reportCallWithUUID:uuid endedAtDate:nil reason:reason];
}

/// Whether [uuid] itself is currently represented in CallKit as a live call.
/// The signaling and push paths race to report the same call (both are kept
/// deliberately - the push is the fallback for a dead socket), so either path
/// can run while the other has already registered this very call. In that case
/// the call must NOT be treated as "arriving next to a live call" and deferred:
/// the live call it would be deferred behind is itself. A stale deferred mark
/// on a really-registered call breaks both exits - decline skips the CallKit
/// end (leaving a ghost ringing entry), and answer re-attaches a second
/// registry entry next to the still-ringing original, which flips the registry
/// into "ringing + active" and summons the system call-waiting screen.
- (BOOL)isLiveCallKitCallWithUuid:(NSUUID *)uuid {
  if (uuid == nil) return NO;
  if ([_pendingCallKitEndUuids containsObject:uuid]) return NO;
  for (CXCall *call in _callController.callObserver.calls) {
    if (call.hasEnded) continue;
    if ([call.UUID isEqual:uuid]) return YES;
  }
  return NO;
}

/// Drops a stale deferred mark from a call that in fact lives in CallKit
/// (see [isLiveCallKitCallWithUuid]). Returns YES when the mark was stale.
- (BOOL)healStaleDeferredMark:(NSUUID *)uuid {
  if (uuid == nil || ![_deferredCallUuids containsObject:uuid]) return NO;
  if (![self isLiveCallKitCallWithUuid:[self currentUuidFor:uuid]]) return NO;
  NSLog(@"[Callkeep][healStaleDeferredMark] %@ is live in CallKit - clearing deferred mark", uuid.UUIDString);
  [_deferredCallUuids removeObject:uuid];
  [_ownCallUuids addObject:uuid];
  return YES;
}

/// Takes every other still-ringing own incoming call out of CallKit right
/// before [answeredUuid] goes active, marking them deferred. Without this the
/// answer flips CallKit into "active + ringing" and iOS covers the app with
/// its full-screen call-waiting prompt for the remaining call.
- (void)deferOtherRingingOwnCallsForAnswerOf:(NSUUID *)answeredUuid {
  if (!_deferredCallKitRegistration) return;
  for (CXCall *call in _callController.callObserver.calls) {
    if (call.hasEnded || call.hasConnected || call.outgoing) continue;
    // Already reported ended by us; the observer just has not confirmed yet.
    // Deferring the fading entry would strand a dead call in the deferred set.
    if ([_pendingCallKitEndUuids containsObject:call.UUID]) continue;
    if ([call.UUID isEqual:answeredUuid]) continue;
    if (![_ownCallUuids containsObject:call.UUID]) continue;
    if ([_answeringCallUuids containsObject:call.UUID]) continue;
    // The deferred set lives in the Flutter side's stable UUID space: a call
    // that re-entered CallKit under a fresh alias (a promoted one) must be
    // marked by its original UUID or later lookups by the Dart side miss it.
    NSUUID *original = [self originalUuidFor:call.UUID];
    if (![_deferredCallUuids containsObject:original]) {
      [_deferredCallUuids addObject:original];
      [_answerDeferredUuids addObject:original];
      NSLog(@"[Callkeep][deferOtherRingingOwnCalls] deferring %@", original.UUIDString);
      [self reportCallKitEndOf:call.UUID reason:CXCallEndedReasonAnsweredElsewhere];
    }
  }
}

- (void)answerCall:(NSString *)uuidString
        completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][answerCall] uuidString = %@", uuidString);
#endif
  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:uuidString];
  [self deferOtherRingingOwnCallsForAnswerOf:[self currentUuidFor:uuid]];

  // A stale deferred mark on a call that really lives in CallKit (a lost
  // report race) must not send the answer down the re-attach branch: that
  // would add an outgoing entry next to the still-ringing original and summon
  // the system call-waiting screen. Heal the mark and answer normally.
  [self healStaleDeferredMark:uuid];

  if (uuid != nil && [_deferredCallUuids containsObject:uuid]) {
    // A deferred call re-enters CallKit only now, at answer time, under a
    // FRESH UUID (re-reporting the original reported-ended UUID hands back a
    // zombie whose answer fails) and as an OUTGOING call: an incoming
    // re-report raises the system incoming UI over the app and iOS keeps the
    // user on its own in-call screen after answering. The start action never
    // reaches Flutter as a start - performStartCallAction fulfills it
    // natively and drives performAnswerCall for the original UUID instead.
    NSUUID *freshUuid = [NSUUID UUID];
    NSLog(@"[Callkeep][answerCall] re-attaching deferred call %@ as outgoing %@", uuidString, freshUuid.UUIDString);
    CXCallUpdate *callUpdate = _incomingCallUpdates[uuid] ?: [[CXCallUpdate alloc] init];
    CXHandle *handle = callUpdate.remoteHandle
        ?: [[CXHandle alloc] initWithType:CXHandleTypePhoneNumber value:@"unknown"];
    [_deferredCallUuids removeObject:uuid];
    [_answerDeferredUuids removeObject:uuid];
    _currentUuidByOriginal[uuid] = freshUuid;
    _originalUuidByCurrent[freshUuid] = uuid;
    [_outgoingReattachUuids addObject:freshUuid];
    [_ownCallUuids addObject:freshUuid];
    CXStartCallAction *startAction = [[CXStartCallAction alloc] initWithCallUUID:freshUuid handle:handle];
    startAction.contactIdentifier = callUpdate.localizedCallerName;
    CXTransaction *startTransaction = [[CXTransaction alloc] initWithAction:startAction];
    // The alias state was committed above so the perform callback can already
    // translate; if CallKit refuses the transaction that state must be undone,
    // or the call becomes unanswerable (retries would target an alias CallKit
    // never created) and the in-flight re-attach mark blocks promotion for
    // the rest of the session.
    [self requestTransaction:startTransaction completion:^(WTPCallRequestError *err, FlutterError *ferr) {
      if (err != nil || ferr != nil) {
        dispatch_async(dispatch_get_main_queue(), ^{
          NSLog(@"[Callkeep][answerCall] re-attach start failed - rolling back to deferred %@", uuidString);
          [self->_outgoingReattachUuids removeObject:freshUuid];
          [self->_ownCallUuids removeObject:freshUuid];
          if ([self->_currentUuidByOriginal[uuid] isEqual:freshUuid]) {
            [self->_currentUuidByOriginal removeObjectForKey:uuid];
          }
          [self->_originalUuidByCurrent removeObjectForKey:freshUuid];
          [self->_deferredCallUuids addObject:uuid];
        });
      }
      completion(err, ferr);
    }];
    return;
  }

  CXAnswerCallAction *action = [[CXAnswerCallAction alloc] initWithCallUUID:[self currentUuidFor:uuid]];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  // If the answer transaction is refused, the calls deferred for it were
  // pulled out of the system UI for nothing - bring them back.
  [self requestTransaction:transaction completion:^(WTPCallRequestError *err, FlutterError *ferr) {
    if (err != nil || ferr != nil) {
      dispatch_async(dispatch_get_main_queue(), ^{
        [self restoreAnswerDeferredCallsAfterFailedAnswer];
      });
    }
    completion(err, ferr);
  }];
}

- (void)setSpeaker:(NSString *)uuidString
        enabled:(BOOL)enabled
      completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
    NSLog(@"[Callkeep][setSpeaker] uuidString = %@ muted = %d", uuidString, enabled);
#endif
}

- (void)endCall:(NSString *)uuidString
     completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][endCall] uuidString = %@", uuidString);
#endif
  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:uuidString];
  // A stale deferred mark on a call that really lives in CallKit (a lost
  // report race) must not take the local shortcut below - skipping the
  // CXEndCallAction would leave the registry entry ringing as a ghost that
  // later flips "connected + ringing" and summons the system screen.
  [self healStaleDeferredMark:uuid];
  if (uuid != nil && [_deferredCallUuids containsObject:uuid]) {
    // A deferred call has no CallKit representation to end - clean up locally
    // and drive the same delegate path a fulfilled CXEndCallAction would, so
    // the Flutter side terminates the call exactly as usual.
    NSLog(@"[Callkeep][endCall] ending deferred call %@ locally", uuidString);
    [_deferredCallUuids removeObject:uuid];
    [_answerDeferredUuids removeObject:uuid];
    [_incomingCallUpdates removeObjectForKey:uuid];
    [_delegateFlutterApi performEndCall:uuidString
                             completion:^(NSNumber *fulfill, FlutterError *error) {}];
    completion(nil, nil);
    return;
  }
  CXEndCallAction *action = [[CXEndCallAction alloc] initWithCallUUID:[self currentUuidFor:uuid]];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  [self requestTransaction:transaction completion:completion];
}

- (void)setHeld:(NSString *)uuidString
         onHold:(BOOL)onHold
     completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][setHeld] uuidString = %@ held = %d", uuidString, onHold);
#endif
  CXSetHeldCallAction *action = [[CXSetHeldCallAction alloc] initWithCallUUID:[self currentUuidFor:[[NSUUID alloc] initWithUUIDString:uuidString]]
                                                                       onHold:onHold];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  [self requestTransaction:transaction completion:completion];
}

- (void)setMuted:(NSString *)uuidString
           muted:(BOOL)muted
      completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][setMuted] uuidString = %@ muted = %d", uuidString, muted);
#endif
  CXSetMutedCallAction *action = [[CXSetMutedCallAction alloc] initWithCallUUID:[self currentUuidFor:[[NSUUID alloc] initWithUUIDString:uuidString]]
                                                                          muted:muted];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  [self requestTransaction:transaction completion:completion];
}

- (void)sendDTMF:(NSString *)uuidString
             key:(NSString *)key
      completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][sendDTMF] uuidString = %@ key = %@", uuidString, key);
#endif
  CXPlayDTMFCallAction *action = [[CXPlayDTMFCallAction alloc] initWithCallUUID:[self currentUuidFor:[[NSUUID alloc] initWithUUIDString:uuidString]]
                                                                         digits:key
                                                                           type:CXPlayDTMFCallActionTypeSingleTone];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  [self requestTransaction:transaction completion:completion];
}

#pragma mark - WTPHostApi - helpers

- (void)requestTransaction:(CXTransaction *)transaction completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
  [_callController requestTransaction:transaction completion:^(NSError *error) {
    if (error != nil) {
      NSLog(@"[Callkeep][requestTransaction] %@ failed: domain=%@ code=%ld (%@)",
            [[transaction.actions firstObject] class], error.domain, (long)error.code, error.localizedDescription);
    }
    if (error == nil) {
      completion(nil, nil);
    } else if ([error.domain isEqualToString:CXErrorDomainRequestTransaction]) {
      completion([WTPCallRequestError makeWithValue:CXErrorCodeRequestTransactionErrorToPigeon((CXErrorCodeRequestTransactionError) error.code)], nil);
    } else {
      completion(nil, [FlutterError errorWithCode:error.domain
                                          message:[error description]
                                          details:nil]);
    }
  }];
}

#pragma mark - WTPHostSoundApi

- (AVAudioPlayer *) createRingbackPlayer:(NSString *)soundAsset {
    NSString* key = [_registrar lookupKeyForAsset:soundAsset];
    NSString* path = [[NSBundle mainBundle] pathForResource:key ofType:nil];
    NSURL *soundFileURL = [NSURL fileURLWithPath:path];
    AVAudioPlayer* p = [[AVAudioPlayer alloc] initWithContentsOfURL:soundFileURL error:nil];
    p.numberOfLoops = -1;
    return p;
}

- (void)playRingbackSound:(void (^)(FlutterError * _Nullable))completion{
    if(_ringback != nil)[_ringback play];
    completion(nil);
}

- (void)stopRingbackSound:(void (^)(FlutterError * _Nullable))completion{
    if(_ringback != nil)[_ringback pause];

    completion(nil);
}

#pragma mark - FlutterApplicationLifeCycleDelegate

- (BOOL) application:(nonnull UIApplication *)application
continueUserActivity:(nonnull NSUserActivity *)userActivity
  restorationHandler:(nonnull void (^)(NSArray *_Nonnull))restorationHandler {
#ifdef DEBUG
  NSLog(@"[Callkeep][FlutterApplicationLifeCycleDelegate][application:continueUserActivity:restorationHandler:]");
#endif
  INInteraction *interaction = userActivity.interaction;
  if (interaction == nil) {
    return NO;
  }
  INIntent *intent = interaction.intent;

  INPerson *person;
  BOOL isVideoCall = NO;

  if ([intent isKindOfClass:[INStartAudioCallIntent class]]) {
    INStartAudioCallIntent *startAudioCallIntent = (INStartAudioCallIntent *) intent;
    person = [startAudioCallIntent.contacts firstObject];
  } else if ([intent isKindOfClass:[INStartVideoCallIntent class]]) {
    INStartVideoCallIntent *startVideoCallIntent = (INStartVideoCallIntent *) intent;
    person = [startVideoCallIntent.contacts firstObject];
    isVideoCall = YES;
  } else if (@available(iOS 13, *)) {
    if ([intent isKindOfClass:[INStartCallIntent class]]) {
      INStartCallIntent *startCallIntent = (INStartCallIntent *) intent;
      person = [startCallIntent.contacts firstObject];
      isVideoCall = startCallIntent.callCapability == INCallCapabilityVideoCall;
    }
  }

  if (person != nil && person.personHandle != nil) {
    [_delegateFlutterApi continueStartCallIntentHandle:[person.personHandle toPigeon]
                                           displayName:[person displayName]
                                                 video:isVideoCall
                                            completion:^(FlutterError *error) {}];

    return YES;
  } else {
    return NO;
  }
}

#pragma mark - PKPushRegistryDelegate

- (void)pushRegistry:(PKPushRegistry *)registry didUpdatePushCredentials:(PKPushCredentials *)pushCredentials forType:(PKPushType)type {
#ifdef DEBUG
  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didUpdatePushCredentials:forType:] pushCredentials = %@ type = %@", pushCredentials, type);
#endif
  if (type == PKPushTypeVoIP) {
    [_pushRegistryDelegateFlutterApi didUpdatePushTokenForPushTypeVoIP:[pushCredentials.token toHexString]
                                                            completion:^(FlutterError *error) {}];
  }
}

- (void)pushRegistry:(PKPushRegistry *)registry didInvalidatePushTokenForType:(PKPushType)type {
#ifdef DEBUG
  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didInvalidatePushTokenForType:] type = %@", type);
#endif
  if (type == PKPushTypeVoIP) {
    [_pushRegistryDelegateFlutterApi didUpdatePushTokenForPushTypeVoIP:nil completion:^(FlutterError *error) {}];
  }
}

- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type withCompletionHandler:(void (^)(void))completion {
#ifdef DEBUG
  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didReceiveIncomingPushWithPayload:forType:withCompletionHandler:] type = %@", type);
#endif
  [self didReceiveIncomingPushWithPayloadForPushTypeVoIP:payload withCompletionHandler:completion];
}

- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type {
#ifdef DEBUG
  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didReceiveIncomingPushWithPayload:forType:] type = %@", type);
#endif
  [self didReceiveIncomingPushWithPayloadForPushTypeVoIP:payload withCompletionHandler:^() {}];
}

/// Called when a VoIP push notification is received by the system.
///
/// This method is responsible for parsing the VoIP payload and reporting a new
/// incoming call to CallKit. It prepares the `CXCallUpdate` and uses the UUID
/// derived from the call ID to avoid race conditions.
///
///  Important:
/// - Before calling `reportNewIncomingCallWithUUID:update:completion:`,
///   this method calls `configureAudioSession()` to preconfigure the audio session.
///   This is a workaround for a known issue (Radar #28774388) where `didActivateAudioSession`
///   might not be triggered correctly on cold start if audio session is not set up early enough.
///
/// @param payload The VoIP push payload containing call information.
/// @param completion A completion handler to signal that processing is complete.
- (void)didReceiveIncomingPushWithPayloadForPushTypeVoIP:(PKPushPayload *)payload withCompletionHandler:(void (^)(void))completion {
  NSDictionary *dictionaryPayload = payload.dictionaryPayload;
#ifdef DEBUG
  NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:] payload = %@", dictionaryPayload);
#endif
  id handleTypeObject = dictionaryPayload[@"handleType"];
  id handleValueObject = dictionaryPayload[@"handleValue"];
  id displayNameObject = dictionaryPayload[@"displayName"];
  id hasVideoObject = dictionaryPayload[@"hasVideo"];
  id callIdObject = dictionaryPayload[@"callId"];

  if ([handleTypeObject isKindOfClass:[NSString class]] == NO ||
    [handleValueObject isKindOfClass:[NSString class]] == NO ||
    [callIdObject isKindOfClass:[NSString class]] == NO) {
#ifdef DEBUG
    NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:] payload wrong format");
#endif
    // A malformed push still carries the reporting obligation. When an own
    // call already lives in CallKit the report hides behind its UUID - the
    // expected duplicate rejection meets the obligation with no registry
    // entry (the Apple-DTS-recommended hiding technique, see the deferred
    // report below and https://developer.apple.com/forums/thread/805255).
    // Otherwise (or when the shield ends in the race and the report
    // unexpectedly succeeds) a blank call is reported and immediately ended.
    NSUUID *shieldUuid = [self anyOwnLiveCallKitCallUuid];
    NSUUID *uuid = shieldUuid ?: [[NSUUID alloc] init];
    CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];

    if (shieldUuid == nil) {
      [_ownCallUuids addObject:uuid];
    }
    [_provider reportNewIncomingCallWithUUID:uuid
                                      update:callUpdate
                                  completion:^(NSError *error) {
                                    BOOL expectedDuplicate = shieldUuid != nil && error != nil &&
                                        [error.domain isEqualToString:CXErrorDomainIncomingCall] &&
                                        error.code == CXErrorCodeIncomingCallErrorCallUUIDAlreadyExists;
                                    if (error == nil) {
                                      [self reportCallKitEndOf:uuid reason:CXCallEndedReasonFailed];
                                    } else if (!expectedDuplicate) {
                                      NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:][reportNewIncomingCallWithUUID] payload wrong format error = %@",
                                            error);
                                    }
                                    completion();
                                  }];
    return;
  }

  NSString *handleType = handleTypeObject;
  NSString *handleValue = handleValueObject;
  NSString *displayName = [displayNameObject isKindOfClass:[NSString class]] ? displayNameObject : nil;

  // Check if hasVideoObject is a string and convert it to NSNumber
  NSNumber *hasVideo;
  if ([hasVideoObject isKindOfClass:[NSNumber class]]) {
      hasVideo = hasVideoObject;
  } else if ([hasVideoObject isKindOfClass:[NSString class]]) {
      NSString *hasVideoString = (NSString *)hasVideoObject;
      BOOL hasVideoBool = [hasVideoString boolValue];
      hasVideo = @(hasVideoBool);
  } else {
      hasVideo = @(NO);
  }
  // Log the value of hasVideo after initialization
  NSLog(@"hasVideo after initialization: %@", hasVideo);

  NSString *callId = callIdObject;

  // It is crucial to use UUID version 5 (namespace name-based) based on callId to get the call UUID for reportNewIncomingCallWithUUID.
  // Such UUID allows overcoming possible races between VoIP push and relevant signaling events.
  NSUUID *uuid = [NSUUID makeWithName:callId namespace:[[NSUUID alloc] initWithUUIDString:NAMESPACE_OID]];

  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
  callUpdate.remoteHandle = [[CXHandle alloc] initWithType:CXHandleTypeFromString(handleType)
                                                     value:handleValue];
  callUpdate.localizedCallerName = displayName;
  callUpdate.hasVideo = [hasVideo boolValue];
  callUpdate.supportsGrouping = NO;
  callUpdate.supportsUngrouping = NO;
  callUpdate.supportsHolding = YES;
  callUpdate.supportsDTMF = YES;
  // This very call may already live in CallKit, registered by the racing
  // signaling path - then the "own live call" the deferral check sees is the
  // call itself, and deferring would strand a really-registered call behind
  // the deferred mark. Report normally instead: the provider answers with the
  // callUUIDAlreadyExists dedup error the Dart side tolerates.
  BOOL alreadyLive = [self isLiveCallKitCallWithUuid:[self currentUuidFor:uuid]];
  if (alreadyLive) {
    [self healStaleDeferredMark:uuid];
  }
  // Deferred either explicitly (taken out of CallKit at answer time) or on
  // arrival (another own call already lives in CallKit - at most one call is
  // represented there). PushKit still obliges reporting, so the report for a
  // deferred call deliberately targets the UUID of a call that already lives
  // in CallKit (the shield): CallKit rejects it with callUUIDAlreadyExists,
  // nothing enters the registry, and the obligation is met without a
  // report-and-immediately-end cycle. The call rings app-side and enters
  // CallKit under a fresh UUID when answered. Only when no live own call
  // remains to hide behind does the report fall back to the call's own UUID
  // followed by an immediate end.
  // The hiding technique is recommended by Apple DTS ("you can safely 'hide'
  // call reports by intentionally reporting new calls using the UUID of your
  // existing call... Either way, you won't crash"), including the fallback
  // for a shield that ended in the race ("If the existing call has ended,
  // then you'll get a new call started"):
  // https://developer.apple.com/forums/thread/805255
  // https://developer.apple.com/forums/thread/775874
  BOOL isDeferred = _deferredCallKitRegistration && !alreadyLive &&
      ([_deferredCallUuids containsObject:uuid] || [self hasOwnLiveCallKitCall]);
  NSUUID *shieldUuid = isDeferred ? [self anyOwnLiveCallKitCallUuid] : nil;
  // When the call already lives in CallKit its registry entry may be a
  // promoted ALIAS: the dedup report must target that alias, or CallKit sees
  // a brand-new UUID and registers a duplicate ringing entry.
  NSUUID *reportUuid;
  if (alreadyLive) {
    reportUuid = [self currentUuidFor:uuid];
  } else if (shieldUuid != nil) {
    reportUuid = shieldUuid;
  } else {
    reportUuid = uuid;
  }
  if (isDeferred) {
    [_deferredCallUuids addObject:uuid];
    _incomingCallUpdates[uuid] = callUpdate;
  } else {
    [_ownCallUuids addObject:reportUuid];
    _incomingCallUpdates[uuid] = callUpdate;
  }
  // The shared audio session is prepared only when the report will surface a
  // real ringing entry: a deferred call never rings system-side, and touching
  // the session category/mode during a live call risks an audio glitch on it.
  if (!isDeferred) {
    [self configureAudioSession];
  }
  [_provider reportNewIncomingCallWithUUID:reportUuid
                                    update:callUpdate
                                completion:^(NSError *error) {
                                  // The rejection the shield report is designed to produce: the
                                  // obligation is met, the registry untouched - a success for the
                                  // deferred call, not an error.
                                  BOOL expectedDuplicate = shieldUuid != nil && error != nil &&
                                      [error.domain isEqualToString:CXErrorDomainIncomingCall] &&
                                      error.code == CXErrorCodeIncomingCallErrorCallUUIDAlreadyExists;
                                  dispatch_async(dispatch_get_main_queue(), ^{
                                    if (error == nil) {
                                      // A fresh registration supersedes any unconfirmed end of
                                      // this UUID from an earlier call with the same callId.
                                      [self->_pendingCallKitEndUuids removeObject:reportUuid];
                                    }
                                    if (isDeferred && error == nil) {
                                      // Either no shield existed, or the shield ended between the
                                      // liveness check and the report and CallKit registered a real
                                      // ringing entry under its UUID - take it right back out so
                                      // the system UI does not show the deferred call.
                                      NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP] re-ending deferred call %@", reportUuid.UUIDString);
                                      [self reportCallKitEndOf:reportUuid reason:CXCallEndedReasonAnsweredElsewhere];
                                    }
                                    if (expectedDuplicate) {
                                      NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP] push for deferred call %@ hidden behind live call %@", uuid.UUIDString, shieldUuid.UUIDString);
                                    }
                                  });
                                  WTPIncomingCallError *incomingCallError = nil;
                                  if (error != nil && !expectedDuplicate) {
                                    if ([error.domain isEqualToString:CXErrorDomainIncomingCall]) {
                                      incomingCallError = [WTPIncomingCallError makeWithValue:CXErrorCodeIncomingCallErrorToPigeon((CXErrorCodeIncomingCallError) error.code)];
                                    } else {
                                      NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:][reportNewIncomingCallWithUUID] error = %@", error);
                                      incomingCallError = [WTPIncomingCallError makeWithValue:WTPIncomingCallErrorEnumInternal];
                                    }
                                  }

                                  [self->_delegateFlutterApi didPushIncomingCallHandle:[callUpdate.remoteHandle toPigeon]
                                                                           displayName:callUpdate.localizedCallerName
                                                                                 video:callUpdate.hasVideo
                                                                                callId:callId
                                                                                  uuid:[uuid UUIDString]
                                                                                 error:incomingCallError
                                                                            completion:^(FlutterError *error) {
                                                                              [self assignIdleTimerDisabled:callUpdate.hasVideo];
                                                                              completion();
                                                                            }];
                                }];
}

/// Prepares the AVAudioSession for an incoming call.
///
/// This method sets the audio session category and mode to support VoIP audio routing.
/// It is called before `reportNewIncomingCallWithUUID` to ensure the audio session
/// is properly configured before CallKit attempts to activate it.
///
/// Do not call `setActive:YES` here — CallKit is responsible for activating the audio session.
///
/// Best practice:
/// - Call this method *before* reporting the call to CallKit (e.g., in `didReceiveIncomingPush…`)
///   to prevent timing issues where `didActivateAudioSession` fails to trigger.
- (void)configureAudioSession {
    AVAudioSession *session = [AVAudioSession sharedInstance];
    NSError *error = nil;

    BOOL success = [session setCategory:AVAudioSessionCategoryPlayAndRecord
                            withOptions:AVAudioSessionCategoryOptionAllowBluetooth
                                  error:&error];
    if (!success) {
        NSLog(@"[Callkeep] Failed to set category: %@", error);
    }

    success = [session setMode:AVAudioSessionModeVoiceChat error:&error];
    if (!success) {
        NSLog(@"[Callkeep] Failed to set mode: %@", error);
    }
}

#pragma mark - CXProviderDelegate

#pragma mark - CXCallObserverDelegate

- (void)callObserver:(CXCallObserver *)callObserver callChanged:(CXCall *)call {
  [self syncCallWaitingTone:callObserver];
  [self promoteDeferredCallIfRegistryVacant];
  [self flushPendingMissedJournalIfIdle];
}

/// Re-enters a still-ringing deferred call into CallKit when the registry has
/// no own live call left (e.g. the visible call was declined while the
/// deferred one keeps ringing app-side). Without this the remaining call is
/// invisible and silent outside the app and just times out. Re-reporting as
/// incoming is safe here - the call-waiting prompt needs a connected call
/// next to the ringing one, and the registry is empty. Skipped while an
/// answer is in flight: the registry is momentarily empty mid-transition and
/// promoting then would race the re-attach.
- (void)promoteDeferredCallIfRegistryVacant {
  if (_deferredCallUuids.count == 0) return;
  if (_outgoingReattachUuids.count > 0 || _answeringCallUuids.count > 0) return;
  if ([self hasOwnLiveCallKitCall]) return;
  // Only an entry with cached metadata can be re-reported meaningfully; a
  // metadata-less one must not block eligible entries behind it.
  NSUUID *original = nil;
  for (NSUUID *candidate in _deferredCallUuids) {
    if (_incomingCallUpdates[candidate] != nil) { original = candidate; break; }
  }
  if (original == nil) return;
  NSLog(@"[Callkeep][promoteDeferredCall] registry vacant - promoting %@", original.UUIDString);
  [self reenterDeferredCallIntoCallKit:original];
}

/// Re-reports the deferred [original] as an incoming call under a fresh alias
/// and rolls the alias state back if CallKit refuses the report.
- (void)reenterDeferredCallIntoCallKit:(NSUUID *)original {
  CXCallUpdate *lastUpdate = _incomingCallUpdates[original];
  if (lastUpdate == nil) return;
  NSUUID *freshUuid = [NSUUID UUID];
  [_deferredCallUuids removeObject:original];
  [_answerDeferredUuids removeObject:original];
  _currentUuidByOriginal[original] = freshUuid;
  _originalUuidByCurrent[freshUuid] = original;
  [_ownCallUuids addObject:freshUuid];
  [_provider reportNewIncomingCallWithUUID:freshUuid
                                    update:lastUpdate
                                completion:^(NSError *error) {
    dispatch_async(dispatch_get_main_queue(), ^{
      if (error != nil) {
        NSLog(@"[Callkeep][reenterDeferredCall] re-entry failed (%@) - back to deferred", error);
        [self->_ownCallUuids removeObject:freshUuid];
        if ([self->_currentUuidByOriginal[original] isEqual:freshUuid]) {
          [self->_currentUuidByOriginal removeObjectForKey:original];
        }
        [self->_originalUuidByCurrent removeObjectForKey:freshUuid];
        [self->_deferredCallUuids addObject:original];
      }
    });
  }];
}

/// After a failed answer the calls deferred for it were pulled out of the
/// system UI for nothing - re-enter them so they ring visibly again. Skipped
/// while an own call is connected: an incoming re-report next to a connected
/// call summons the system prompt, and the in-call tone still announces them.
- (void)restoreAnswerDeferredCallsAfterFailedAnswer {
  if (_answerDeferredUuids.count == 0) return;
  for (CXCall *call in _callController.callObserver.calls) {
    if (!call.hasEnded && call.hasConnected && [_ownCallUuids containsObject:call.UUID]) return;
  }
  for (NSUUID *original in [_answerDeferredUuids allObjects]) {
    if (![_deferredCallUuids containsObject:original]) {
      [_answerDeferredUuids removeObject:original];
      continue;
    }
    NSLog(@"[Callkeep][restoreAnswerDeferred] answer failed - restoring %@", original.UUIDString);
    [self reenterDeferredCallIntoCallKit:original];
  }
}

/// Writes the queued missed-call journal entries (report + instant end under
/// a throwaway UUID) once no call - own or foreign - is live: flashing a
/// ringing entry next to a live call would summon the system call-waiting
/// prompt. Flash UUIDs are guarded in the perform callbacks so a tap on the
/// sub-second banner never reaches the Flutter side.
- (void)flushPendingMissedJournalIfIdle {
  if (_pendingMissedJournalUpdates.count == 0) return;
  if (_outgoingReattachUuids.count > 0 || _answeringCallUuids.count > 0) return;
  for (CXCall *call in _callController.callObserver.calls) {
    if (!call.hasEnded) return;
  }
  NSArray<CXCallUpdate *> *entries = [_pendingMissedJournalUpdates copy];
  [_pendingMissedJournalUpdates removeAllObjects];
  for (CXCallUpdate *entry in entries) {
    NSUUID *flashUuid = [NSUUID UUID];
    [_journalFlashUuids addObject:flashUuid];
    NSLog(@"[Callkeep][missedJournal] flash-reporting missed deferred call as %@", flashUuid.UUIDString);
    [_provider reportNewIncomingCallWithUUID:flashUuid
                                      update:entry
                                  completion:^(NSError *error) {
      dispatch_async(dispatch_get_main_queue(), ^{
        if (error == nil) {
          [self reportCallKitEndOf:flashUuid reason:CXCallEndedReasonUnanswered];
        } else {
          [self->_journalFlashUuids removeObject:flashUuid];
        }
      });
    }];
  }
}

/// Mirrors the Android connection-service behavior: a soft call-waiting beep plays
/// while one call is connected (or held) and another incoming call is ringing, and
/// stops as soon as that state ends (answered, declined, hung up on either side).
/// With callWaitingToneOwnCallsOnly (default) only this app's own calls are counted -
/// CXCallObserver reports every CallKit call on the device, including cellular and
/// other VoIP apps. Known scope boundary: an outgoing call that is still dialing has
/// hasConnected == NO, so a second incoming call during it produces no tone (same as
/// the Android connection-service logic, which plays the full ringtone there).
- (void)syncCallWaitingTone:(CXCallObserver *)callObserver {
  // An end we reported for a UUID that is not (or no longer) in the registry
  // never gets an observer confirmation - prune pending-end entries whose
  // UUID has left the observer's list entirely, or they accumulate and shadow
  // later genuine registrations of the same deterministic UUID.
  if (_pendingCallKitEndUuids.count > 0) {
    NSMutableSet<NSUUID *> *listedUuids = [NSMutableSet set];
    for (CXCall *call in callObserver.calls) {
      [listedUuids addObject:call.UUID];
    }
    [_pendingCallKitEndUuids intersectSet:listedUuids];
  }
  BOOL hasConnected = NO;
  BOOL hasRingingIncoming = NO;
  for (CXCall *call in callObserver.calls) {
    if (call.hasEnded || call.hasConnected) {
      [_answeringCallUuids removeObject:call.UUID];
    }
    if (call.hasEnded) {
      [_pendingCallKitEndUuids removeObject:call.UUID];
      [_journalFlashUuids removeObject:call.UUID];
      [_ownCallUuids removeObject:call.UUID];
      // Keep the update of a deferred call - it is needed for the re-report at
      // answer time; any other ended call's metadata is no longer useful.
      if (![_deferredCallUuids containsObject:call.UUID]) {
        [_incomingCallUpdates removeObjectForKey:call.UUID];
      }
      NSUUID *original = _originalUuidByCurrent[call.UUID];
      if (original != nil) {
        // A re-deferred call keeps its metadata and its NEWER alias: this end
        // may be the confirmation of an OLD alias reported ended when the call
        // went back to deferred, arriving after a fresh alias already exists.
        if (![_deferredCallUuids containsObject:original]) {
          [_incomingCallUpdates removeObjectForKey:original];
        }
        if ([_currentUuidByOriginal[original] isEqual:call.UUID]) {
          [_currentUuidByOriginal removeObjectForKey:original];
        }
        [_originalUuidByCurrent removeObjectForKey:call.UUID];
      }
      continue;
    }
    if (_callWaitingToneOwnCallsOnly && ![_ownCallUuids containsObject:call.UUID]) {
      continue;  // a foreign CallKit call (cellular / another VoIP app)
    }
    if (call.hasConnected) {
      hasConnected = YES;
    } else if (!call.outgoing && ![_answeringCallUuids containsObject:call.UUID]) {
      hasRingingIncoming = YES;
    }
  }
  // A deferred incoming call has no CallKit entry, so the loop above cannot
  // see it - but it IS a ringing incoming call, and during a conversation the
  // tone is the only audible cue it exists.
  if (_deferredCallUuids.count > 0) {
    hasRingingIncoming = YES;
  }
#ifdef DEBUG
  NSLog(@"[CallWaitingTone] sync: calls=%lu connected=%d ringingIncoming=%d",
        (unsigned long)callObserver.calls.count, hasConnected, hasRingingIncoming);
#endif
  if (hasConnected && hasRingingIncoming) {
    [_callWaitingTone play];
  } else {
    [_callWaitingTone stop];
  }
}

- (void)providerDidReset:(CXProvider *)provider {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][providerDidReset:]");
#endif
  [_callWaitingTone stop];
  [_ownCallUuids removeAllObjects];
  [_answeringCallUuids removeAllObjects];
  [_deferredCallUuids removeAllObjects];
  [_incomingCallUpdates removeAllObjects];
  [_currentUuidByOriginal removeAllObjects];
  [_originalUuidByCurrent removeAllObjects];
  [_outgoingReattachUuids removeAllObjects];
  [_pendingCallKitEndUuids removeAllObjects];
  [_answerDeferredUuids removeAllObjects];
  [_pendingMissedJournalUpdates removeAllObjects];
  [_journalFlashUuids removeAllObjects];
  [_delegateFlutterApi didReset:^(FlutterError *error) {}];
}

- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performStartCallAction:]");
#endif
  if ([_outgoingReattachUuids containsObject:action.callUUID]) {
    // A deferred call re-entering CallKit as outgoing: fulfill natively and
    // surface to Flutter as the answer of the original incoming call.
    [_outgoingReattachUuids removeObject:action.callUUID];
    NSUUID *original = [self originalUuidFor:action.callUUID];
    NSLog(@"[Callkeep][performStartCallAction] outgoing re-attach %@ -> answering %@",
          action.callUUID.UUIDString, original.UUIDString);
    [action fulfill];
    [_provider reportOutgoingCallWithUUID:action.callUUID startedConnectingAtDate:nil];
    [_delegateFlutterApi performAnswerCall:original.UUIDString
                                completion:^(NSNumber *fulfill, FlutterError *error) {
                                  if (error != nil || [fulfill boolValue] != YES) {
                                    NSLog(@"[Callkeep][performStartCallAction] re-attach answer failed, ending %@",
                                          action.callUUID.UUIDString);
                                    [self reportCallKitEndOf:action.callUUID reason:CXCallEndedReasonFailed];
                                  } else {
                                    [self->_provider reportOutgoingCallWithUUID:action.callUUID
                                                                connectedAtDate:nil];
                                  }
                                }];
    return;
  }
  [_ownCallUuids addObject:action.callUUID];
  [_delegateFlutterApi performStartCall:action.callUUID.UUIDString
                                 handle:[action.handle toPigeon]
         displayNameOrContactIdentifier:action.contactIdentifier
                                  video:action.video
                             completion:^(NSNumber *fulfill, FlutterError *error) {
                               if (error != nil || [fulfill boolValue] != YES) {
                                 [action fail];
                               } else {
                                 [action fulfill];
                                 [self assignIdleTimerDisabled:action.video];
                               }
                             }];
}

- (void)provider:(CXProvider *)provider performAnswerCallAction:(CXAnswerCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performAnswerCallAction:]");
#endif
  // A journal-only flash entry (a missed-call record being written) is dead
  // app-side and must never be answerable.
  if ([_journalFlashUuids containsObject:action.callUUID]) {
    [action fail];
    return;
  }
  // Covers answers coming through CallKit's own UI (lock screen, banner) as
  // well - by this point no Dart code has run yet, so the deferral must happen
  // here to keep the remaining ringing call from raising the system prompt.
  [self deferOtherRingingOwnCallsForAnswerOf:action.callUUID];
  // Suppress the call-waiting tone from the moment the user accepts: the CXCall stays
  // "ringing" until the answer roundtrip fulfills the action, which can take seconds.
  [_answeringCallUuids addObject:action.callUUID];
  [_delegateFlutterApi performAnswerCall:[self originalUuidFor:action.callUUID].UUIDString
                              completion:^(NSNumber *fulfill, FlutterError *error) {
                                if (error != nil || [fulfill boolValue] != YES) {
                                  [self->_answeringCallUuids removeObject:action.callUUID];
                                  [action fail];
                                  [self restoreAnswerDeferredCallsAfterFailedAnswer];
                                } else {
                                  [action fulfill];
                                }
                              }];
}

- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performEndCallAction:]");
#endif
  // A journal-only flash entry ends by itself; the Flutter side knows nothing
  // about its throwaway UUID.
  if ([_journalFlashUuids containsObject:action.callUUID]) {
    [action fulfill];
    return;
  }
  [_delegateFlutterApi performEndCall:[self originalUuidFor:action.callUUID].UUIDString
                           completion:^(NSNumber *fulfill, FlutterError *error) {
                             if (error != nil || [fulfill boolValue] != YES) {
                               [action fail];
                             } else {
                               [action fulfill];
                               [self assignIdleTimerDisabled:NO];
                             }
                           }];
}

- (void)provider:(CXProvider *)provider performSetHeldCallAction:(CXSetHeldCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performSetHeldCallAction:]");
#endif
  [_delegateFlutterApi performSetHeld:[self originalUuidFor:action.callUUID].UUIDString
                               onHold:action.onHold
                           completion:^(NSNumber *fulfill, FlutterError *error) {
                             if (error != nil || [fulfill boolValue] != YES) {
                               [action fail];
                             } else {
                               [action fulfill];
                             }
                           }];
}

- (void)provider:(CXProvider *)provider performSetMutedCallAction:(CXSetMutedCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performSetMutedCallAction:]");
#endif
  [_delegateFlutterApi performSetMuted:[self originalUuidFor:action.callUUID].UUIDString
                                 muted:action.muted
                            completion:^(NSNumber *fulfill, FlutterError *error) {
                              if (error != nil || [fulfill boolValue] != YES) {
                                [action fail];
                              } else {
                                [action fulfill];
                              }
                            }];
}

- (void)provider:(CXProvider *)provider performSetGroupCallAction:(CXSetGroupCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performSetGroupCallAction:] - not implemented");
#endif
  [action fail];
}

- (void)provider:(CXProvider *)provider performPlayDTMFCallAction:(CXPlayDTMFCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performPlayDTMFCallAction:]");
#endif
  if (action.type != CXPlayDTMFCallActionTypeSingleTone) {
    [action fail];
    return;
  }
  [_delegateFlutterApi performSendDTMF:[self originalUuidFor:action.callUUID].UUIDString
                                   key:action.digits
                            completion:^(NSNumber *fulfill, FlutterError *error) {
                              if (error != nil || [fulfill boolValue] != YES) {
                                [action fail];
                              } else {
                                [action fulfill];
                              }
                            }];
}

- (void)provider:(CXProvider *)provider timedOutPerformingAction:(CXAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:timedOutPerformingAction:] action = %@", action);
#endif
}

- (void)provider:(CXProvider *)provider didActivateAudioSession:(AVAudioSession *)audioSession {
#ifdef DEBUG
  NSLog(@"[CallKeep][CXProviderDelegate][provider:didActivateAudioSession:]");
#endif
  // Pre-warm the call-waiting tone player before the Dart side starts the WebRTC
  // voice-processing engine (playback sources created after it are near-silent).
  [_callWaitingTone onAudioSessionActivated];
  // Re-evaluate the tone on the observer's queue: this callback runs on the provider's
  // private queue and must not resume playback from stale state on its own.
  dispatch_async(dispatch_get_main_queue(), ^{
    CXCallController *controller = self->_callController;
    if (controller != nil) {
      [self syncCallWaitingTone:controller.callObserver];
    }
  });
  [_delegateFlutterApi didActivateAudioSession:^(FlutterError *error) {}];
}

- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession {
#ifdef DEBUG
  NSLog(@"[CallKeep][CXProviderDelegate][provider:didDeactivateAudioSession:]");
#endif
  [_callWaitingTone onAudioSessionDeactivated];
  [_delegateFlutterApi didDeactivateAudioSession:^(FlutterError *error) {}];
}

#pragma mark - helpers

- (WTPIOSOptions *)getUserDefaultsIosOptions {
  NSData *data = [[NSUserDefaults standardUserDefaults] objectForKey:OptionsKey];
  if (data != nil) {
    NSError *error;
    NSDictionary *iosOptionsMap = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&error];
    if (iosOptionsMap != nil) {
      // Currently is necessary to overcome possible inconsistence with the options dictionary because of add/remove/rename properties of WTPIOSOptions class.
      // This logic could be refactored when the following limitation is eliminated - Initialization isn't supported for fields in Pigeon data classes.
      NSDictionary *iosOptionsMapDefault = @{
        @"driveIdleTimerDisabled": @YES,
      };
      NSMutableDictionary *iosOptionsMapMerged = [[NSMutableDictionary alloc] init];
      [iosOptionsMapMerged addEntriesFromDictionary:iosOptionsMapDefault];
      [iosOptionsMapMerged addEntriesFromDictionary:iosOptionsMap];
      return [WTPIOSOptions fromMap:iosOptionsMapMerged];
    }
  }
  return nil;
}

- (BOOL)setUserDefaultsIosOptions:(WTPIOSOptions *)iosOptions {
  NSDictionary *iosOptionsMap = [iosOptions toMap];
  NSError *error;
  NSData *data = [NSJSONSerialization dataWithJSONObject:iosOptionsMap options:kNilOptions error:&error];
  NSData *currentData = [[NSUserDefaults standardUserDefaults] objectForKey:OptionsKey];
  if (currentData == nil || [data isEqualToData:currentData] != YES) {
    [[NSUserDefaults standardUserDefaults] setObject:data forKey:OptionsKey];
    return YES;
  } else {
    return NO;
  }
}

- (void)removeUserDefaultsIosOptions {
  [[NSUserDefaults standardUserDefaults] removeObjectForKey:OptionsKey];
}

- (void)assignIdleTimerDisabled:(BOOL)value {
  if (_driveIdleTimerDisabled) {
    [UIApplication sharedApplication].idleTimerDisabled = value;
  }
}

@end
