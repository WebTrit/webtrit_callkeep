import Flutter
import UIKit

public class WebtritCallkeepPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "webtrit_callkeep_ios", binaryMessenger: registrar.messenger())
    let instance = WebtritCallkeepPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS")
  }
}
