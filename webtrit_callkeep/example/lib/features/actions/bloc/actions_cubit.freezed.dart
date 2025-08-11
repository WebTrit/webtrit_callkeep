// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint
// ignore_for_file: unused_element, deprecated_member_use, deprecated_member_use_from_same_package, use_function_type_syntax_for_parameters, unnecessary_const, avoid_init_to_null, invalid_override_different_default_values_named, prefer_expression_function_bodies, annotate_overrides, invalid_annotation_target, unnecessary_question_mark

part of 'actions_cubit.dart';

// **************************************************************************
// FreezedGenerator
// **************************************************************************

T _$identity<T>(T value) => value;

final _privateConstructorUsedError = UnsupportedError(
    'It seems like you constructed your class using `MyClass._()`. This constructor is only meant to be used by freezed and you are not supposed to need it nor use it.\nPlease check the documentation here for more information: https://github.com/rrousselGit/freezed#adding-getters-and-methods-to-our-models');

/// @nodoc
mixin _$ActionsState {
  List<String> get actions => throw _privateConstructorUsedError;

  /// Create a copy of ActionsState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  $ActionsStateCopyWith<ActionsState> get copyWith =>
      throw _privateConstructorUsedError;
}

/// @nodoc
abstract class $ActionsStateCopyWith<$Res> {
  factory $ActionsStateCopyWith(
          ActionsState value, $Res Function(ActionsState) then) =
      _$ActionsStateCopyWithImpl<$Res, ActionsState>;
  @useResult
  $Res call({List<String> actions});
}

/// @nodoc
class _$ActionsStateCopyWithImpl<$Res, $Val extends ActionsState>
    implements $ActionsStateCopyWith<$Res> {
  _$ActionsStateCopyWithImpl(this._value, this._then);

  // ignore: unused_field
  final $Val _value;
  // ignore: unused_field
  final $Res Function($Val) _then;

  /// Create a copy of ActionsState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? actions = null,
  }) {
    return _then(_value.copyWith(
      actions: null == actions
          ? _value.actions
          : actions // ignore: cast_nullable_to_non_nullable
              as List<String>,
    ) as $Val);
  }
}

/// @nodoc
abstract class _$$ActionsStateImplCopyWith<$Res>
    implements $ActionsStateCopyWith<$Res> {
  factory _$$ActionsStateImplCopyWith(
          _$ActionsStateImpl value, $Res Function(_$ActionsStateImpl) then) =
      __$$ActionsStateImplCopyWithImpl<$Res>;
  @override
  @useResult
  $Res call({List<String> actions});
}

/// @nodoc
class __$$ActionsStateImplCopyWithImpl<$Res>
    extends _$ActionsStateCopyWithImpl<$Res, _$ActionsStateImpl>
    implements _$$ActionsStateImplCopyWith<$Res> {
  __$$ActionsStateImplCopyWithImpl(
      _$ActionsStateImpl _value, $Res Function(_$ActionsStateImpl) _then)
      : super(_value, _then);

  /// Create a copy of ActionsState
  /// with the given fields replaced by the non-null parameter values.
  @pragma('vm:prefer-inline')
  @override
  $Res call({
    Object? actions = null,
  }) {
    return _then(_$ActionsStateImpl(
      actions: null == actions
          ? _value._actions
          : actions // ignore: cast_nullable_to_non_nullable
              as List<String>,
    ));
  }
}

/// @nodoc

class _$ActionsStateImpl extends _ActionsState {
  const _$ActionsStateImpl({required final List<String> actions})
      : _actions = actions,
        super._();

  final List<String> _actions;
  @override
  List<String> get actions {
    if (_actions is EqualUnmodifiableListView) return _actions;
    // ignore: implicit_dynamic_type
    return EqualUnmodifiableListView(_actions);
  }

  @override
  String toString() {
    return 'ActionsState(actions: $actions)';
  }

  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
        (other.runtimeType == runtimeType &&
            other is _$ActionsStateImpl &&
            const DeepCollectionEquality().equals(other._actions, _actions));
  }

  @override
  int get hashCode =>
      Object.hash(runtimeType, const DeepCollectionEquality().hash(_actions));

  /// Create a copy of ActionsState
  /// with the given fields replaced by the non-null parameter values.
  @JsonKey(includeFromJson: false, includeToJson: false)
  @override
  @pragma('vm:prefer-inline')
  _$$ActionsStateImplCopyWith<_$ActionsStateImpl> get copyWith =>
      __$$ActionsStateImplCopyWithImpl<_$ActionsStateImpl>(this, _$identity);
}

abstract class _ActionsState extends ActionsState {
  const factory _ActionsState({required final List<String> actions}) =
      _$ActionsStateImpl;
  const _ActionsState._() : super._();

  @override
  List<String> get actions;

  /// Create a copy of ActionsState
  /// with the given fields replaced by the non-null parameter values.
  @override
  @JsonKey(includeFromJson: false, includeToJson: false)
  _$$ActionsStateImplCopyWith<_$ActionsStateImpl> get copyWith =>
      throw _privateConstructorUsedError;
}
