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
  lifecycle.main();
  call_scenarios.main();
  client_scenarios.main();
  connections.main();
  delegate_edge_cases.main();
  foreground_service.main();
  background_services.main();
  reportendcall_reasons.main();
  state_machine.main();
  stress.main();
}
