import 'package:dart_casing/dart_casing.dart';

// TODO convert to abstract class with static methods

class StringUtils {
  factory StringUtils() => _singleton;
  StringUtils._internal();

  static final StringUtils _singleton = StringUtils._internal();

  final _beforeNonLeadingCapitalLetter = RegExp('(?=(?!^)[A-Z])');

  List<String> _splitPascalCase(String input) {
    return input.split(_beforeNonLeadingCapitalLetter);
  }

  String pascalCase(String input) {
    return Casing.pascalCase(input);
  }

  String constantCase(String input) {
    return Casing.constantCase(input);
  }

  String splitPascalCase(String input, {String separator = ''}) {
    return _splitPascalCase(input).join(separator);
  }
}
