/// How incoming calls are delivered on Android.
///
/// - [telecom]: the device supports `android.software.telecom`, so calls go
///   through the system Telecom `android.telecom.ConnectionService` path (full integration).
/// - [standalone]: the device lacks Telecom, so calls use a limited standalone
///   foreground service. Delivery may be throttled by the system (Doze,
///   background restrictions) and outgoing calls, hold and Bluetooth/wired
///   headset selection are not available.
/// - [unknown]: the mode could not be determined or the platform is not Android.
enum CallkeepAndroidCallDeliveryMode { telecom, standalone, unknown }
