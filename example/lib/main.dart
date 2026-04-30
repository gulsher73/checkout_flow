import 'package:checkout_flow/checkout_flow.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'checkout_flow demo',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.indigo),
      home: const _DemoPage(),
    );
  }
}

class _DemoPage extends StatefulWidget {
  const _DemoPage();

  @override
  State<_DemoPage> createState() => _DemoPageState();
}

class _DemoPageState extends State<_DemoPage> {
  CheckoutFlowResult? _last;
  bool _busy = false;

  Future<void> _launch() async {
    setState(() {
      _busy = true;
      _last = null;
    });

    // Replace with a real `payment-session` body from your BFF — both
    // native SDKs validate the entire object.
    final paymentSession = <String, dynamic>{
      'id': 'ps_REPLACE_ME',
      'amount': 1000,
      'currency': 'AED',
    };

    final result = await CheckoutFlow.launch(
      paymentSession: paymentSession,
      publicKey: 'pk_sbox_REPLACE_ME',
      environment: CheckoutFlowEnvironment.sandbox,
      locale: 'en-AE',
      title: 'Pay with card',
    );

    if (!mounted) return;
    setState(() {
      _busy = false;
      _last = result;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('checkout_flow demo')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FilledButton(
              onPressed: _busy ? null : _launch,
              child: _busy
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Text('Launch Checkout Flow'),
            ),
            const SizedBox(height: 24),
            if (_last != null) _ResultCard(result: _last!),
          ],
        ),
      ),
    );
  }
}

class _ResultCard extends StatelessWidget {
  final CheckoutFlowResult result;
  const _ResultCard({required this.result});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = switch (result.status) {
      CheckoutFlowStatus.completed => Colors.green,
      CheckoutFlowStatus.failed => cs.error,
      CheckoutFlowStatus.cancelled => cs.outline,
    };
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              result.status.name.toUpperCase(),
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: color,
                letterSpacing: 0.6,
              ),
            ),
            const SizedBox(height: 8),
            if (result.paymentId != null)
              Text('paymentId: ${result.paymentId}'),
            if (result.errorCode != null)
              Text('errorCode:  ${result.errorCode}'),
            if (result.errorMessage != null)
              Text('message:    ${result.errorMessage}'),
          ],
        ),
      ),
    );
  }
}
