enum CallkeepCallRequestError {
  unknown,
  unentitled,
  unknownCallUuid,
  callUuidAlreadyExists,
  maximumCallGroupsReached,
  internal,
  emergencyNumber,

  /// Android only.
  ///
  /// Triggered when the phone is not registered as a self-managed
  /// [PhoneAccount]. As a result, the `ConnectionService` cannot create
  /// a connection, and the system throws an exception such as
  /// `CALL_PHONE permission required to place calls`, because it attempts
  /// to use the GSM dialer instead of VoIP.
  selfManagedPhoneAccountNotRegistered,
}
