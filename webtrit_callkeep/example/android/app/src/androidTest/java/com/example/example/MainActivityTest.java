package com.example.example;

import androidx.test.rule.ActivityTestRule;
import dev.flutter.plugins.integration_test.FlutterTestRunner;
import org.junit.Rule;
import org.junit.runner.RunWith;

// Entry point for Firebase Test Lab instrumentation.
// FlutterTestRunner launches the Flutter app (built with --target pointing
// to an integration_test/*.dart file) and delegates test execution to the
// Dart side via the integration_test package.
@RunWith(FlutterTestRunner.class)
public class MainActivityTest {
    @Rule
    public ActivityTestRule<MainActivity> rule =
            new ActivityTestRule<>(MainActivity.class, true, false);
}
