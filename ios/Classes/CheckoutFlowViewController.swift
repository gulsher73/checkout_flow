import UIKit
import SwiftUI

// CheckoutComponents 1.x ships the umbrella module as `CheckoutComponents`;
// older betas / xcframework distributions use `CheckoutComponentsSDK` as the
// alias. The Checkout sample app uses this exact pattern — try both so the
// plugin compiles against either build of the SwiftPM dep.
//
// If neither symbol is in scope (the host hasn't added the SwiftPM package
// yet), the file still compiles via the `#else` branch below and the plugin
// returns a clear runtime error pointing at the README.
//
// Add the SwiftPM dep to your iOS Runner target via the in-repo automation:
//   afex_sdk_module/scripts/ios/setup-ios.sh   (calls add_swift_package.rb)
// or manually: File → Add Package Dependencies → checkout-ios-components.
#if canImport(CheckoutComponents)
import CheckoutComponents
#elseif canImport(CheckoutComponentsSDK)
import CheckoutComponentsSDK
#endif

enum CheckoutFlowOutcome {
    case completed(paymentId: String?)
    case failed(code: String?, message: String?)
    case cancelled
}

/// Hosts Checkout.com's Flow component inside a `UINavigationController`.
/// Calls the [completion] closure once exactly with the user's outcome.
final class CheckoutFlowViewController: UIViewController {

    private let paymentSession: [String: Any]
    private let publicKey: String
    private let environment: String
    private let locale: String?
    private let screenTitle: String?
    /// Apple Pay merchant identifier registered in the host app's
    /// entitlements (e.g. `merchant.com.alfardanexchange.alfapay`).
    /// When non-nil/non-empty, the Flow component renders an Apple Pay
    /// button alongside the card form. Must match a merchant ID in
    /// `Runner.entitlements > com.apple.developer.in-app-payments`,
    /// otherwise the SDK silently hides the button at runtime.
    private let applePayMerchantId: String?
    private let completion: (CheckoutFlowOutcome) -> Void

    private var hasFinished = false
    #if canImport(CheckoutComponents) || canImport(CheckoutComponentsSDK)
    private var sdk: CheckoutComponents?
    private var hostingController: UIHostingController<AnyView>?
    #endif

    private lazy var spinner: UIActivityIndicatorView = {
        let v = UIActivityIndicatorView(style: .large)
        v.translatesAutoresizingMaskIntoConstraints = false
        v.hidesWhenStopped = true
        return v
    }()

    private lazy var flowContainer: UIView = {
        let v = UIView()
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()

    init(
        paymentSession: [String: Any],
        publicKey: String,
        environment: String,
        locale: String?,
        title: String?,
        applePayMerchantId: String?,
        completion: @escaping (CheckoutFlowOutcome) -> Void
    ) {
        self.paymentSession = paymentSession
        self.publicKey = publicKey
        self.environment = environment
        self.locale = locale
        self.screenTitle = title
        self.applePayMerchantId = applePayMerchantId
        self.completion = completion
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("Use init(paymentSession:...)") }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        title = screenTitle ?? "Payment"

        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .cancel,
            target: self,
            action: #selector(didTapCancel)
        )

        view.addSubview(flowContainer)
        view.addSubview(spinner)
        NSLayoutConstraint.activate([
            flowContainer.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            flowContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            flowContainer.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            flowContainer.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
        spinner.startAnimating()

        Task { @MainActor in
            do {
                try await startFlow()
            } catch {
                self.finish(.failed(
                    code: "init_failed",
                    message: error.localizedDescription
                ))
            }
        }
    }

    @objc private func didTapCancel() {
        finish(.cancelled)
    }

    // MARK: - Checkout SDK wiring

    #if canImport(CheckoutComponents) || canImport(CheckoutComponentsSDK)

    /// Mirrors the 4-step pattern from Checkout.com's sample app
    /// (`SampleApplication/Main/ViewModel/MainViewModel.swift`):
    ///   1. Decode our raw payment-session map into the SDK's `PaymentSession` model
    ///   2. Build `CheckoutComponents.Configuration` (async — talks to CKO servers)
    ///   3. `sdk.create(.flow(options:))` to make the component
    ///   4. Render the SwiftUI view and host it in a `UIHostingController`
    private func startFlow() async throws {
        let env: CheckoutComponents.Environment =
            environment.lowercased() == "production" ? .production : .sandbox

        // Step 1 — typed PaymentSession from the BFF's JSON.
        // The SDK's PaymentSession is Codable; round-trip via JSONSerialization
        // so the host doesn't need to know the SDK's internal field names.
        let sessionJsonData = try JSONSerialization.data(withJSONObject: paymentSession)
        let session = try JSONDecoder().decode(PaymentSession.self, from: sessionJsonData)

        // Step 2 — Configuration. We provide minimal callbacks: onReady (hide
        // the spinner), onSuccess (resolve with paymentId), onError (resolve
        // with failure). The other callbacks (onChange, onTokenized, etc.)
        // aren't needed for the basic Flow → save-card use case.
        let callbacks = CheckoutComponents.Callbacks(
            onReady: { [weak self] _ in
                Task { @MainActor in self?.spinner.stopAnimating() }
            },
            onSuccess: { [weak self] _, paymentID in
                Task { @MainActor in
                    self?.finish(.completed(paymentId: "\(paymentID)"))
                }
            },
            onError: { [weak self] error in
                Task { @MainActor in
                    self?.finish(.failed(
                        code: nil,
                        message: error.localizedDescription
                    ))
                }
            }
        )

        let configuration = try await CheckoutComponents.Configuration(
            paymentSession: session,
            publicKey: publicKey,
            environment: env,
            locale: locale,
            callbacks: callbacks
        )

        let sdk = CheckoutComponents(configuration: configuration)
        self.sdk = sdk

        // Step 3 — Flow component. Card is always enabled; Apple Pay is
        // enabled when the host passes a `merchant.*` identifier that
        // matches its `Runner.entitlements`. The Checkout SDK will only
        // actually render the Apple Pay button if:
        //   1. This option is present here, AND
        //   2. The BFF's payment-session enabled `applepay` in its
        //      `payment_methods` array, AND
        //   3. The device supports Apple Pay (PassKit canMakePayments).
        var options: [CheckoutComponents.PaymentMethod] = [
            .card(
                showPayButton: true,
                paymentButtonAction: .payment,
                cardConfiguration: .init(),
                addressConfiguration: nil,
                rememberMeConfiguration: nil
            ),
        ]
        if let merchantId = applePayMerchantId, !merchantId.isEmpty {
            options.append(.applePay(merchantIdentifier: merchantId))
        }
        let flowComponent = try sdk.create(.flow(options: options))

        // Step 4 — Render and host the SwiftUI view.
        guard
            let renderable = flowComponent as? any CheckoutComponents.Renderable,
            renderable.isAvailable
        else {
            throw NSError(
                domain: "afex_checkout_flow",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey:
                    "Flow component unavailable for this payment session."]
            )
        }

        let renderedView = renderable.render()
        let hosting = UIHostingController(rootView: renderedView)
        addChild(hosting)
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        flowContainer.addSubview(hosting.view)
        NSLayoutConstraint.activate([
            hosting.view.topAnchor.constraint(equalTo: flowContainer.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: flowContainer.bottomAnchor),
            hosting.view.leadingAnchor.constraint(equalTo: flowContainer.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: flowContainer.trailingAnchor),
        ])
        hosting.didMove(toParent: self)
        self.hostingController = hosting
    }

    #else

    /// SDK absent at compile time — surface a clear error instead of silently
    /// no-op'ing. Add the SwiftPM package per
    /// `packages/afex_checkout_flow/README.md`.
    private func startFlow() async throws {
        throw NSError(
            domain: "afex_checkout_flow",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey:
                "CheckoutComponents (Checkout.com Flow) is not linked. Add the "
                + "SwiftPM package https://github.com/checkout/checkout-ios-components "
                + "to the iOS Runner target — see afex_checkout_flow/README.md."]
        )
    }

    #endif

    // MARK: - Result plumbing

    private func finish(_ outcome: CheckoutFlowOutcome) {
        guard !hasFinished else { return }
        hasFinished = true

        let block: () -> Void = { [completion] in completion(outcome) }
        if presentingViewController != nil {
            dismiss(animated: true, completion: block)
        } else {
            block()
        }
    }
}
