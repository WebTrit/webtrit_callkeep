#include "include/webtrit_callkeep_windows/webtrit_callkeep_windows.h"

// This must be included before many other Windows headers.
#include <windows.h>

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>
#include <flutter/standard_method_codec.h>

#include <map>
#include <memory>

namespace {

using flutter::EncodableValue;

class WebtritCallkeepWindows : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  WebtritCallkeepWindows();

  virtual ~WebtritCallkeepWindows();

 private:
  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

// static
void WebtritCallkeepWindows::RegisterWithRegistrar(
    flutter::PluginRegistrarWindows *registrar) {
  auto channel =
      std::make_unique<flutter::MethodChannel<flutter::EncodableValue>>(
          registrar->messenger(), "webtrit_callkeep_windows",
          &flutter::StandardMethodCodec::GetInstance());

  auto plugin = std::make_unique<WebtritCallkeepWindows>();

  channel->SetMethodCallHandler(
      [plugin_pointer = plugin.get()](const auto &call, auto result) {
        plugin_pointer->HandleMethodCall(call, std::move(result));
      });

  registrar->AddPlugin(std::move(plugin));
}

WebtritCallkeepWindows::WebtritCallkeepWindows() {}

WebtritCallkeepWindows::~WebtritCallkeepWindows() {}

void WebtritCallkeepWindows::HandleMethodCall(
    const flutter::MethodCall<flutter::EncodableValue> &method_call,
    std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result) {
  if (method_call.method_name().compare("getPlatformName") == 0) {
    result->Success(EncodableValue("Windows"));
  }
  else {
    result->NotImplemented();
  }
}

}  // namespace

void WebtritCallkeepWindowsRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  WebtritCallkeepWindows::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
