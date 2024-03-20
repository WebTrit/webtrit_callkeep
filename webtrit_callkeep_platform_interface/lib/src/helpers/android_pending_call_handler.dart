import 'dart:async';

class AndroidPendingCallHandler {
  final StreamController<PendingCall> _streamController = StreamController.broadcast();
  final List<PendingCall> _calls = [];

  void add(PendingCall call) {
    bool hasSameId = _calls.any((existingCall) => existingCall.id == call.id);
    if (!hasSameId) {
      _calls.add(call);
      if (_streamController.hasListener) {
        _streamController.add(call);
      }
    }
  }

  void flush() {
    for (var element in _calls) {
      _streamController.add(element);
    }
  }

  StreamSubscription<PendingCall> subscribe(Function(PendingCall) call) {
    final subscription = _streamController.stream.map((pendingCall) {
      _calls.remove(pendingCall);
      return pendingCall;
    }).listen(call);

    flush();
    return subscription;
  }
}

class PendingCall {
  PendingCall({
    required this.id,
    required this.handle,
    required this.displayName,
    this.hasVideo = false,
    this.hasMute = false,
    this.hasHold = false,
  });

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

  final String id;
  final String displayName;
  final String handle;
  final bool hasVideo;
  final bool hasMute;
  final bool hasHold;

  @override
  String toString() {
    return 'PendingCall{id: $id, displayName: $displayName, handle: $handle, hasVideo: $hasVideo, hasMute: $hasMute, hasHold: $hasHold}';
  }
}
