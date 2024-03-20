import 'package:build/build.dart';

import 'package:path/path.dart' as path;

import 'path_model.dart';

class BuildStepHelper {
  static OutDirModel prepareOutDir(BuildStep buildStep) {
    final originOutPath = path.withoutExtension(buildStep.allowedOutputs.first.path);
    final fileName = path.basenameWithoutExtension(originOutPath);
    final baseDir = path.dirname(originOutPath);
    const extension = '.kt';
    final fullPath = path.join(path.normalize(baseDir), '$fileName$extension');
    return OutDirModel(baseDir: baseDir, baseName: fileName, extension: extension, fullPath: fullPath);
  }
}
