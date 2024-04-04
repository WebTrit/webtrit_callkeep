import 'package:flutter/material.dart';

class Button extends StatelessWidget {
  const Button({
    Key? key,
    required this.title,
    required this.onClick,
    this.textAlign = TextAlign.center,
    this.padding = const EdgeInsets.all(16),
  }) : super(key: key);

  final EdgeInsets padding;
  final String title;
  final TextAlign textAlign;
  final Function() onClick;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onClick,
      child: Container(
        margin: const EdgeInsets.all(8),
        padding: padding,
        constraints: const BoxConstraints(maxHeight: 64),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(4),
          boxShadow: [
            BoxShadow(
              color: Colors.grey.withOpacity(0.2),
              spreadRadius: 1,
              blurRadius: 4,
              offset: const Offset(0, 3),
            ),
          ],
        ),
        child: Text(
          title,
          textAlign: textAlign,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ),
    );
  }
}
