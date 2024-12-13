import 'package:equatable/equatable.dart';

enum CallkeepConnectionState {
  stateInitializing, // The connection is initializing.
  stateNew, // The connection is new and not connected.
  stateRinging, // An incoming connection is in the ringing state. During this state, the user's ringer or vibration feature will be activated
  stateDialing, // An outgoing connection is in the dialing state. In this state the other party has not yet answered the call and the user traditionally hears a ringback tone.
  stateActive, // A connection is active. Both parties are connected to the call and can actively communicate.
  stateHolding, // A connection is on hold.
  stateDisconnected, // A connection has been disconnected. This is the final state once the user has been disconnected from a call either locally, remotely or by an error in the service
  statePullingCall; // The state of an external connection which is in the process of being pulled from a remote device to the local device.
}

enum CallkeepDisconnectCauseType {
  unknown, // Disconnected for an unknown reason.
  error, // Disconnected due to an error, such as a network problem.
  local, // Disconnected because of a local user-initiated action.
  remote, // Disconnected because the remote party hung up or did not answer.
  canceled, //  Disconnected because the call was canceled.
  missed, // Disconnected due to no response to an incoming call.
  rejected, // Disconnected because the user rejected the call.
  busy, // Disconnected because the other party was busy.
  restricted, // Disconnected due to restrictions like airplane mode.
  other, // Disconnected for a reason not described by other codes.
  connectionManagerNotSupported, // Call not supported by the connection manager.
  answeredElsewhere, // Call was answered on another device.
  callPulled, // Call was pulled to another device.
}

class CallkeepConnection extends Equatable {
  const CallkeepConnection({
    required this.callId,
    required this.state,
    required this.disconnectCause,
  });

  final String callId;
  final CallkeepConnectionState state;
  final CallkeepDisconnectCause? disconnectCause;

  @override
  List<Object?> get props => [state];

  @override
  String toString() {
    return 'CallkeepConnection(callId: $callId, state: $state, disconnectCause: $disconnectCause)';
  }
}

class CallkeepDisconnectCause extends Equatable {
  const CallkeepDisconnectCause({
    required this.type,
    required this.reason,
  });

  final CallkeepDisconnectCauseType type;
  final String? reason;

  @override
  List<Object?> get props => [type, reason];

  @override
  String toString() {
    return 'CallkeepDisconnectCause(type: $type, reason: ${reason ?? "N/A"})';
  }
}
