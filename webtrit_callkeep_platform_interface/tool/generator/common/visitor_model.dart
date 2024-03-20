import 'package:analyzer/dart/element/element.dart';
import 'package:analyzer/dart/element/type.dart';
import 'package:analyzer/dart/element/visitor.dart';

class VisitorModel extends SimpleElementVisitor {
  late DartType className;
  Map<String, DartType> fields = {};
  Map<String, List<ElementAnnotation>> metaData = {};
  List<FieldElement> elements = [];

  @override
  visitFieldElement(FieldElement element) {
    fields[element.name] = element.type;
    metaData[element.name] = element.metadata;
    elements.add(element);
  }

  @override
  visitTopLevelVariableElement(TopLevelVariableElement element) {
    fields[element.name] = element.type;
    metaData[element.name] = element.metadata;
  }
}
