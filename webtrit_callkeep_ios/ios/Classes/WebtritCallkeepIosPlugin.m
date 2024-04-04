//#import "WebtritCallkeepIosPlugin.h"
//#import <AVFoundation/AVFoundation.h>
//#import <PushKit/PushKit.h>
//#import <CallKit/CallKit.h>
//#import <Intents/Intents.h>
//
//#import "Generated.h"
//#import "Converters.h"
//#import "NSUUID+v5.h"
//
//static NSString *const OptionsKey = @"WebtritCallkeepPluginOptions";
//
//@interface WebtritCallkeepIosPlugin ()<PKPushRegistryDelegate, CXProviderDelegate, WTPPushRegistryHostApi, WTPHostApi>
//@end
//
//@implementation WebtritCallkeepIosPlugin {
//  NSObject<FlutterPluginRegistrar> *_registrar;
//  WTPPushRegistryDelegateFlutterApi *_pushRegistryDelegateFlutterApi;
//  PKPushRegistry *_pushRegistry;
//  WTPDelegateFlutterApi *_delegateFlutterApi;
//  CXProvider *_provider;
//  CXCallController *_callController;
//  BOOL _driveIdleTimerDisabled;
//}
//
//+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
//  WebtritCallkeepIosPlugin *instance = [[WebtritCallkeepIosPlugin alloc] initWithRegistrar:registrar];
//  [instance restoreSetUp];
//  [registrar addApplicationDelegate:instance];
//  [registrar publish:instance];
//}
//
//- (instancetype)initWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][initWithRegistrar:]");
//#endif
//  self = [super init];
//  if (self) {
//    _registrar = registrar;
//    NSObject<FlutterBinaryMessenger> *binaryMessenger = [_registrar messenger];
//    _pushRegistryDelegateFlutterApi = [[WTPPushRegistryDelegateFlutterApi alloc] initWithBinaryMessenger:binaryMessenger];
//    WTPPushRegistryHostApiSetup(binaryMessenger, self);
//    _delegateFlutterApi = [[WTPDelegateFlutterApi alloc] initWithBinaryMessenger:binaryMessenger];
//    WTPHostApiSetup(binaryMessenger, self);
//  }
//  return self;
//}
//
//- (void)dealloc {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][dealloc]");
//#endif
//  NSObject<FlutterBinaryMessenger> *binaryMessenger = [_registrar messenger];
//  WTPHostApiSetup(binaryMessenger, nil);
//  WTPPushRegistryHostApiSetup(binaryMessenger, nil);
//}
//
//- (BOOL)isSetUp {
//  if (_provider != nil) {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][isSetUp] YES");
//#endif
//    return YES;
//  } else {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][isSetUp] NO");
//#endif
//    return NO;
//  }
//}
//
//- (void)restoreSetUp {
//  WTPIOSOptions *iosOptions = [self getUserDefaultsIosOptions];
//  if (iosOptions != nil) {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][restoreSetUp] processed");
//#endif
//    _pushRegistry = [[PKPushRegistry alloc] initWithQueue:nil];
//    _pushRegistry.delegate = self;
//    _pushRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
//
//    _provider = [[CXProvider alloc] initWithConfiguration:[iosOptions toCallKitWithRegistrar:_registrar]];
//    [_provider setDelegate:self queue:nil];
//
//    _callController = [[CXCallController alloc] init];
//
//    _driveIdleTimerDisabled = iosOptions.driveIdleTimerDisabled.boolValue;
//  } else {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][restoreSetUp] skipped");
//#endif
//  }
//}
//
//#pragma mark - WTPPushRegistryHostApi
//
//- (nullable NSString *)pushTokenForPushTypeVoIP:(FlutterError **)error {
//  if (_pushRegistry != nil) {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][pushTokenForPushTypeVoIP] processed");
//#endif
//    return [[_pushRegistry pushTokenForType:PKPushTypeVoIP] toHexString];
//  } else {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][pushTokenForPushTypeVoIP] skipped");
//#endif
//    return nil;
//  }
//}
//
//#pragma mark - WTPHostApi
//
//- (nullable NSNumber *)isSetUp:(FlutterError **)error {
//  return [NSNumber numberWithBool:[self isSetUp]];
//}
//
//- (void)setUp:(WTPOptions *)options
//   completion:(void (^)(FlutterError *))completion {
//  WTPIOSOptions *iosOptions = options.ios;
//  if ([self setUserDefaultsIosOptions:iosOptions] == YES) {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][setUp] processed");
//#endif
//    // apply new options
//    if (_pushRegistry == nil) {
//      _pushRegistry = [[PKPushRegistry alloc] initWithQueue:nil];
//      _pushRegistry.delegate = self;
//      _pushRegistry.desiredPushTypes = [NSSet setWithObject:PKPushTypeVoIP];
//    }
//    if (_provider == nil) {
//      _provider = [[CXProvider alloc] initWithConfiguration:[iosOptions toCallKitWithRegistrar:_registrar]];
//      [_provider setDelegate:self queue:nil];
//    } else {
//      _provider.configuration = [iosOptions toCallKitWithRegistrar:_registrar];
//    }
//    if (_callController == nil) {
//      _callController = [[CXCallController alloc] init];
//    }
//    _driveIdleTimerDisabled = iosOptions.driveIdleTimerDisabled.boolValue;
//  } else {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][setUp] skipped");
//#endif
//  }
//  completion(nil);
//}
//
//- (void)tearDown:(void (^)(FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][tearDown]");
//#endif
//  if (_callController != nil) {
//    _callController = nil;
//  }
//  if (_provider != nil) {
//    [_provider invalidate];
//    _provider = nil;
//  }
//  if (_pushRegistry != nil) {
//    _pushRegistry.desiredPushTypes = [NSSet set];
//    _pushRegistry = nil;
//  }
//  [self removeUserDefaultsIosOptions];
//  completion(nil);
//}
//
//- (void)reportNewIncomingCall:(WTPId *)pid
//                       handle:(WTPHandle *)handle
//                  displayName:(NSString *)displayName
//                     hasVideo:(NSNumber *)hasVideo
//                   completion:(void (^)(WTPIncomingCallError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][reportNewIncomingCall] uuidString = %@", pid.uuid);
//#endif
//  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
//  callUpdate.remoteHandle = [handle toCallKit];
//  callUpdate.localizedCallerName = displayName;
//  callUpdate.hasVideo = [hasVideo boolValue];
//  callUpdate.supportsGrouping = NO;
//  callUpdate.supportsUngrouping = NO;
//  callUpdate.supportsHolding = YES;
//  callUpdate.supportsDTMF = YES;
//  [_provider reportNewIncomingCallWithUUID:[[NSUUID alloc] initWithUUIDString: pid.uuid]
//                                    update:callUpdate
//                                completion:^(NSError *error) {
//                                  if (error == nil) {
//                                    [self assignIdleTimerDisabled:callUpdate.hasVideo];
//                                    completion(nil, nil);
//                                  } else if ([error.domain isEqualToString:CXErrorDomainIncomingCall]) {
//                                    completion([WTPIncomingCallError makeWithValue:CXErrorCodeIncomingCallErrorToPigeon((CXErrorCodeIncomingCallError) error.code)], nil);
//                                  } else {
//                                    completion(nil, [FlutterError errorWithCode:error.domain
//                                                                        message:[error description]
//                                                                        details:nil]);
//                                  }
//                                }];
//}
//
//- (void)reportConnectingOutgoingCall:(WTPId *)pid
//                          completion:(void (^)(FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][reportConnectingOutgoingCall] uuidString = %@", pid.uuid);
//#endif
//  [_provider reportOutgoingCallWithUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                startedConnectingAtDate:nil];
//  completion(nil);
//}
//
//- (void)reportConnectedOutgoingCall:(WTPId *)pid
//                         completion:(void (^)(FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][reportConnectedOutgoingCall] uuidString = %@", pid.uuid);
//#endif
//  [_provider reportOutgoingCallWithUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                        connectedAtDate:nil];
//  completion(nil);
//}
//
//- (void)reportUpdateCall:(WTPId *)pid
//                  handle:(nullable WTPHandle *)handle
//             displayName:(nullable NSString *)displayName
//                hasVideo:(nullable NSNumber *)hasVideo
//              completion:(void (^)(FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][reportUpdateCall] uuidString = %@", pid.uuid);
//#endif
//  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
//  if (handle != nil) {
//    callUpdate.remoteHandle = [handle toCallKit];
//  }
//  if (displayName != nil) {
//    callUpdate.localizedCallerName = displayName;
//  }
//  if (hasVideo != nil) {
//    callUpdate.hasVideo = [hasVideo boolValue];
//  }
//  [_provider reportCallWithUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                        updated:callUpdate];
//  [self assignIdleTimerDisabled:callUpdate.hasVideo];
//  completion(nil);
//}
//
//- (void)reportEndCall:(WTPId *)pid
//               reason:(WTPEndCallReason *)reason
//           completion:(void (^)(FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][reportEndCall] uuidString = %@", pid.uuid);
//#endif
//  [_provider reportCallWithUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                    endedAtDate:nil
//                         reason:[reason toCallKit]];
//  [self assignIdleTimerDisabled:NO];
//  completion(nil);
//}
//
//- (void)             startCall:(WTPId *)pid
//                        handle:(WTPHandle *)handle
//displayNameOrContactIdentifier:(NSString *)displayNameOrContactIdentifier
//                         video:(NSNumber *)video
//                    completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][startCall] uuidString = %@", pid.uuid);
//#endif
//  NSUUID *uuid = [[NSUUID alloc] initWithUUIDString:pid.uuid];
//
//  CXStartCallAction *action = [[CXStartCallAction alloc] initWithCallUUID:uuid
//                                                                   handle:[handle toCallKit]];
//  if (displayNameOrContactIdentifier != nil) {
//    action.contactIdentifier = displayNameOrContactIdentifier;
//  }
//  action.video = [video boolValue];
//  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
//
//  [self requestTransaction:transaction completion:^(WTPCallRequestError *pigeonError, FlutterError *flutterError) {
//    if (pigeonError == nil && flutterError == nil) {
//      CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
//      callUpdate.remoteHandle = action.handle;
//      callUpdate.localizedCallerName = action.contactIdentifier;
//      callUpdate.hasVideo = action.video;
//      callUpdate.supportsGrouping = NO;
//      callUpdate.supportsUngrouping = NO;
//      callUpdate.supportsHolding = YES;
//      callUpdate.supportsDTMF = YES;
//      [self->_provider reportCallWithUUID:uuid
//                                  updated:callUpdate];
//
//      completion(nil, nil);
//    } else {
//      completion(pigeonError, flutterError);
//    }
//  }];
//}
//
//- (void)answerCall:(WTPId *)pid
//        completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][answerCall] uuidString = %@", pid.uuid);
//#endif
//  CXAnswerCallAction *action = [[CXAnswerCallAction alloc] initWithCallUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]];
//  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
//
//  [self requestTransaction:transaction completion:completion];
//}
//
//- (void)endCall:(WTPId *)pid
//     completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][endCall] uuidString = %@", pid.uuid);
//#endif
//  CXEndCallAction *action = [[CXEndCallAction alloc] initWithCallUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]];
//  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
//
//  [self requestTransaction:transaction completion:completion];
//}
//
//- (void)setHeld:(WTPId *)pid
//         onHold:(NSNumber *)onHold
//     completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][setHeld] uuidString = %@ held = %@", pid.uuid, onHold);
//#endif
//  CXSetHeldCallAction *action = [[CXSetHeldCallAction alloc] initWithCallUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                                                                       onHold:onHold.boolValue];
//  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
//
//  [self requestTransaction:transaction completion:completion];
//}
//
//- (void)setMuted:(WTPId *)pid
//           muted:(NSNumber *)muted
//      completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][setMuted] uuidString = %@ muted = %@", pid.uuid, muted);
//#endif
//  CXSetMutedCallAction *action = [[CXSetMutedCallAction alloc] initWithCallUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                                                                          muted:muted.boolValue];
//  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
//
//  [self requestTransaction:transaction completion:completion];
//}
//
//- (void)setSpeaker:(WTPId *)pid
//           enabled:(NSNumber *)enabled
//      completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][setSpeaker] uuidString = %@ enabled = %@", pid.uuid, enabled);
//#endif
//}
//
//- (void)sendDTMF:(WTPId *)pid
//             key:(NSString *)key
//      completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][sendDTMF] uuidString = %@ key = %@", pid.uuid, key);
//#endif
//  CXPlayDTMFCallAction *action = [[CXPlayDTMFCallAction alloc] initWithCallUUID:[[NSUUID alloc] initWithUUIDString:pid.uuid]
//                                                                         digits:key
//                                                                           type:CXPlayDTMFCallActionTypeSingleTone];
//  CXTransaction *transaction = [[CXTransaction alloc] initWithAction:action];
//
//  [self requestTransaction:transaction completion:completion];
//}
//
//#pragma mark - WTPHostApi - helpers
//
//- (void)requestTransaction:(CXTransaction *)transaction completion:(void (^)(WTPCallRequestError *, FlutterError *))completion {
//  [_callController requestTransaction:transaction completion:^(NSError *error) {
//    if (error == nil) {
//      completion(nil, nil);
//    } else if ([error.domain isEqualToString:CXErrorDomainRequestTransaction]) {
//      completion([WTPCallRequestError makeWithValue:CXErrorCodeRequestTransactionErrorToPigeon((CXErrorCodeRequestTransactionError) error.code)], nil);
//    } else {
//      completion(nil, [FlutterError errorWithCode:error.domain
//                                          message:[error description]
//                                          details:nil]);
//    }
//  }];
//}
//
//#pragma mark - FlutterApplicationLifeCycleDelegate
//
//- (BOOL) application:(nonnull UIApplication *)application
//continueUserActivity:(nonnull NSUserActivity *)userActivity
//  restorationHandler:(nonnull void (^)(NSArray *_Nonnull))restorationHandler {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][FlutterApplicationLifeCycleDelegate][application:continueUserActivity:restorationHandler:]");
//#endif
//  INInteraction *interaction = userActivity.interaction;
//  if (interaction == nil) {
//    return NO;
//  }
//  INIntent *intent = interaction.intent;
//
//  INPerson *person;
//  BOOL isVideoCall = NO;
//
//  if ([intent isKindOfClass:[INStartAudioCallIntent class]]) {
//    INStartAudioCallIntent *startAudioCallIntent = (INStartAudioCallIntent *) intent;
//    person = [startAudioCallIntent.contacts firstObject];
//  } else if ([intent isKindOfClass:[INStartVideoCallIntent class]]) {
//    INStartVideoCallIntent *startVideoCallIntent = (INStartVideoCallIntent *) intent;
//    person = [startVideoCallIntent.contacts firstObject];
//    isVideoCall = YES;
//  } else if (@available(iOS 13, *)) {
//    if ([intent isKindOfClass:[INStartCallIntent class]]) {
//      INStartCallIntent *startCallIntent = (INStartCallIntent *) intent;
//      person = [startCallIntent.contacts firstObject];
//      isVideoCall = startCallIntent.callCapability == INCallCapabilityVideoCall;
//    }
//  }
//
//  if (person != nil && person.personHandle != nil) {
//    [_delegateFlutterApi continueStartCallIntentHandle:[person.personHandle toPigeon]
//                                           displayName:[person displayName]
//                                                 video:[NSNumber numberWithBool:isVideoCall]
//                                            completion:^(NSError *error) {}];
//
//    return YES;
//  } else {
//    return NO;
//  }
//}
//
//#pragma mark - PKPushRegistryDelegate
//
//- (void)pushRegistry:(PKPushRegistry *)registry didUpdatePushCredentials:(PKPushCredentials *)pushCredentials forType:(PKPushType)type {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didUpdatePushCredentials:forType:] pushCredentials = %@ type = %@", pushCredentials, type);
//#endif
//  if (type == PKPushTypeVoIP) {
//    [_pushRegistryDelegateFlutterApi didUpdatePushTokenForPushTypeVoIP:[pushCredentials.token toHexString]
//                                                            completion:^(NSError *error) {}];
//  }
//}
//
//- (void)pushRegistry:(PKPushRegistry *)registry didInvalidatePushTokenForType:(PKPushType)type {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didInvalidatePushTokenForType:] type = %@", type);
//#endif
//  if (type == PKPushTypeVoIP) {
//    [_pushRegistryDelegateFlutterApi didUpdatePushTokenForPushTypeVoIP:nil completion:^(NSError *error) {}];
//  }
//}
//
//- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type withCompletionHandler:(void (^)(void))completion {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didReceiveIncomingPushWithPayload:forType:withCompletionHandler:] type = %@", type);
//#endif
//  [self didReceiveIncomingPushWithPayloadForPushTypeVoIP:payload withCompletionHandler:completion];
//}
//
//- (void)pushRegistry:(PKPushRegistry *)registry didReceiveIncomingPushWithPayload:(PKPushPayload *)payload forType:(PKPushType)type {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][PKPushRegistryDelegate][pushRegistry:didReceiveIncomingPushWithPayload:forType:] type = %@", type);
//#endif
//  [self didReceiveIncomingPushWithPayloadForPushTypeVoIP:payload withCompletionHandler:^() {}];
//}
//
//- (void)didReceiveIncomingPushWithPayloadForPushTypeVoIP:(PKPushPayload *)payload withCompletionHandler:(void (^)(void))completion {
//  NSDictionary *dictionaryPayload = payload.dictionaryPayload;
//#ifdef DEBUG
//  NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:] payload = %@", dictionaryPayload);
//#endif
//  NSString *handleType = dictionaryPayload[@"handleType"];
//  NSString *handleValue = dictionaryPayload[@"handleValue"];
//  NSString *displayName = dictionaryPayload[@"displayName"];
//  NSNumber *hasVideo = dictionaryPayload[@"hasVideo"];
//  NSString *callId = dictionaryPayload[@"callId"];
//
//  if (handleType == nil || handleValue == nil || callId == nil) {
//#ifdef DEBUG
//    NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:] payload wrong format");
//#endif
//    completion();
//    return;
//  }
//
//  // It is crucial to use UUID version 5 (namespace name-based) based on callId to get the call UUID for reportNewIncomingCallWithUUID.
//  // Such UUID allows overcoming possible races between VoIP push and relevant signaling events.
//  NSUUID *uuid = [NSUUID makeWithName:callId namespace:[[NSUUID alloc] initWithUUIDString:NAMESPACE_OID]];
//
//  CXCallUpdate *callUpdate = [[CXCallUpdate alloc] init];
//  callUpdate.remoteHandle = [[CXHandle alloc] initWithType:CXHandleTypeFromString(handleType)
//                                                     value:handleValue];
//  callUpdate.localizedCallerName = displayName;
//  if (hasVideo != nil) {
//    callUpdate.hasVideo = [hasVideo boolValue];
//  } else {
//    callUpdate.hasVideo = NO;
//  }
//  callUpdate.supportsGrouping = NO;
//  callUpdate.supportsUngrouping = NO;
//  callUpdate.supportsHolding = YES;
//  callUpdate.supportsDTMF = YES;
//  [_provider reportNewIncomingCallWithUUID:uuid
//                                    update:callUpdate
//                                completion:^(NSError *error) {
//                                  WTPIncomingCallError *incomingCallError = nil;
//                                  if (error != nil) {
//                                    if ([error.domain isEqualToString:CXErrorDomainIncomingCall]) {
//                                      incomingCallError = [WTPIncomingCallError makeWithValue:CXErrorCodeIncomingCallErrorToPigeon((CXErrorCodeIncomingCallError) error.code)];
//                                    } else {
//                                      NSLog(@"[Callkeep][didReceiveIncomingPushWithPayloadForPushTypeVoIP:withCompletionHandler:][reportNewIncomingCallWithUUID] error = %@", error);
//                                      incomingCallError = [WTPIncomingCallError makeWithValue:WTPIncomingCallErrorEnumInternal];
//                                    }
//                                  }
//
//                                  [self->_delegateFlutterApi didPushIncomingCallHandle:[callUpdate.remoteHandle toPigeon]
//                                                                           displayName:callUpdate.localizedCallerName
//                                                                                 video:[NSNumber numberWithBool:callUpdate.hasVideo]
//                                                                                callId:callId
//                                                                                  uuid:[uuid UUIDString]
//                                                                                 error:incomingCallError
//                                                                            completion:^(NSError *error) {
//                                                                              [self assignIdleTimerDisabled:callUpdate.hasVideo];
//                                                                              completion();
//                                                                            }];
//                                }];
//}
//
//#pragma mark - CXProviderDelegate
//
//- (void)providerDidReset:(CXProvider *)provider {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][providerDidReset:]");
//#endif
//  [_delegateFlutterApi didReset:^(NSError *error) {}];
//}
//
//- (void)provider:(CXProvider *)provider performStartCallAction:(CXStartCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performStartCallAction:]");
//#endif
//  [_delegateFlutterApi performStartCall:action.callUUID.UUIDString
//                                 handle:[action.handle toPigeon]
//         displayNameOrContactIdentifier:action.contactIdentifier
//                                  video:[NSNumber numberWithBool:action.video]
//                             completion:^(NSNumber *fulfill, NSError *error) {
//                               if (error != nil || [fulfill boolValue] != YES) {
//                                 [action fail];
//                               } else {
//                                 [action fulfill];
//                                 [self assignIdleTimerDisabled:action.video];
//                               }
//                             }];
//}
//
//- (void)provider:(CXProvider *)provider performAnswerCallAction:(CXAnswerCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performAnswerCallAction:]");
//#endif
//  [_delegateFlutterApi performAnswerCall:action.callUUID.UUIDString
//                              completion:^(NSNumber *fulfill, NSError *error) {
//                                if (error != nil || [fulfill boolValue] != YES) {
//                                  [action fail];
//                                } else {
//                                  [action fulfill];
//                                }
//                              }];
//}
//
//- (void)provider:(CXProvider *)provider performEndCallAction:(CXEndCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performEndCallAction:]");
//#endif
//  [_delegateFlutterApi performEndCall:action.callUUID.UUIDString
//                           completion:^(NSNumber *fulfill, NSError *error) {
//                             if (error != nil || [fulfill boolValue] != YES) {
//                               [action fail];
//                             } else {
//                               [action fulfill];
//                               [self assignIdleTimerDisabled:NO];
//                             }
//                           }];
//}
//
//- (void)provider:(CXProvider *)provider performSetHeldCallAction:(CXSetHeldCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performSetHeldCallAction:]");
//#endif
//  [_delegateFlutterApi performSetHeld:action.callUUID.UUIDString
//                               onHold:[NSNumber numberWithBool:action.onHold]
//                           completion:^(NSNumber *fulfill, NSError *error) {
//                             if (error != nil || [fulfill boolValue] != YES) {
//                               [action fail];
//                             } else {
//                               [action fulfill];
//                             }
//                           }];
//}
//
//- (void)provider:(CXProvider *)provider performSetMutedCallAction:(CXSetMutedCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performSetMutedCallAction:]");
//#endif
//  [_delegateFlutterApi performSetMuted:action.callUUID.UUIDString
//                                 muted:[NSNumber numberWithBool:action.muted]
//                            completion:^(NSNumber *fulfill, NSError *error) {
//                              if (error != nil || [fulfill boolValue] != YES) {
//                                [action fail];
//                              } else {
//                                [action fulfill];
//                              }
//                            }];
//}
//
//- (void)provider:(CXProvider *)provider performSetGroupCallAction:(CXSetGroupCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performSetGroupCallAction:] - not implemented");
//#endif
//  [action fail];
//}
//
//- (void)provider:(CXProvider *)provider performPlayDTMFCallAction:(CXPlayDTMFCallAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:performPlayDTMFCallAction:]");
//#endif
//  if (action.type != CXPlayDTMFCallActionTypeSingleTone) {
//    [action fail];
//    return;
//  }
//  [_delegateFlutterApi performSendDTMF:action.callUUID.UUIDString
//                                   key:action.digits
//                            completion:^(NSNumber *fulfill, NSError *error) {
//                              if (error != nil || [fulfill boolValue] != YES) {
//                                [action fail];
//                              } else {
//                                [action fulfill];
//                              }
//                            }];
//}
//
//- (void)provider:(CXProvider *)provider timedOutPerformingAction:(CXAction *)action {
//#ifdef DEBUG
//  NSLog(@"[Callkeep][CXProviderDelegate][provider:timedOutPerformingAction:] action = %@", action);
//#endif
//}
//
//- (void)provider:(CXProvider *)provider didActivateAudioSession:(AVAudioSession *)audioSession {
//#ifdef DEBUG
//  NSLog(@"[CallKeep][CXProviderDelegate][provider:didActivateAudioSession:]");
//#endif
//  [_delegateFlutterApi didActivateAudioSession:^(NSError *error) {}];
//}
//
//- (void)provider:(CXProvider *)provider didDeactivateAudioSession:(AVAudioSession *)audioSession {
//#ifdef DEBUG
//  NSLog(@"[CallKeep][CXProviderDelegate][provider:didDeactivateAudioSession:]");
//#endif
//  [_delegateFlutterApi didDeactivateAudioSession:^(NSError *error) {}];
//}
//
//#pragma mark - helpers
//
//- (WTPIOSOptions *)getUserDefaultsIosOptions {
//  NSData *data = [[NSUserDefaults standardUserDefaults] objectForKey:OptionsKey];
//  if (data != nil) {
//    NSError *error;
//    NSDictionary *iosOptionsMap = [NSJSONSerialization JSONObjectWithData:data options:kNilOptions error:&error];
//    if (iosOptionsMap != nil) {
//      // Currently is necessary to overcome possible inconsistence with the options dictionary because of add/remove/rename properties of WTPIOSOptions class.
//      // This logic could be refactored when the following limitation is eliminated - Initialization isn't supported for fields in Pigeon data classes.
//      NSDictionary *iosOptionsMapDefault = @{
//        @"driveIdleTimerDisabled": @YES,
//      };
//      NSMutableDictionary *iosOptionsMapMerged = [[NSMutableDictionary alloc] init];
//      [iosOptionsMapMerged addEntriesFromDictionary: iosOptionsMapDefault];
//      [iosOptionsMapMerged addEntriesFromDictionary: iosOptionsMap];
//      return [WTPIOSOptions fromMap:iosOptionsMapMerged];
//    }
//  }
//  return nil;
//}
//
//- (BOOL)setUserDefaultsIosOptions:(WTPIOSOptions *)iosOptions {
//  NSDictionary *iosOptionsMap = [iosOptions toMap];
//  NSError *error;
//  NSData *data = [NSJSONSerialization dataWithJSONObject:iosOptionsMap options:kNilOptions error:&error];
//  NSData *currentData = [[NSUserDefaults standardUserDefaults] objectForKey:OptionsKey];
//  if (currentData == nil || [data isEqualToData:currentData] != YES) {
//    [[NSUserDefaults standardUserDefaults] setObject:data forKey:OptionsKey];
//    return YES;
//  } else {
//    return NO;
//  }
//}
//
//- (void)removeUserDefaultsIosOptions {
//  [[NSUserDefaults standardUserDefaults] removeObjectForKey:OptionsKey];
//}
//
//- (void)assignIdleTimerDisabled:(BOOL)value {
//  if (_driveIdleTimerDisabled) {
//    [UIApplication sharedApplication].idleTimerDisabled = value;
//  }
//}
//
//@end
