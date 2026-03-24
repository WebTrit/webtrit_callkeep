// Entry point that runs all integration tests in a single driver invocation.
//
// Used for web:
//   flutter drive \
//     --driver=test_driver/integration_test.dart \
//     --target=integration_test/all_tests.dart \
//     -d chrome
//
// On Android/iOS you can still run individual files directly with
//   flutter test integration_test/<file>_test.dart
//
// Each file is wrapped in its own group() so that top-level setUp/tearDown
// callbacks are scoped to their file and do not interfere with other files.
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'callkeep_background_services_test.dart' as background_services;
import 'callkeep_call_scenarios_test.dart' as call_scenarios;
import 'callkeep_client_scenarios_test.dart' as client_scenarios;
import 'callkeep_connections_test.dart' as connections;
import 'callkeep_delegate_edge_cases_test.dart' as delegate_edge_cases;
import 'callkeep_foreground_service_test.dart' as foreground_service;
import 'callkeep_lifecycle_test.dart' as lifecycle;
import 'callkeep_reportendcall_reasons_test.dart' as reportendcall_reasons;
import 'callkeep_state_machine_test.dart' as state_machine;
import 'callkeep_stress_test.dart' as stress;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  group('lifecycle', lifecycle.main);
  group('call_scenarios', call_scenarios.main);
  group('client_scenarios', client_scenarios.main);
  group('connections', connections.main);
  group('delegate_edge_cases', delegate_edge_cases.main);
  group('foreground_service', foreground_service.main);
  group('background_services', background_services.main);
  group('reportendcall_reasons', reportendcall_reasons.main);
  group('state_machine', state_machine.main);
  group('stress', stress.main);
}
