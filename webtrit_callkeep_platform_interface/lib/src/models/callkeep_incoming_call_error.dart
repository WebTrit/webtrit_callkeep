enum CallkeepIncomingCallError {
  unknown,
  unentitled,
  callIdAlreadyExists,
  callIdAlreadyExistsAndAnswered,
  callIdAlreadyTerminated,
  filteredByDoNotDisturb,
  filteredByBlockList,
  internal,

  /// Android only.
  ///
  /// Telecom rejected the incoming call registration via
  /// `onCreateIncomingConnectionFailed` (without calling
  /// `onCreateIncomingConnection` first).
  ///
  /// **When this happens**: Android does not allow two self-managed calls to be
  /// simultaneously in RINGING state. If a call is already ringing, Telecom
  /// rejects every subsequent incoming self-managed call. This is standard
  /// AOSP behaviour (reproducible on stock Pixel devices running Android 11+),
  /// not limited to OEM devices. Some vendors (Huawei, certain MediaTek OEMs)
  /// apply the same rejection even when the first call is already ACTIVE.
  ///
  /// **Consequences for the app**:
  /// - The call was never presented to the user, so `performEndCall` will NOT
  ///   fire for this call ID.
  /// - The app must notify the server (e.g. send a SIP BYE) immediately upon
  ///   receiving this error, without waiting for `performEndCall`.
  callRejectedBySystem,
}
