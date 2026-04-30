/// Maps to Checkout.com's `environment` enum on both native SDKs.
enum CheckoutFlowEnvironment {
  sandbox,
  production;

  String get wireValue => switch (this) {
        CheckoutFlowEnvironment.sandbox => 'sandbox',
        CheckoutFlowEnvironment.production => 'production',
      };
}

enum CheckoutFlowStatus {
  /// Payment was approved end-to-end (incl. 3DS challenge).
  completed,

  /// Native SDK reported a failure — see [CheckoutFlowResult.errorCode].
  failed,

  /// User dismissed the sheet without completing.
  cancelled,
}

/// Outcome of a single `AfexCheckoutFlow.launch` invocation.
class CheckoutFlowResult {
  /// The final state of the Flow screen.
  final CheckoutFlowStatus status;

  /// Checkout.com payment id (`pay_xxx`) — present on [CheckoutFlowStatus.completed].
  final String? paymentId;

  /// Optional machine-readable code surfaced by the SDK on failure
  /// (e.g. `payment_session_invalid`, `card_declined`).
  final String? errorCode;

  /// Human-readable message surfaced by the SDK on failure.
  final String? errorMessage;

  const CheckoutFlowResult({
    required this.status,
    this.paymentId,
    this.errorCode,
    this.errorMessage,
  });

  bool get isSuccess => status == CheckoutFlowStatus.completed;

  factory CheckoutFlowResult.fromMap(Map<dynamic, dynamic> map) {
    final raw = (map['status'] as String?) ?? 'failed';
    final status = switch (raw) {
      'completed' => CheckoutFlowStatus.completed,
      'cancelled' => CheckoutFlowStatus.cancelled,
      _ => CheckoutFlowStatus.failed,
    };
    return CheckoutFlowResult(
      status: status,
      paymentId: map['paymentId'] as String?,
      errorCode: map['errorCode'] as String?,
      errorMessage: map['errorMessage'] as String?,
    );
  }

  factory CheckoutFlowResult.cancelled() =>
      const CheckoutFlowResult(status: CheckoutFlowStatus.cancelled);

  factory CheckoutFlowResult.failed(String message, {String? code}) =>
      CheckoutFlowResult(
        status: CheckoutFlowStatus.failed,
        errorCode: code,
        errorMessage: message,
      );
}
