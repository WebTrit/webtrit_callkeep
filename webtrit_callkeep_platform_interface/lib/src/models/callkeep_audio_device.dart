class CallkeepAudioDevice {
  final CallkeepAudioDeviceType type;
  final String? id;
  final String? name;

  CallkeepAudioDevice({
    required this.type,
    this.id,
    this.name,
  });
}

enum CallkeepAudioDeviceType {
  earpiece,
  speaker,
  bluetooth,
  wiredHeadset,
  streaming,
  unknown,
}
