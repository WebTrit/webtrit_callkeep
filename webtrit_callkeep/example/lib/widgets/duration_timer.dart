import 'dart:async';
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:webtrit_callkeep_example/extensions/extensions.dart';

class DurationTimer extends StatefulWidget {
  const DurationTimer({
    Key? key,
    required this.time,
    this.textStyle,
  }) : super(key: key);

  final DateTime? time;
  final TextStyle? textStyle;

  @override
  State<DurationTimer> createState() => _DurationTimerState();
}

class _DurationTimerState extends State<DurationTimer> {
  Timer? durationTimer;
  Duration? duration;

  @override
  void initState() {
    if (widget.time != null) {
      _durationTimerInit(widget.time!);
    }
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return duration != null
        ? Text(
            duration!.format(),
            maxLines: 2,
            style: widget.textStyle ??
                theme.textTheme.bodySmall!.copyWith(fontFeatures: [
                  const FontFeature.tabularFigures(),
                ], color: theme.colorScheme.background),
          )
        : const SizedBox();
  }

  @override
  void didUpdateWidget(DurationTimer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.time != oldWidget.time) {
      _durationTimerCancel();
      final acceptedTime = widget.time;
      if (acceptedTime != null) {
        _durationTimerInit(acceptedTime);
      }
    }
  }

  void _durationTimerCancel() {
    durationTimer?.cancel();
    durationTimer = null;
  }

  void _durationTimerInit(DateTime time) {
    durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      _durationTic(time);
    });
    _durationTic(time);
  }

  void _durationTic(DateTime acceptedTime) {
    setState(() {
      duration = clock.now().difference(acceptedTime);
    });
  }

  @override
  void dispose() {
    durationTimer?.cancel();
    super.dispose();
  }
}
