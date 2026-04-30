# checkout_flow

Flutter bridge for [Checkout.com's native Flow Components SDK](https://www.checkout.com/docs/payments/accept-payments/accept-a-payment-on-your-mobile-app/get-started-with-flow-for-mobile).
Wraps the iOS + Android native SDKs behind a single `MethodChannel`, returns
the user's payment outcome to Dart.

> **Why a separate plugin?** Checkout.com's Flow SDKs are distributed via
> SwiftPM (iOS) and Maven Central (Android). This plugin contains only the
> Dart + Kotlin + Swift glue — the native SDKs themselves are pulled in by
> the host app, so the same plugin works in any Flutter project.

---

## Install

```yaml
dependencies:
  checkout_flow:
    git:
      url: https://github.com/alfapay/checkout_flow
      ref: main
    # …or once published: checkout_flow: ^0.0.1
```

Then **complete the host-side native setup below** — the plugin will not
work without it.

---

## Host-side setup

### Android (1 line in your app's `build.gradle`)

The plugin compiles against `com.checkout:checkout-android-components:1.0.0`,
but the consumer app must also declare it so the SDK ships in the final APK.

In your **app's** `android/app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.checkout:checkout-android-components:1.0.0")
}
```

…or in groovy `build.gradle`:

```groovy
dependencies {
    implementation 'com.checkout:checkout-android-components:1.0.0'
}
```

That's it for Android. `mavenCentral()` is already declared in modern Flutter
templates' `settings.gradle`; add it manually if you're on an older one.

### iOS (add the SwiftPM package once via Xcode)

The Checkout.com Components SDK is **SPM-only** (no CocoaPods spec exists).
You add it once to your **host app's** Runner target — _not_ this plugin's
pod target.

1. Open `ios/Runner.xcworkspace` in Xcode.
2. **File → Add Package Dependencies…**
3. Paste the URL: `https://github.com/checkout/checkout-ios-components`
4. Set **Dependency Rule** → "Up to Next Major Version" → `1.8.2` (or newer).
5. Add Package → select the **`CheckoutComponents`** product → assign to the
   **Runner** target → Add Package.
6. Set the iOS deployment target on the Runner target to **15.0+**
   (Checkout.com requires it).

You don't need to touch `Podfile` or any Swift file in your project — the
plugin compiles cleanly via `#if canImport(CheckoutComponents)` guards, and
the linker resolves the package at the host level.

#### Scripted alternative

If you'd rather not click through Xcode every time you re-generate the iOS
folder (e.g. after `flutter clean`), drop this 30-line ruby script into your
project and invoke it from your iOS setup pipeline:

> See `afex_sdk_module/scripts/ios/add_swift_package.rb` in the AlfaPay SDK
> repo for a working idempotent SPM injector you can copy.

---

## Usage

```dart
import 'package:checkout_flow/checkout_flow.dart';

Future<void> startPayment() async {
  // Pass the raw response body of POST /payment-sessions through
  // verbatim — do NOT strip fields. Both native SDKs validate the
  // entire object.
  final paymentSession = <String, dynamic>{
    'id': 'ps_…',
    'amount': 1000,
    'currency': 'AED',
    // … full Checkout.com payment-session object
  };

  final result = await CheckoutFlow.launch(
    paymentSession: paymentSession,
    publicKey: 'pk_sbox_xxxxxxxxxxxxxxxxxxxxxxxxxxxx',
    environment: CheckoutFlowEnvironment.sandbox,
    locale: 'en-AE',          // optional
    title: 'Pay with card',   // optional, Android only
  );

  switch (result.status) {
    case CheckoutFlowStatus.completed:
      print('Paid: ${result.paymentId}');
    case CheckoutFlowStatus.failed:
      print('Failed: [${result.errorCode}] ${result.errorMessage}');
    case CheckoutFlowStatus.cancelled:
      print('User cancelled');
  }
}
```

### Result shape

| Field | Type | When |
|---|---|---|
| `status` | `CheckoutFlowStatus` | Always |
| `paymentId` | `String?` | On `completed` (e.g. `pay_xxx`) |
| `errorCode` | `String?` | On `failed` (e.g. `card_declined`) |
| `errorMessage` | `String?` | On `failed` |

### Environment

`CheckoutFlowEnvironment.sandbox` (default) for `pk_sbox_…` keys,
`.production` for `pk_…` keys. The native SDK rejects mismatches.

---

## Troubleshooting

### iOS — `No such module 'CheckoutComponents'`

The SwiftPM package isn't on the **Runner** target. Re-do the iOS setup
steps above. Note: `flutter pub get` regenerates `Runner.xcodeproj` and
wipes manual SPM edits — you'll need to re-add the package after every
`flutter clean`.

### Android — `Could not find com.checkout:checkout-android-components`

Either `mavenCentral()` isn't in your `settings.gradle`'s
`dependencyResolutionManagement.repositories`, or the consumer app didn't
declare the dep in its `build.gradle`. Both are required.

### `Empty response from native SDK` at runtime

The native side returned a result the Dart layer couldn't parse. Most likely
the iOS SwiftPM package is missing — the plugin's swift code falls back to a
runtime stub that returns nothing. Re-do the iOS setup.

---

## Limitations

- **iOS 15+ only** (Checkout.com requirement)
- **Android minSdk 24** (Checkout.com requirement)
- **One Flow at a time** — calling `launch` while another sheet is on screen
  returns `already_in_progress`
- **No Apple Pay / Google Pay tokenisation flows** — only the standard card
  + 3DS Flow Components. Apple Pay merchant validation has to be done in
  the host app.

---

## Versions

| Plugin | iOS SPM | Android Maven |
|---|---|---|
| `0.0.1` | `checkout-ios-components` >= 1.8.2 | `com.checkout:checkout-android-components:1.0.0` |

---

## License

MIT — see `LICENSE`.
