# Callkeep Exception Report — 2026-03-25

## Analyzed Files

| File | Lines | Device |
|------|-------|--------|
| `bugggggg_logcat_47160DLAQ00937__20260325_142008.txt` | 913 | 47160DLAQ00937 |
| `bugggggg_logcat_9b010059305331323800d2d22f578b__20260325_142008.txt` | 4268 | 9b010059305331323800d2d22f578b |

---

## Summary

| # | Exception | Severity | Count | Component | Handled? |
|---|-----------|----------|-------|-----------|---------|
| 1 | IncomingCallService stop timeout | HIGH | 1 | CK-IncomingCallService | Forced stop |
| 2 | WebRTC setRemoteDescription — glare | MEDIUM | 1 | WebRTC / CallBloc | Yes, rollback + retry |
| 3 | WebRTC setLocalDescription — wrong state | MEDIUM | 1 | RenegotiationHandler | Yes, libwebrtc retries |
| 4 | SIP 448 — SDP answer incompatible | MEDIUM | 1 | WebtritSignalingClient | Yes, rollback + re-renegotiate |
| 5 | HTTP 404 — `call-to-actions` not found | LOW | 1 | WebtritApiClient | Logged only |
| 6 | Unhandled signaling events | LOW | 10+ | CallBloc | No crash |

---

## Detailed Findings

### 1. IncomingCallService Stop Timeout

- **Severity**: HIGH
- **Timestamp**: 2026-03-25 14:20:27.611
- **Process**: main (PID 31566)
- **File**: `bugggggg_logcat_9b010059305331323800d2d22f578b`

**Log lines:**
```
I flutter : ----------------FIREBASE CRASHLYTICS----------------
I flutter : Exception: [CK-IncomingCallService] Service stop timeout (2000 ms) reached. Stopping forcefully.
I flutter : null
I flutter : ----------------------------------------------------
I flutter : WARNING CallkeepLogs - [CK-IncomingCallService] Service stop timeout (2000 ms) reached. Stopping forcefully.
```

**Description:**
`IncomingCallService` did not complete shutdown within the 2-second timeout window and was forcefully stopped. This exception is reported to Firebase Crashlytics. A force-stop during active call handling can interrupt cleanup: the Telecom connection may not be released cleanly, pending Pigeon callbacks may not fire, and in-flight call state in `MainProcessConnectionTracker` may be left stale.

**Root cause area:**
Service `onStop()` / `onDestroy()` handler in `IncomingCallService` is taking more than 2000 ms. Likely candidates: waiting for a background isolate to shut down, waiting on a Pigeon response, or blocking I/O.

**Recommended action:**
Review `IncomingCallService` shutdown path. Ensure isolate teardown and any async operations are given their own shorter deadline before the service timeout fires, or reduce blocking work in the stop path.

---

### 2. WebRTC setRemoteDescription — Glare Condition

- **Severity**: MEDIUM
- **Timestamp**: 2026-03-25 14:20:32.609
- **Process**: main (PID 31566)
- **Component**: CallBloc / WebRTC
- **File**: `bugggggg_logcat_9b010059305331323800d2d22f578b`

**Log line:**
```
WARNING CallBloc - __onCallSignalingEventUpdating: glare detected via catch
(Unable to RTCPeerConnection::setRemoteDescription:
 WEBRTC_SET_REMOTE_DESCRIPTION_ERROR: Failed to set remote offer sdp:
 Called in wrong state: have-local-offer),
rolling back local offer and retrying
```

**Description:**
Both endpoints sent SDP offers simultaneously ("glare"). The local state was `have-local-offer` when the remote offer arrived, causing `setRemoteDescription` to fail. The app correctly detected this, rolled back the local offer, and retried.

**Status**: Handled gracefully. No action required unless frequency increases significantly in production.

---

### 3. WebRTC setLocalDescription — Wrong State During Renegotiation

- **Severity**: MEDIUM
- **Timestamp**: 2026-03-25 14:20:33.500
- **Process**: main (PID 31566)
- **Component**: RenegotiationHandler / WebRTC
- **File**: `bugggggg_logcat_9b010059305331323800d2d22f578b`

**Log line:**
```
WARNING RenegotiationHandler - onRenegotiationNeeded: setLocalDescription failed in wrong state
(Unable to RTCPeerConnection::setLocalDescription:
 WEBRTC_SET_LOCAL_DESCRIPTION_ERROR: Failed to set local offer sdp:
 Called in wrong state: have-remote-offer)
— libwebrtc will re-fire onRenegotiationNeeded when stable
```

**Description:**
A renegotiation attempt (likely video enablement or codec change mid-call) failed because a pending remote offer was already present. libWebRTC will re-fire `onRenegotiationNeeded` once the connection reaches stable state, so the renegotiation will proceed automatically.

**Status**: Handled gracefully. Directly follows from the glare condition above (exceptions 2 and 3 are a cascade).

---

### 4. SIP Error 448 — SDP Answer Incompatible with Session Status

- **Severity**: MEDIUM
- **Timestamp**: 2026-03-25 14:20:34.484
- **Process**: main (PID 31566)
- **Component**: WebtritSignalingClient / CallBloc
- **Call ID**: `9fRE6wJ4TUxVFYse9xsLaGvT`
- **File**: `bugggggg_logcat_9b010059305331323800d2d22f578b`

**Log lines:**
```
FINE WebtritSignalingClient-0 - _wsOnData:
  {"code":448,"line":0,
   "reason":"[SIP-111000333] SDP type answer is incompatible with session status incall",
   "transaction":"transaction-9","event":"error","call_id":"9fRE6wJ4TUxVFYse9xsLaGvT"}

WARNING CallBloc - __onCallSignalingEventCallError:
  448 for callId=9fRE6wJ4TUxVFYse9xsLaGvT
  signalingState=RTCSignalingState.RTCSignalingStateStable
  — rolling back if needed and re-triggering renegotiation
```

**Description:**
The SIP server rejected an SDP answer mid-call with error 448: the server's session state (`incall`) did not expect a new SDP answer at that point. This is a server-side validation failure caused by the timing of the renegotiation following the glare condition (exceptions 2-4 form a single cascade). The app rolled back and re-triggered renegotiation.

**Status**: Handled by CallBloc. Cascade root cause is the glare condition in exception 2.

---

### 5. HTTP 404 — `call-to-actions` Endpoint Not Found

- **Severity**: LOW
- **Timestamp**: 2026-03-25 14:20:21.357
- **Process**: main (PID 20753)
- **Component**: WebtritApiClient
- **File**: `bugggggg_logcat_47160DLAQ00937`

**Log line:**
```
SEVERE WebtritApiClient - POST failed for requestId: null with error:
  RequestFailure(statusCode: 404, requestId: meawbmvvrpupdnmdkhhasiykhulihwdn)
```

**Description:**
An HTTP POST to the `call-to-actions` API method returned 404. This is a backend/integration issue — the endpoint is either not deployed on the tested server or the path is incorrect. This is not a callkeep library defect.

**Status**: Not a callkeep bug. Needs investigation on the server/API side.

---

### 6. Unhandled Signaling Events in CallBloc

- **Severity**: LOW
- **Count**: 10+ occurrences across both files
- **Component**: CallBloc

**Examples of unhandled event types:**
- `IceWebrtcUpEvent` — WebRTC ICE connection established
- `IceMediaEvent` — ICE media event
- `CallingEvent` — SIP call in progress
- `ProceedingEvent` — SIP provisional response
- `UpdatingEvent` — call update event

**Description:**
CallBloc logs warnings for signaling events it does not have an explicit handler for. No exceptions are thrown and no call state is corrupted, but unhandled `UpdatingEvent` in particular may be relevant: if the app does not drive renegotiation in response to `UpdatingEvent`, it relies solely on WebRTC's own re-fire mechanism, which contributed to the cascade above.

**Recommended action:**
Audit which of these events should have explicit handling in CallBloc, especially `UpdatingEvent`.

---

## Exception Cascade — Root Cause Chain

Exceptions 2, 3, and 4 are a single cascading failure, not three independent issues:

```
Glare condition (both sides sent offer simultaneously)
    |
    v
WebRTC: setRemoteDescription failed (have-local-offer)  [Exception 2]
    |
    v
App rolls back local offer, re-triggers renegotiation
    |
    v
WebRTC: setLocalDescription failed (have-remote-offer)  [Exception 3]
    |
    v
libWebRTC re-fires onRenegotiationNeeded, renegotiation retried
    |
    v
SIP server rejects SDP answer with 448 (session already incall)  [Exception 4]
    |
    v
App rolls back and re-triggers renegotiation again (recovery)
```

All three are handled without dropping the call. The glare handling and renegotiation retry logic in the app is correct.

---

## Android Native Layer

No Java/Kotlin stack traces were found in either log file. No crashes, ANRs, or errors from:
- `PhoneConnectionService` / `:callkeep_core` process
- Android Telecom framework
- `ForegroundService` Pigeon host
- `MainProcessConnectionTracker`

The Android native callkeep layer appears stable for both tested devices.

---

## Action Items

| Priority | Action | Owner |
|----------|--------|-------|
| HIGH | Investigate `IncomingCallService` shutdown path — reduce or eliminate blocking work in `onStop`/`onDestroy` to stay within the 2 s timeout | Android / callkeep |
| MEDIUM | Audit `CallBloc` for unhandled `UpdatingEvent` — determine whether explicit handling would prevent or shorten the glare cascade | App / Flutter |
| LOW | Confirm `call-to-actions` endpoint availability on test/production server | Backend |
| LOW | Monitor glare frequency in production Crashlytics — if it exceeds ~1% of calls, review offer timing in the signaling client | App / Flutter |
