import 'package:build/build.dart';

import 'builders/platform_class_builder.dart';
import 'generator/android/kotlin_class_generator.dart';

Builder platformClassBuilder(BuilderOptions options) => PlatformClassBuilder(
      outPatter:
          '../webtrit_callkeep_android/android/src/main/kotlin/com/webtrit/callkeep/webtrit_callkeep_android/generated/{{}}.kt',
      inputPatter: '{{}}.dart',
      generator: KotlinClassGenerator(),
    );
