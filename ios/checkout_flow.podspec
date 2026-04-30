#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint checkout_flow.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'checkout_flow'
  s.version          = '0.0.1'
  s.summary          = 'Flutter bridge for Checkout.com native Flow Components SDK.'
  s.description      = <<-DESC
    Wraps Checkout.com's native iOS Flow Components SDK behind a Dart
    MethodChannel. Drop-in pluggable into any Flutter app.
  DESC
  s.homepage         = 'https://github.com/alfapay/checkout_flow'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'AlfaPay' => 'engineering@alfapay.ae' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*.{h,m,swift}'
  s.public_header_files = 'Classes/**/*.h'
  s.dependency 'Flutter'

  # Checkout.com mobile Flow Components SDK is distributed via Swift
  # Package Manager only — not CocoaPods. The host app MUST add it as a
  # SwiftPM dependency on the Runner target (see README.md).
  #
  # We don't declare it here because `pod install` cannot resolve a
  # non-CocoaPods spec. The Swift sources use `#if canImport(...)` guards
  # so the plugin compiles cleanly even when the host hasn't added the
  # SwiftPM package yet (the runtime returns a clear `not_initialised`
  # error pointing at the README in that case).
  #
  # CheckoutComponents 1.x requires iOS 15+ (per Checkout.com docs).
  s.platform     = :ios, '15.0'
  s.swift_version = '5.9'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
