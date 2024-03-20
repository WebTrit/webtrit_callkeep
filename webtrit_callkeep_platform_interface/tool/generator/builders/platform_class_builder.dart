import 'package:build/build.dart';
import 'package:source_gen/source_gen.dart';
import 'package:analyzer/dart/element/element.dart';

import 'package:webtrit_callkeep_platform_interface/src/annotation/annotation.dart';

import '../common/common.dart';
import '../generator/platform_generator.dart';

class PlatformClassBuilder implements Builder {
  PlatformClassBuilder({
    required this.outPatter,
    required this.inputPatter,
    required this.generator,
  });

  final String outPatter;
  final String inputPatter;

  final PlatformClassGenerator generator;

  final TypeChecker _multiplatformConstFileChecker = const TypeChecker.fromRuntime(MultiplatformConstFile);
  final TypeChecker _multiplatformConstFieldChecker = const TypeChecker.fromRuntime(MultiplatformConstField);

  @override
  Map<String, List<String>> get buildExtensions => {
        inputPatter: [outPatter],
      };

  @override
  Future<void> build(BuildStep buildStep) async {
    try {
      final outputDir = BuildStepHelper.prepareOutDir(buildStep);
      final annotated = await _getClassesAnnotatedWithFile(buildStep);
      final allFields = annotated.map((field) {
        if (field.element is! ClassElement) {
          return null;
        }

        final className = field.element.displayName;
        final fieldList = _getAllFields(field.element as ClassElement);
        final filteredLists = _getAnnotatedFields(fieldList);

        return generator.initialize(className, outputDir.baseDir).generateClass(filteredLists);
      }).where((element) => element != null);

      if (allFields.isEmpty) {
        return;
      }
      log.info('Output path: ${outputDir.fullPath}');
      log.info('Output name: ${outputDir.baseName}');
      log.info('Output dir: ${outputDir.baseDir}');

      await buildStep.writeAsString(AssetId(buildStep.inputId.package, outputDir.fullPath), allFields.join('\n'));
    } catch (err, stack) {
      log.severe('Failed to generate kotlin', err, stack);
    }
  }

  Future<Iterable<AnnotatedElement>> _getClassesAnnotatedWithFile(BuildStep buildStep) async {
    final lib = await buildStep.resolver.libraryFor(buildStep.inputId, allowSyntaxErrors: true);
    final libraryReader = LibraryReader(lib);
    return libraryReader.annotatedWith(_multiplatformConstFileChecker);
  }

  List<FieldElement> _getAllFields(ClassElement element) {
    final visitor = VisitorModel();
    element.visitChildren(visitor);
    return [...visitor.elements];
  }

  List<FieldElement> _getAnnotatedFields(List<FieldElement> fieldList) {
    return fieldList.where((element) => _multiplatformConstFieldChecker.hasAnnotationOf(element)).toList();
  }
}
