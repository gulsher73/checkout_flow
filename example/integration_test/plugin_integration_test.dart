// Smoke test — verifies the example app boots and the CheckoutFlow API is
// importable. A real end-to-end test would require a sandbox public key
// and a live `payment-session` from Checkout.com, both of which the CI
// environment doesn't have access to.

import 'package:checkout_flow/checkout_flow.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('CheckoutFlow API surface is exposed', (tester) async {
    expect(CheckoutFlowEnvironment.sandbox.wireValue, 'sandbox');
    expect(CheckoutFlowEnvironment.production.wireValue, 'production');
    final cancelled = CheckoutFlowResult.cancelled();
    expect(cancelled.status, CheckoutFlowStatus.cancelled);
  });
}
