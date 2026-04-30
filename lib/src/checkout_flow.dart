import 'package:flutter/services.dart';

import 'checkout_flow_models.dart';

/// Public entry point for the plugin.
class CheckoutFlow {
  CheckoutFlow._();

  static const MethodChannel _channel =
      MethodChannel('com.alfapay/checkout_flow');

  /// Launch the native Checkout.com Flow screen.
  ///
  /// [paymentSession] is the raw response from Checkout.com's
  /// `POST /payment-sessions` (or your BFF that proxies it). The native SDK
  /// validates the entire object — pass it through verbatim, do not strip
  /// fields.
  ///
  /// [publicKey] is the partner's Checkout.com public key (`pk_…`).
  ///
  /// [environment] selects sandbox vs production. Must match the public key.
  ///
  /// [locale] is an optional BCP-47 tag forwarded to the native SDK for
  /// translations (e.g. `en-AE`, `ar-AE`).
  ///
  /// [title] is the screen title shown above the Flow component on Android.
  /// (iOS uses the standard navigation bar with a Cancel button.)
  ///
  /// [applePayMerchantId] is the Apple Merchant ID (e.g.
  /// `merchant.com.example.app`) registered in the iOS host's
  /// `Runner.entitlements > com.apple.developer.in-app-payments`.
  /// When supplied AND the BFF's payment-session enables `applepay`,
  /// an Apple Pay button is rendered beside the card form on iOS. The
  /// arg is ignored on Android (Google Pay's merchant config lives in
  /// the payment session, not on the SDK init).
  static Future<CheckoutFlowResult> launch({
    required Map<String, dynamic> paymentSession,
    required String publicKey,
    CheckoutFlowEnvironment environment = CheckoutFlowEnvironment.sandbox,
    String? locale,
    String? title,
    String? applePayMerchantId,
  }) async {
    try {
      final raw = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'launchFlow',
        <String, dynamic>{
          'paymentSession': paymentSession,
          'publicKey': publicKey,
          'environment': environment.wireValue,
          'locale': ?locale,
          'title': ?title,
          'applePayMerchantId': ?applePayMerchantId,
        },
      );
      if (raw == null) {
        return CheckoutFlowResult.failed('Empty response from native SDK');
      }
      return CheckoutFlowResult.fromMap(raw);
    } on PlatformException catch (e) {
      // Native side throws `cancelled` as a PlatformException to keep the
      // happy-path return type clean — translate it back here.
      if (e.code == 'cancelled') return CheckoutFlowResult.cancelled();
      return CheckoutFlowResult.failed(
        e.message ?? 'Checkout Flow failed',
        code: e.code,
      );
    } on MissingPluginException {
      return CheckoutFlowResult.failed(
        'checkout_flow plugin is not registered on this platform',
        code: 'plugin_missing',
      );
    }
  }
}
