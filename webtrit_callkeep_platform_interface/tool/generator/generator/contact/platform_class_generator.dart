import 'package:analyzer/dart/element/element.dart';

abstract class PlatformClassGenerator {
  PlatformClassGenerator initialize(
    String className,
    String outputDirname,
  );

  String generateClass(
    List<FieldElement> fields,
  );
}
