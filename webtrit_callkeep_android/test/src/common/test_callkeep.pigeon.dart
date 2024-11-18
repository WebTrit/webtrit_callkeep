// Autogenerated from Pigeon (v22.6.1), do not edit directly.
// See also: https://pub.dev/packages/pigeon
// ignore_for_file: public_member_api_docs, non_constant_identifier_names, avoid_as, unused_import, unnecessary_parenthesis, unnecessary_import, no_leading_underscores_for_local_identifiers
// ignore_for_file: avoid_relative_lib_imports
import 'dart:async';
import 'dart:typed_data' show Float64List, Int32List, Int64List, Uint8List;
import 'package:flutter/foundation.dart' show ReadBuffer, WriteBuffer;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:webtrit_callkeep_android/src/common/callkeep.pigeon.dart';


class _PigeonCodec extends StandardMessageCodec {
  const _PigeonCodec();
  @override
  void writeValue(WriteBuffer buffer, Object? value) {
    if (value is int) {
      buffer.putUint8(4);
      buffer.putInt64(value);
    }    else if (value is PLogTypeEnum) {
      buffer.putUint8(129);
      writeValue(buffer, value.index);
    }    else if (value is PSpecialPermissionStatusTypeEnum) {
      buffer.putUint8(130);
      writeValue(buffer, value.index);
    }    else if (value is PCallkeepAndroidBatteryMode) {
      buffer.putUint8(131);
      writeValue(buffer, value.index);
    }    else if (value is PHandleTypeEnum) {
      buffer.putUint8(132);
      writeValue(buffer, value.index);
    }    else if (value is PCallInfoConsts) {
      buffer.putUint8(133);
      writeValue(buffer, value.index);
    }    else if (value is PEndCallReasonEnum) {
      buffer.putUint8(134);
      writeValue(buffer, value.index);
    }    else if (value is PIncomingCallErrorEnum) {
      buffer.putUint8(135);
      writeValue(buffer, value.index);
    }    else if (value is PCallRequestErrorEnum) {
      buffer.putUint8(136);
      writeValue(buffer, value.index);
    }    else if (value is PCallkeepLifecycleType) {
      buffer.putUint8(137);
      writeValue(buffer, value.index);
    }    else if (value is PIOSOptions) {
      buffer.putUint8(138);
      writeValue(buffer, value.encode());
    }    else if (value is PAndroidOptions) {
      buffer.putUint8(139);
      writeValue(buffer, value.encode());
    }    else if (value is POptions) {
      buffer.putUint8(140);
      writeValue(buffer, value.encode());
    }    else if (value is PHandle) {
      buffer.putUint8(141);
      writeValue(buffer, value.encode());
    }    else if (value is PEndCallReason) {
      buffer.putUint8(142);
      writeValue(buffer, value.encode());
    }    else if (value is PIncomingCallError) {
      buffer.putUint8(143);
      writeValue(buffer, value.encode());
    }    else if (value is PCallRequestError) {
      buffer.putUint8(144);
      writeValue(buffer, value.encode());
    }    else if (value is PCallkeepServiceStatus) {
      buffer.putUint8(145);
      writeValue(buffer, value.encode());
    } else {
      super.writeValue(buffer, value);
    }
  }

  @override
  Object? readValueOfType(int type, ReadBuffer buffer) {
    switch (type) {
      case 129: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PLogTypeEnum.values[value];
      case 130: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PSpecialPermissionStatusTypeEnum.values[value];
      case 131: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PCallkeepAndroidBatteryMode.values[value];
      case 132: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PHandleTypeEnum.values[value];
      case 133: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PCallInfoConsts.values[value];
      case 134: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PEndCallReasonEnum.values[value];
      case 135: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PIncomingCallErrorEnum.values[value];
      case 136: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PCallRequestErrorEnum.values[value];
      case 137: 
        final int? value = readValue(buffer) as int?;
        return value == null ? null : PCallkeepLifecycleType.values[value];
      case 138: 
        return PIOSOptions.decode(readValue(buffer)!);
      case 139: 
        return PAndroidOptions.decode(readValue(buffer)!);
      case 140: 
        return POptions.decode(readValue(buffer)!);
      case 141: 
        return PHandle.decode(readValue(buffer)!);
      case 142: 
        return PEndCallReason.decode(readValue(buffer)!);
      case 143: 
        return PIncomingCallError.decode(readValue(buffer)!);
      case 144: 
        return PCallRequestError.decode(readValue(buffer)!);
      case 145: 
        return PCallkeepServiceStatus.decode(readValue(buffer)!);
      default:
        return super.readValueOfType(type, buffer);
    }
  }
}
