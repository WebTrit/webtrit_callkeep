import 'package:webtrit_callkeep_platform_interface/webtrit_callkeep_platform_interface.dart';

WebtritCallkeepPlatform get _platform => WebtritCallkeepPlatform.instance;

/// Returns the name of the current platform.
Future<String> getPlatformName() async {
  final platformName = await _platform.getPlatformName();
  if (platformName == null) throw Exception('Unable to get platform name.');
  return platformName;
}
