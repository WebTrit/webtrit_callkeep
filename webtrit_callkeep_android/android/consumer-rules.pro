# Keep rules for webtrit_callkeep_android
#
# PhoneConnectionService is declared in the manifest (Android build tools protect it
# automatically), but we also ship it as a library AAR where the consuming app's R8
# pass runs after manifest merging. -keepnames ensures the class name is never
# rewritten, which is required for explicit ComponentName-based startService() calls
# used for cross-process IPC after the android:process=":callkeep_core" split.

-keepnames class com.webtrit.callkeep.services.services.connection.PhoneConnectionService
