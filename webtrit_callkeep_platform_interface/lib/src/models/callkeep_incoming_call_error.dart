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
  /// Telecom rejected the incoming call registration — e.g. the device does
  /// not support concurrent self-managed calls (common on Huawei and other OEM
  /// devices). The call was never confirmed to the app so no `performEndCall`
  /// will be fired.
  callRejectedBySystem,
}
