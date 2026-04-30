import Flutter
import UIKit

/// Flutter plugin entry. Registers a single MethodChannel and forwards
/// `launchFlow` calls to a `CheckoutFlowViewController` that hosts the
/// native Checkout.com Components SDK.
public class CheckoutFlowPlugin: NSObject, FlutterPlugin {
    private static let channelName = "com.alfapay/checkout_flow"

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: channelName,
            binaryMessenger: registrar.messenger()
        )
        let instance = CheckoutFlowPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "launchFlow":
            launchFlow(arguments: call.arguments, result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    // MARK: - launchFlow

    private func launchFlow(arguments: Any?, result: @escaping FlutterResult) {
        guard
            let args = arguments as? [String: Any],
            let session = args["paymentSession"] as? [String: Any],
            let publicKey = args["publicKey"] as? String,
            !publicKey.isEmpty
        else {
            result(FlutterError(
                code: "invalid_args",
                message: "paymentSession and publicKey are required",
                details: nil
            ))
            return
        }

        let environment = (args["environment"] as? String) ?? "sandbox"
        let locale = args["locale"] as? String
        let title = args["title"] as? String

        guard let presenter = Self.topViewController() else {
            result(FlutterError(
                code: "no_view_controller",
                message: "checkout_flow could not find a view controller to present from.",
                details: nil
            ))
            return
        }

        let controller = CheckoutFlowViewController(
            paymentSession: session,
            publicKey: publicKey,
            environment: environment,
            locale: locale,
            title: title,
            completion: { outcome in
                switch outcome {
                case .cancelled:
                    result(FlutterError(code: "cancelled", message: "User cancelled", details: nil))
                case .completed(let paymentId):
                    result([
                        "status": "completed",
                        "paymentId": paymentId as Any,
                    ])
                case .failed(let code, let message):
                    result([
                        "status": "failed",
                        "errorCode": code as Any,
                        "errorMessage": message as Any,
                    ])
                }
            }
        )

        let nav = UINavigationController(rootViewController: controller)
        nav.modalPresentationStyle = .fullScreen
        presenter.present(nav, animated: true)
    }

    // MARK: - Helpers

    private static func topViewController(
        base: UIViewController? = nil
    ) -> UIViewController? {
        let baseVC = base ?? Self.rootViewController()

        if let nav = baseVC as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        }
        if let tab = baseVC as? UITabBarController, let selected = tab.selectedViewController {
            return topViewController(base: selected)
        }
        if let presented = baseVC?.presentedViewController {
            return topViewController(base: presented)
        }
        return baseVC
    }

    /// Resolve the active window's root VC. `UIWindowScene.keyWindow` is iOS
    /// 15+, so on older iOS we fall back to `UIApplication.shared.windows`.
    private static func rootViewController() -> UIViewController? {
        if #available(iOS 15.0, *) {
            return UIApplication.shared
                .connectedScenes
                .compactMap { ($0 as? UIWindowScene)?.keyWindow }
                .first?
                .rootViewController
        }
        if #available(iOS 13.0, *) {
            return UIApplication.shared
                .connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first { $0.isKeyWindow }?
                .rootViewController
        }
        return UIApplication.shared.windows.first { $0.isKeyWindow }?.rootViewController
    }
}
