import 'dart:async';

/// A handler for pending calls on Android.
class AndroidPendingCallHandler {
  final StreamController<PendingCall> _streamController = StreamController.broadcast();
  final List<PendingCall> _calls = [];

  /// Adds a new pending call.
  void add(PendingCall call) {
    final hasSameId = _calls.any((existingCall) => existingCall.id == call.id);
    if (!hasSameId) {
      _calls.add(call);
      if (_streamController.hasListener) {
        _streamController.add(call);
      }
    }
  }

  /// Removes a pending call.
  void flush() {
    for (final element in _calls) {
      _streamController.add(element);
    }
  }

  /// Subscribes to pending calls.
  StreamSubscription<PendingCall> subscribe(void Function(PendingCall) call) {
    final subscription = _streamController.stream.map((pendingCall) {
      _calls.remove(pendingCall);
      return pendingCall;
    }).listen(call);

    flush();
    return subscription;
  }
}

/// Represents a pending call on Android.
class PendingCall {
  /// Creates a new instance of [PendingCall].
  PendingCall({
    required this.id,
    required this.handle,
    required this.displayName,
    this.hasVideo = false,
    this.hasMute = false,
    this.hasHold = false,
  });

  /// Creates a new instance of [PendingCall] from a map.
  factory PendingCall.fromMap(Map<String, dynamic> map) {
    return PendingCall(
      id: map['callId'] as String? ?? '',
      displayName: map['displayName'] as String? ?? '',
      handle: map['number'] as String? ?? '',
      hasVideo: map['hasVideo'] == 'true',
      hasMute: map['hasMute'] == 'true',
      hasHold: map['hasHold'] == 'true',
    );
  }

  /// Unique identifier for the call
  final String id;

  /// Display name of the caller
  final String displayName;

  /// Phone number of the caller
  final String handle;

  /// Whether the call has video
  final bool hasVideo;

  /// Whether the call is muted
  final bool hasMute;

  /// Whether the call is on hold
  final bool hasHold;

  @override
  String toString() {
    return 'PendingCall{id: $id, displayName: $displayName, handle: $handle, hasVideo: $hasVideo, hasMute: $hasMute, hasHold: $hasHold}';
  }
}
