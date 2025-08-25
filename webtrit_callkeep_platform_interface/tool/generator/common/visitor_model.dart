import 'package:analyzer/dart/element/element2.dart';
import 'package:analyzer/dart/element/type.dart';
import 'package:analyzer/dart/element/visitor2.dart';

class VisitorModel extends SimpleElementVisitor2<void> {
  late DartType className;
  Map<String, DartType> fields = {};
  Map<String, List<ElementAnnotation>> metaData = {};
  List<FieldElement2> elements = [];

  @override
  void visitFieldElement(FieldElement2 element) {
    fields[element.name3 ?? ''] = element.type;
    metaData[element.name3 ?? ''] = element.metadata2.annotations;
    elements.add(element);
  }

  @override
  void visitTopLevelVariableElement(TopLevelVariableElement2 element) {
    fields[element.name3 ?? ''] = element.type;
    metaData[element.name3 ?? ''] = element.metadata2.annotations;
  }
}
