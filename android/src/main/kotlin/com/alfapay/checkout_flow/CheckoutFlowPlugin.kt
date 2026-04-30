package com.alfapay.checkout_flow

import android.app.Activity
import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener

/**
 * Flutter plugin entry. Registers a single MethodChannel and forwards
 * `launchFlow` calls to [CheckoutFlowActivity], which hosts the
 * Checkout.com Components SDK.
 *
 * The activity returns its outcome via `onActivityResult`; we hand the
 * result back to Dart by completing the pending [Result].
 */
class CheckoutFlowPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    ActivityResultListener {

    companion object {
        private const val CHANNEL_NAME = "com.alfapay/checkout_flow"
        private const val REQUEST_CODE = 0xC4E0
    }

    private var channel: MethodChannel? = null
    private var activity: Activity? = null
    private var pendingResult: Result? = null

    // ── FlutterPlugin ──────────────────────────────────────────────────

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME).apply {
            setMethodCallHandler(this@CheckoutFlowPlugin)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        channel = null
    }

    // ── ActivityAware ──────────────────────────────────────────────────

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    // ── MethodCallHandler ──────────────────────────────────────────────

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "launchFlow" -> launchFlow(call, result)
            else -> result.notImplemented()
        }
    }

    private fun launchFlow(call: MethodCall, result: Result) {
        val activity = this.activity ?: run {
            result.error(
                "no_activity",
                "checkout_flow requires an attached Activity to launch the Flow screen.",
                null,
            )
            return
        }
        if (pendingResult != null) {
            result.error(
                "already_in_progress",
                "Another Checkout Flow is already on screen.",
                null,
            )
            return
        }

        @Suppress("UNCHECKED_CAST")
        val paymentSession = call.argument<Map<String, Any?>>("paymentSession")
        val publicKey = call.argument<String>("publicKey")
        val environment = call.argument<String>("environment") ?: "sandbox"
        val locale = call.argument<String?>("locale")
        val title = call.argument<String?>("title")

        if (paymentSession == null || publicKey.isNullOrBlank()) {
            result.error(
                "invalid_args",
                "paymentSession and publicKey are required.",
                null,
            )
            return
        }

        pendingResult = result
        val intent = CheckoutFlowActivity.intent(
            activity,
            paymentSession = paymentSession,
            publicKey = publicKey,
            environment = environment,
            locale = locale,
            title = title,
        )
        activity.startActivityForResult(intent, REQUEST_CODE)
    }

    // ── ActivityResultListener ─────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val result = pendingResult ?: return true
        pendingResult = null

        val payload = CheckoutFlowActivity.parseResult(resultCode, data)
        when (payload.status) {
            "cancelled" -> result.error("cancelled", "User cancelled", null)
            else -> result.success(payload.toMap())
        }
        return true
    }
}
