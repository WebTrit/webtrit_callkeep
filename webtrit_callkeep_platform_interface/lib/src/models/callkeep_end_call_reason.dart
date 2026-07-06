enum CallkeepEndCallReason {
  failed,
  remoteEnded,
  unanswered,
  answeredElsewhere,
  declinedElsewhere,
  missed,

  /// The call was ended while it was never presented in the Flutter app state - the remote party
  /// hung up an incoming call before it was registered in CallBloc (e.g. a push->foreground handoff
  /// where the caller gives up while signaling is still connecting). Distinct from [remoteEnded]:
  /// it signals that the app never knew about this call, so a later re-presentation of the same
  /// callId is a stale ghost (NOT a transfer-back, which always reuses a call the app did know).
  missedWhileConnecting,
}
