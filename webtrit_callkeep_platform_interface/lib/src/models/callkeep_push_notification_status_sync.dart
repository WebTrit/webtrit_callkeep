enum CallkeepPushNotificationSyncStatus {
  synchronizeCallStatus, // Request the server if the call is still active; otherwise, close the notification
  releaseResources, // Close the connection service if the incoming call is no longer relevant
}
