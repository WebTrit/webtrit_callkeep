enum CallkeepSpecialPermissionStatus {
  denied,
  granted,
  unknown;

  bool get isDenied => this == CallkeepSpecialPermissionStatus.denied;

  bool get isGranted => this == CallkeepSpecialPermissionStatus.granted;

  bool get isUnknown => this == CallkeepSpecialPermissionStatus.unknown;
}
