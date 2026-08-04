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
  if (callUuid != nil && [_deferredCallUuids containsObject:callUuid]) {
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
  if (callUuid != nil && [self hasOwnLiveCallKitCall]) {
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
  if (callUuid != nil) {
    [_ownCallUuids addObject:callUuid];
    _incomingCallUpdates[callUuid] = callUpdate;
  }
  [_provider reportNewIncomingCallWithUUID:callUuid
                                    update:callUpdate
                                completion:^(NSError *error) {
                                  if (error == nil) {
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
    
  [_provider reportCallWithUUID:[self currentUuidFor:[[NSUUID alloc] initWithUUIDString:uuidString]]
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
  if (uuid != nil && [_deferredCallUuids containsObject:uuid]) {
    // The deferred call is not in CallKit - nothing to report, just forget it
    // (remote hangup / cancel of a call that was ringing app-side only).
    NSLog(@"[Callkeep][reportEndCall] clearing deferred call %@", uuidString);
    [_deferredCallUuids removeObject:uuid];
    [_incomingCallUpdates removeObjectForKey:uuid];
    completion(nil);
    return;
  }
  if (uuid != nil) {
    [_incomingCallUpdates removeObjectForKey:uuid];
  }

  [_provider reportCallWithUUID:[self currentUuidFor:uuid]
                    endedAtDate:nil
                         reason:[reason toCallKit]];
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
    if ([_ownCallUuids containsObject:call.UUID]) return YES;
  }
  return NO;
}

/// Takes every other still-ringing own incoming call out of CallKit right
/// before [answeredUuid] goes active, marking them deferred. Without this the
/// answer flips CallKit into "active + ringing" and iOS covers the app with
/// its full-screen call-waiting prompt for the remaining call.
- (void)deferOtherRingingOwnCallsForAnswerOf:(NSUUID *)answeredUuid {
  for (CXCall *call in _callController.callObserver.calls) {
    if (call.hasEnded || call.hasConnected || call.outgoing) continue;
    if ([call.UUID isEqual:answeredUuid]) continue;
    if (![_ownCallUuids containsObject:call.UUID]) continue;
    if ([_answeringCallUuids containsObject:call.UUID]) continue;
    if (![_deferredCallUuids containsObject:call.UUID]) {
      [_deferredCallUuids addObject:call.UUID];
      NSLog(@"[Callkeep][deferOtherRingingOwnCalls] deferring %@", call.UUID.UUIDString);
      [_provider reportCallWithUUID:call.UUID
                        endedAtDate:nil
                             reason:CXCallEndedReasonAnsweredElsewhere];
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

  if (uuid != nil && [_deferredCallUuids containsObject:uuid]) {
    // A deferred call re-enters CallKit only now, at answer time, and under a
    // FRESH UUID: re-reporting the original (already reported-ended) UUID makes
    // CallKit hand back a zombie whose answer transaction fails. The alias maps
    // keep the Flutter side on the stable original UUID. The answer is
    // requested straight from the report completion, so the ringing state
    // exists for the shortest possible moment.
    NSUUID *freshUuid = [NSUUID UUID];
    NSLog(@"[Callkeep][answerCall] re-reporting deferred call %@ as %@", uuidString, freshUuid.UUIDString);
    CXCallUpdate *callUpdate = _incomingCallUpdates[uuid] ?: [[CXCallUpdate alloc] init];
    [_provider reportNewIncomingCallWithUUID:freshUuid
                                      update:callUpdate
                                  completion:^(NSError *error) {
                                    // The report completion arrives on the provider's private queue;
                                    // all plugin state is main-thread-confined (delegate and observer
                                    // callbacks run on main), so hop before touching it.
                                    dispatch_async(dispatch_get_main_queue(), ^{
                                      if (error != nil) {
                                        NSLog(@"[Callkeep][answerCall] deferred re-report failed: domain=%@ code=%ld (%@)",
                                              error.domain, (long)error.code, error.localizedDescription);
                                        completion([WTPCallRequestError makeWithValue:WTPCallRequestErrorEnumInternal], nil);
                                        return;
                                      }
                                      NSLog(@"[Callkeep][answerCall] deferred re-report ok (%@ -> %@), requesting answer",
                                            uuid.UUIDString, freshUuid.UUIDString);
                                      [self->_deferredCallUuids removeObject:uuid];
                                      self->_currentUuidByOriginal[uuid] = freshUuid;
                                      self->_originalUuidByCurrent[freshUuid] = uuid;
                                      [self->_ownCallUuids addObject:freshUuid];
                                      CXAnswerCallAction *action = [[CXAnswerCallAction alloc] initWithCallUUID:freshUuid];
                                      CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
                                      [self requestTransaction:transaction completion:completion];
                                    });
                                  }];
    return;
  }

  CXAnswerCallAction *action = [[CXAnswerCallAction alloc] initWithCallUUID:[self currentUuidFor:uuid]];
  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];

  [self requestTransaction:transaction completion:completion];
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
  if (uuid != nil && [_deferredCallUuids containsObject:uuid]) {
    // A deferred call has no CallKit representation to end - clean up locally
    // and drive the same delegate path a fulfilled CXEndCallAction would, so
    // the Flutter side terminates the call exactly as usual.
    NSLog(@"[Callkeep][endCall] ending deferred call %@ locally", uuidString);
    [_deferredCallUuids removeObject:uuid];
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
    NSUUID *uuid = [[NSUUID alloc] init];
    CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];

    [_ownCallUuids addObject:uuid];
    [_provider reportNewIncomingCallWithUUID:uuid
                                      update:callUpdate
                                  completion:^(NSError *error) {
                                    if (error != nil) {
                                      NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:][reportNewIncomingCallWithUUID] payload wrong format error = %@",
                                            error);
                                    } else {
                                      [_provider reportCallWithUUID:uuid
                                                        endedAtDate:nil
                                                             reason:CXCallEndedReasonFailed];
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

  [self configureAudioSession];

  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
  callUpdate.remoteHandle = [[CXHandle alloc] initWithType:CXHandleTypeFromString(handleType)
                                                     value:handleValue];
  callUpdate.localizedCallerName = displayName;
  callUpdate.hasVideo = [hasVideo boolValue];
  callUpdate.supportsGrouping = NO;
  callUpdate.supportsUngrouping = NO;
  callUpdate.supportsHolding = YES;
  callUpdate.supportsDTMF = YES;
  // Deferred either explicitly (taken out of CallKit at answer time) or on
  // arrival (another own call already lives in CallKit - at most one call is
  // represented there). PushKit still obliges reporting, so the call is
  // reported and taken right back out; it rings app-side and enters CallKit
  // under a fresh UUID when answered.
  BOOL isDeferred = [_deferredCallUuids containsObject:uuid] || [self hasOwnLiveCallKitCall];
  if (isDeferred) {
    [_deferredCallUuids addObject:uuid];
    _incomingCallUpdates[uuid] = callUpdate;
  } else {
    [_ownCallUuids addObject:uuid];
    _incomingCallUpdates[uuid] = callUpdate;
  }
  [_provider reportNewIncomingCallWithUUID:uuid
                                    update:callUpdate
                                completion:^(NSError *error) {
                                  if (isDeferred && error == nil) {
                                    // PushKit obliges reporting every VoIP push, but this call is
                                    // deferred (rings app-side only) - take it right back out so
                                    // the system UI does not resurrect it.
                                    NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP] re-ending deferred call %@", uuid.UUIDString);
                                    [self->_provider reportCallWithUUID:uuid
                                                            endedAtDate:nil
                                                                 reason:CXCallEndedReasonAnsweredElsewhere];
                                  }
                                  WTPIncomingCallError *incomingCallError = nil;
                                  if (error != nil) {
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
  BOOL hasConnected = NO;
  BOOL hasRingingIncoming = NO;
  for (CXCall *call in callObserver.calls) {
    if (call.hasEnded || call.hasConnected) {
      [_answeringCallUuids removeObject:call.UUID];
    }
    if (call.hasEnded) {
      [_ownCallUuids removeObject:call.UUID];
      // Keep the update of a deferred call - it is needed for the re-report at
      // answer time; any other ended call's metadata is no longer useful.
      if (![_deferredCallUuids containsObject:call.UUID]) {
        [_incomingCallUpdates removeObjectForKey:call.UUID];
      }
      NSUUID *original = _originalUuidByCurrent[call.UUID];
      if (original != nil) {
        [_incomingCallUpdates removeObjectForKey:original];
        [_currentUuidByOriginal removeObjectForKey:original];
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
  [_delegateFlutterApi didReset:^(FlutterError *error) {}];
}

- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performStartCallAction:]");
#endif
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
                                } else {
                                  [action fulfill];
                                }
                              }];
}

- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action {
#ifdef DEBUG
  NSLog(@"[Callkeep][CXProviderDelegate][provider:performEndCallAction:]");
#endif
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
