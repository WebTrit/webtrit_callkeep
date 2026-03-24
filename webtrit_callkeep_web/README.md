# webtrit_callkeep_web

Web implementation of [`webtrit_callkeep`](../webtrit_callkeep/README.md).

Manages call state in memory and forwards all events to the registered
`CallkeepDelegate`. There is no native call UI on the web — incoming and
outgoing call screens are rendered entirely by the Flutter application.

## What is implemented

| Feature | Status |
| --- | --- |
| `setUp` / `tearDown` | Supported |
| `reportNewIncomingCall` | Supported (in-memory, delegate notified) |
| `startCall` / `answerCall` / `endCall` | Supported (delegate notified) |
| `setHeld` / `setMuted` / `sendDTMF` | Supported (delegate notified) |
| `setAudioDevice` | Supported (delegate notified) |
| `getConnection` / `getConnections` / `cleanConnections` | Supported |
| `getDiagnosticReport` | Returns basic web diagnostics |

## What is no-op

| Feature | Notes |
| --- | --- |
| Android background/push notification service | Android-only |
| Android SMS reception | Android-only |
| Android activity control (lock screen, wake) | Android-only |
| iOS PushKit / push registry | iOS-only |
| Native permissions (phone state, etc.) | Returns `granted` for all |
| Speaker / ringback sound | Handled by the WebRTC layer |

---

## Related packages

| Package | Description |
| --- | --- |
| [`webtrit_callkeep`](../webtrit_callkeep/README.md) | Public API aggregator |
| [`webtrit_callkeep_android`](../webtrit_callkeep_android/README.md) | Android implementation |
| [`webtrit_callkeep_ios`](../webtrit_callkeep_ios/README.md) | iOS implementation |
