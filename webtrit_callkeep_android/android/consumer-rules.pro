# Keep rules for webtrit_callkeep_android
#
# These classes are referenced by name at runtime via explicit Intents
# (startService / sendBroadcast with ComponentName) for cross-process IPC.
# R8 must not rename or remove them, even after the :callkeep_core process split
# when PhoneConnectionService runs in a separate JVM heap.

-keep class com.webtrit.callkeep.services.services.connection.PhoneConnectionService { *; }
-keep class com.webtrit.callkeep.services.services.connection.ConnectionManager { *; }
-keep class com.webtrit.callkeep.services.services.connection.PhoneConnection { *; }
