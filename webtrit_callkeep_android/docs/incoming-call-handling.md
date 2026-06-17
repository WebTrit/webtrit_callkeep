# Incoming call handling (decision + outcomes)

How callkeep decides who runs the background work for an incoming call, and the terminal outcomes
it drives. callkeep is transport-agnostic: it does not know whether a call arrives via push, a
persistent socket, or in-app signaling. The integrator reports the call; callkeep presents it via
Android Telecom and routes call control.

Related: step-by-step flows in [call-flows.md](call-flows.md); services in
[background-services.md](background-services.md) and [foreground-service.md](foreground-service.md);
hosting callkeep on an app-owned engine in
[../../docs/external-flutter-engines.md](../../docs/external-flutter-engines.md).

Color: blue = callkeep, orange = host app, grey = external (Telecom / system UI), yellow = decision.

## Who owns the background work

After a call is reported and `IncomingCallService` starts, `IncomingCallHandler.maybeInitBackgroundHandling`
decides whether callkeep spawns its own background isolate:

```mermaid
flowchart TB
  classDef ck fill:#dae8fc,stroke:#6c8ebf,color:#000
  classDef app fill:#ffe6cc,stroke:#d79b00,color:#000
  classDef ext fill:#f5f5f5,stroke:#999999,color:#000
  classDef dec fill:#fff2cc,stroke:#d6b656,color:#000

  IN["reportNewIncomingCall -> CallkeepCore.startIncomingCall<br/>-> PhoneConnectionService (Telecom) -> IncomingCallService.start (ringing UI)"]:::ck
  GATE{"IncomingCallHandler.maybeInitBackgroundHandling"}:::dec
  IN --> GATE
  GATE -- "hosted on an external engine<br/>(WebtritCallkeep.attachToEngine)" --> HOST["the host engine owns the background work<br/>callkeep does NOT spawn an isolate"]:::app
  GATE -- "app process active<br/>(ON_RESUME / ON_PAUSE / ON_STOP)" --> MAIN["the main app handles the call<br/>callkeep does NOT spawn an isolate"]:::app
  GATE -- "else: app process dead<br/>(state null / ON_DESTROY)" --> OWN["callkeep spawns its OWN background isolate<br/>(IncomingCallService, automaticallyRegisterPlugins = true)"]:::ck
```

## Terminal outcomes

The owner that reported the call drives the outcome; callkeep mediates through Telecom and
`IncomingCallService`. The owner is the callkeep isolate, the host engine, or the main app
(see the decision above).

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant SYS as Android System UI
    participant PCS as PhoneConnectionService (Telecom)
    participant CORE as CallkeepCore
    participant ICS as IncomingCallService
    participant OWNER as Background owner (isolate / host engine / main app)
    participant ACT as Activity + ForegroundService

    SYS-->>User: ringing
    alt User answers
        User->>SYS: Answer
        SYS->>PCS: onAnswer
        PCS->>OWNER: performAnswerCall / markAnswered
        Note over ACT: Activity adopts the connection, the app completes the answer (200 OK)
    else User declines
        User->>SYS: Decline
        SYS->>PCS: onReject -> terminateWithCause(REJECTED)
        PCS->>ICS: onDisconnect -> release(IC_RELEASE_WITH_DECLINE)
        ICS->>OWNER: performEndCall (the owner sends the decline)
    else Missed (caller cancels)
        OWNER->>CORE: releaseCall -> terminate connection
        PCS->>SYS: dismiss incoming UI
    end
```
