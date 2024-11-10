enum CallkeepSpecialPermissionStatus {
  denied,
  granted;

  bool get isDenied => this == CallkeepSpecialPermissionStatus.denied;

  bool get isGranted => this == CallkeepSpecialPermissionStatus.granted;
}
