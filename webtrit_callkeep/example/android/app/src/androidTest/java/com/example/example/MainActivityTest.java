package com.example.example;

import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import dev.flutter.plugins.integration_test.FlutterTestRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

// Entry point for Firebase Test Lab instrumentation.
// FlutterTestRunner launches the Flutter app (built with --target pointing
// to an integration_test/*.dart file) and delegates test execution to the
// Dart side via the integration_test package.
@RunWith(FlutterTestRunner.class)
public class MainActivityTest {
    @Rule
    public ActivityTestRule<MainActivity> rule =
            new ActivityTestRule<>(MainActivity.class, true, false);

    // Grant POST_NOTIFICATIONS before tests run so notification-related
    // behaviour and background components that depend on this permission
    // can operate reliably during instrumentation tests.
    //
    // UiAutomation.grantRuntimePermission() is unreliable on API 33+ for this
    // permission; using "pm grant" via executeShellCommand runs as the shell
    // user which holds GRANT_RUNTIME_PERMISSIONS and propagates immediately.
    @Before
    public void grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation()
                        .executeShellCommand(
                                "pm grant com.example.example android.permission.POST_NOTIFICATIONS"
                        );
                // Drain the output stream to ensure the command completes before
                // continuing.
                InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                byte[] buffer = new byte[256];
                //noinspection StatementWithEmptyBody
                while (stream.read(buffer) != -1) {}
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
