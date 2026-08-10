# iOS deferred CallKit registration: design notes

## Purpose

When two incoming calls ring at the same time on iOS and the user answers one of them, the system
takes over with its full-screen call-waiting prompt ("End & Accept / Decline / Hold & Accept") for
the other call, and the decision has to be made there instead of in the app. In the worst cases that
prompt hangs on a dead call, or iOS parks the user on its own in-call screen and the app cannot get
the foreground back. And when the app is in the background, the system incoming-call UI shows only
ONE of the two ringing calls - the second is invisible system-wide and its ringtone is suppressed.

The goal of this mechanism is that the app owns the whole multi-call experience: the system UI never
takes over, and every ringing call is visible and answerable inside the app.

Implementation: entirely native, in `WebtritCallkeepPlugin.m`. No Dart API changes, no app
involvement - the Flutter side keeps talking to the plugin exactly as before.

## The platform rules this is built on

These are not app bugs; they are CallKit platform behavior, verified on a real device:

1. **A call registered with CallKit belongs to the system.** How it is presented - banner, lock
   screen, full-screen prompt - is decided by iOS. There is no API to style or suppress it.
2. **The registry configuration "connected (or held) + ringing" automatically summons the system
   call-waiting screen.** It is not tied to any event: the prompt appears the instant that
   combination exists among the app's calls and disappears the instant it stops existing. This is
   why the bug fires at the moment of ANSWER - answering call B turns the registry into
   "active + ringing(A)". Two purely ringing calls coexist without conflict.
3. **Every VoIP push MUST be reported to CallKit** (iOS 13+, otherwise the app is terminated), and
   the backend sends a push for every incoming call as a fallback against dead sockets. So "just do
   not register the call" is not an option: the push path has to be handled too.

On top of that, one limitation with no lever at all: the system UI shows only one ringing call.
The only way to keep the second call visible and audible is to keep it out of the system world
entirely and present it in the app.

## Registry combination matrix

The system call UI is a pure function of the system-wide call registry (callservicesd): the app
influences it only by changing what the registry contains. The tables below enumerate the registry
combinations exercised on a real device (iOS 26). "Own calls" means calls this app reported; the
registry also holds cellular and other VoIP calls, which are out of scope here.

### Safe combinations (no system takeover)

| # | Registry state (own calls) | System behavior |
|---|---------------------------|-----------------|
| S1 | empty -> one incoming reported, app foreground | Compact banner on top; app stays interactive; system ringtone plays |
| S2 | one incoming ringing, app background / locked | Full-screen incoming UI (expected platform behavior, not a takeover of the app) |
| S3 | two incoming ringing, app foreground | No takeover: banner(s) only, the app keeps the screen; both calls can sit in the registry for seconds without conflict |
| S4 | connected + outgoing-dialing (CXStartCallAction) -> connected + connected | No system UI at any point: an outgoing call goes active silently; the user never leaves the app |
| S5 | connected + connected (two active calls after both answered) | No prompt; the system in-call UI exists but does not cover the app |
| S6 | ringing -> removed via reportCall(ended, answeredElsewhere) while another call is being answered | The ringing entry silently disappears; no missed-call prompt; the full-screen prompt never forms |
| S7 | incoming reported + ended immediately (the PushKit-mandated report of a deferred call) | At most a sub-second flash; no lasting UI |

### Problem combinations (system takes over or breaks)

| # | Registry state (own calls) | System behavior |
|---|---------------------------|-----------------|
| P1 | connected + ringing (answer one of two ringing calls; the other stays registered) | Full-screen call-waiting prompt covers the app REGARDLESS of foreground state; the app goes paused about half a second after the answer |
| P2 | connected + new incoming reported (second call arrives during an active conversation) | The same full-screen call-waiting prompt; standard iOS call waiting, but it violates the "app owns the UX" goal |
| P3 | held + ringing | Same family as P1: held counts as connected for the prompt logic |
| P4 | two incoming ringing, app background | The full-screen incoming UI shows only ONE call; the second is invisible system-wide and its ringtone is suppressed; no lever exists to show both |
| P5 | UUID reported ended earlier -> reportNewIncomingCall with the SAME UUID | The report "succeeds" (the UI even renders) but the call object is a zombie: CXAnswerCallAction fails with InvalidAction (code 6) or silently never performs, on roughly half of the attempts; the ringing UI then hangs until the call times out |
| P6 | connected + fresh-UUID INCOMING re-report (re-attach of a deferred call as incoming) | The call-waiting prompt appears for the re-reported call and, after answering, iOS parks the user on its own in-call screen; the app cannot force itself back to the foreground |

### Rules distilled from the matrix

1. The trigger is never an event - it is a REGISTRY CONFIGURATION (P1/P2/P3 vs S3/S5).
2. Ringing entries are only dangerous NEXT TO a connected one; any number of pure-ringing entries
   is safe in the foreground (S3) but under-displayed in the background (P4).
3. A CallKit UUID is single-use: once reported ended, never report it as incoming again (P5).
   Use a fresh UUID for any re-entry.
4. Re-entering the registry as INCOMING always risks the prompt (P6); re-entering as OUTGOING is
   silent (S4). Outgoing-dialing next to a connected call is the only combination that adds a call
   to an active conversation without any system UI.
5. Report-plus-instant-end is tolerated by the system (S7) - this is how the mandated PushKit
   report of a call the app wants to keep out of the registry is satisfied.

## The mechanism

One sentence: **iOS sees at most ONE call; all the others live only in the app and enter the system
world at the moment they are answered - silently, as outgoing calls.** (This mirrors the
telecom-deferred direction on Android, so both platforms converge on the same semantics.)

Four pillars, each mapping onto the matrix:

1. **Defer at answer** (turns an imminent P1/P3 into S6). At the moment of answering, every other
   ringing own call is removed from CallKit (`reportCall:endedAtDate:` with reason
   answeredElsewhere) and marked deferred. This fires on both entry points - the in-app answer and
   the lock-screen answer (`performAnswerCallAction`).
2. **Defer on arrival** (turns P2 into "registry untouched", and its PushKit branch into S7). A new
   incoming call while a live call is already in CallKit is not registered at all: the signaling
   path stays silent; the push path does the mandated report followed by an immediate end. One
   native choke point instead of guards scattered across the app layers. The "is there a live
   call" predicate reads the live CXCallObserver list, not accumulated bookkeeping (see pitfall 4).
3. **Fresh UUID + alias translation** (avoids P5 entirely). A deferred call re-enters CallKit under
   a new NSUUID. Original-to-current maps at the plugin boundary translate every action coming from
   Dart and every perform callback going back, so the Dart side keeps living in its stable
   deterministic-UUID world and knows nothing about the aliasing.
4. **Re-attach as outgoing** (turns P6 into S4). The re-attach is done via CXStartCallAction: the
   call becomes active silently, with no system screen. The start action is fulfilled natively and
   reaches Flutter as a regular answer of the original call.

## Platform pitfalls encoded in the implementation

Hard-won behaviors that are not in Apple's documentation:

1. **Zombie UUID.** A call removed from CallKit and then re-reported as incoming with the same UUID
   is accepted (the UI even renders), but the call object is dead inside: answering fails with
   InvalidAction or silently never performs, non-deterministically. The plugin's deterministic
   UUIDs made every naive re-attach hit a burned UUID. Hence pillar 3.
2. **Incoming re-report summons the system.** Even with a fresh UUID, re-reporting as incoming
   during an active conversation draws the call-waiting prompt, and after answering iOS keeps the
   user on its own in-call screen. Hence pillar 4.
3. **Threading.** The completion of `reportNewIncomingCall` can arrive off the main thread;
   mutating the plugin's non-thread-safe state from there is a segfault. Everything touching
   plugin state is confined to the main thread.
4. **The observer forgets the past.** CXCallObserver can drop an ended call from its list before
   the app's cleanup sees it, so bookkeeping accumulated from its callbacks goes stale. Decisions
   that depend on "are there live calls right now" must read the observer's live `calls` list.

## Accepted trade-offs

- A deferred call is invisible and silent outside the app: not in CallKit means no lock-screen
  presence and no system ringtone (the mirror of the Android trade-off).
- A re-attached call appears in the system call log as outgoing.
- A call removed from CallKit at answer time is logged as "answered elsewhere" rather than
  "missed", even if nobody answers it afterwards.

## What this mechanism does not solve

- Visibility of the second call while the app is backgrounded (P4 in its pure form) is consciously
  traded away by the at-most-one-call policy. If the product ever wants a mitigation, that is a
  separate work item (a local notification or an audible cue, in the spirit of
  `ios-call-waiting-tone.md`).
