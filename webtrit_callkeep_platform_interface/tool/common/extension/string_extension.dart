import '../utils/string_utils.dart';

extension StringExtension on String {
  String pascalCase() {
    return StringUtils().pascalCase(this);
  }

  String constantCase() {
    return StringUtils().constantCase(this);
  }

  String splitPascalCase({String separator = ''}) {
    return StringUtils().splitPascalCase(this, separator: separator);
  }
}
