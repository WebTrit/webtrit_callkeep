import 'package:flutter/material.dart';

import 'package:webtrit_callkeep_ios/webtrit_callkeep_ios.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  // ignore: unused_field
  final _webtritCallkeepIosPlugin = WebtritCallkeep();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        home: Scaffold(
      appBar: AppBar(
        title: const Text('Plugin example app'),
      ),
      body: const Center(
        child: Text('Webtrit callkeep'),
      ),
    ));
  }
}
