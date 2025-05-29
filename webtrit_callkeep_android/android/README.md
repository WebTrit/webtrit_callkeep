# Webtrit CallKeep Android Plugin

## Overview
The `Webtrit CallKeep Android Plugin` is a comprehensive plugin implementation for Flutter on Android, enabling advanced call management using the Android Telecom framework. It supports both incoming and outgoing calls, integrates with native services, handles background and foreground execution, and enables seamless communication between Flutter isolates and Android services.

This plugin provides robust support for:
- Call management (initiate, answer, decline, hold, mute, etc.)
- Full-screen notifications
- Foreground and background service management
- Wake lock and lifecycle handling
- Integration with Android Telecom API
- Communication through Pigeon-generated Flutter APIs

---

## Permissions & Features
Defined in `AndroidManifest.xml`:

**Required permissions:**
- `MANAGE_OWN_CALLS`
- `READ_PHONE_NUMBERS`
- `USE_FULL_SCREEN_INTENT`
- `VIBRATE`, `WAKE_LOCK`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`
- `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CAMERA`
- `RECEIVE_BOOT_COMPLETED`
- `POST_NOTIFICATIONS`

**Used features:**
- `android.hardware.telephony` (optional)
- `android.software.telecom` (required)

---

## Plugin Entry Point
### `WebtritCallkeepPlugin`
This is the main plugin class that registers and initializes:
- Pigeon APIs (Permissions, Sound, Connections)
- Background signaling and push notification services
- Lifecycle observers
- Communication bridges between services and Flutter via binary messengers

The plugin supports `ActivityAware`, `ServiceAware`, and `LifecycleEventObserver` to manage binding/unbinding logic and isolate registration.

---

## Background Isolate APIs
### `BackgroundSignalingIsolateBootstrapApi`
Handles background signaling service initialization and control:
- Registers callback dispatchers
- Configures foreground notification title and description
- Starts/stops the `SignalingService`

### `BackgroundPushNotificationIsolateBootstrapApi`
Handles:
- Callback dispatcher registration for incoming call events
- Dispatching new incoming call notifications to Android Telecom and Flutter

---

## Android Services

### `SignalingService`
Foreground service responsible for signaling and lifecycle awareness:
- Maintains a persistent notification
- Starts the Flutter engine in background mode
- Handles call commands: start, stop, answer, decline, update lifecycle

### `IncomingCallService`
Foreground service for handling push-notified incoming calls:
- Starts on incoming notification
- Manages call state via background isolate or direct signaling
- Handles answering, hangup, and decline actions based on app lifecycle state

### `ForegroundService`
Service responsible for handling active call state while the app is running:
- Hosts the `PHostApi` for bidirectional method calls with Flutter
- Registers notification channels and manages call setup/teardown
- Handles mute, hold, speaker, and DTMF functionality

### `ActiveCallService`
Displays an ongoing call notification for multiple simultaneous calls. Handles:
- Notification building and management
- Hangup action from notification area

### `PhoneConnectionService`
Implements Android’s `ConnectionService`:
- Manages the creation of incoming/outgoing connections
- Routes connection states to Android Telecom
- Coordinates sensor and wake lock handling
- Notifies Flutter about call changes via `CommunicateServiceDispatcher`

---

## Connection Handling
### `PhoneConnection`
A subclass of Android's `Connection`:
- Represents an individual call
- Handles answer, reject, disconnect, mute, hold, and speaker changes
- Manages DTMF, video state, and audio route
- Communicates call state updates via `CommunicateServiceDispatcher`

### `ConnectionTimeout`
Custom utility to enforce timeouts on call states (e.g. ringing or dialing timeouts).

---

## Dispatcher Utilities
### `PhoneConnectionServiceDispatcher`
Handles connection state updates such as:
- Answer, Decline, HungUp
- Mute, Hold, DTMF
- Speaker toggle and update call metadata

### `IncomingCallEventDispatcher`
Decides which service (foreground or background) should handle incoming call events based on `SignalingService` status.

### `CommunicateServiceDispatcher`
Generic broadcast dispatcher used by `PhoneConnectionService` and `PhoneConnection` to notify registered services (e.g., `ForegroundService`, `IncomingCallService`) about:
- Answer/Decline
- Speaker/Mute
- Hold/DTMF
- Failure events

---

## Notifications
Handled by custom builders:
- `IncomingCallNotificationBuilder`
- `ForegroundCallNotificationBuilder`
- `ActiveCallNotificationBuilder`
- `MissedCallNotificationBuilder`

Also includes `NotificationChannelManager` to register system channels.

---

## Conclusion
This plugin provides a production-ready, telecom-compliant, and Flutter-integrated call management system on Android. It leverages Android’s Telecom APIs, Pigeon-based Flutter communication, isolate awareness, and foreground/background execution strategies to support real-time, reliable, and customizable telephony experiences.

It is especially suitable for VoIP or SIP-based apps, and apps that require full-call lifecycle control within a Flutter + Android hybrid environment.
