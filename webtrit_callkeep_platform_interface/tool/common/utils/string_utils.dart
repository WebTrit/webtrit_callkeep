import 'package:dart_casing/dart_casing.dart';

class StringUtils {
  static final StringUtils _singleton = StringUtils._internal();

  factory StringUtils() {
    return _singleton;
  }

  StringUtils._internal();

  final _beforeNonLeadingCapitalLetter = RegExp(r'(?=(?!^)[A-Z])');

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
