import 'package:analyzer/dart/element/element2.dart';

abstract class PlatformClassGenerator {
  PlatformClassGenerator initialize(
    String className,
    String outputDirname,
  );

  String generateClass(
    List<FieldElement2> fields,
  );
}
