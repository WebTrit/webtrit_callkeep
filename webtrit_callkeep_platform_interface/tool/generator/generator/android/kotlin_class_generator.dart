// ignore_for_file: cascade_invocations, avoid_redundant_argument_values

import 'package:analyzer/dart/element/element2.dart';

import '../../../common/common.dart';
import '../contact/platform_class_generator.dart';

class KotlinClassGenerator implements PlatformClassGenerator {
  String? className;
  String? outputDirname;

  final _androidDirStructure = 'android/src/main/kotlin/';
  final _androidCodeStyleFieldSpace = '    ';

  @override
  PlatformClassGenerator initialize(
    String className,
    String outputDirname,
  ) {
    this.outputDirname = outputDirname;
    this.className = className;
    return this;
  }

  @override
  String generateClass(
    List<FieldElement2> fields,
  ) {
    final package = outputDirname
            ?.replaceAll(_androidDirStructure, '')
            .replaceAll('/', '.') ??
        '';
    return _buildClass(fields, package);
  }

  String _buildClass(
    List<FieldElement2> fields,
    String package,
  ) {
    final outputClass = StringBuffer();
    outputClass.writeln('// GENERATED CODE - DO NOT MODIFY BY HAND');
    outputClass.writeln('package $package');
    outputClass.writeln('');
    outputClass.writeln('object $className {');
    outputClass.writeAll(_buildConstFields(fields), '\n');
    outputClass.writeln('');
    outputClass.writeln('}');
    return outputClass.toString();
  }

  List<String> _buildConstFields(List<FieldElement2> fields) {
    return fields.map((element) {
      final value = element.computeConstantValue()?.toStringValue();
      final key =
          (element.name3 ?? '').splitPascalCase(separator: '-').constantCase();
      return _buildConstField(key, value);
    }).toList();
  }

  String _buildConstField(String key, String? value) {
    return '${_androidCodeStyleFieldSpace}const val $key = "${value ?? ""}"';
  }
}
