package com.alfapay.checkout_flow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.json.JSONObject

/**
 * Hosts the native Checkout.com Flow component. Returns the user's outcome
 * (completed / failed / cancelled) to the caller via `setResult` →
 * [CheckoutFlowPlugin.onActivityResult].
 *
 * ─────────────────────────────────────────────────────────────────────────
 * TODO — wire the actual `CheckoutComponentsFactory` API
 * ─────────────────────────────────────────────────────────────────────────
 * The published `com.checkout:checkout-android-components:1.0.0` SDK
 * exposes a different API surface than the version this file was originally
 * drafted against:
 *
 *   • Entry class    : `com.checkout.components.core.CheckoutComponentsFactory`
 *   • Configuration  : (see Checkout.com Android docs — likely
 *                       `CheckoutComponentsConfiguration` builder)
 *   • Environment    : likely under `com.checkout.components.core` or
 *                       a sub-package
 *
 * Until those calls are wired, [startFlow] returns a `not_implemented`
 * failure to the Dart layer. The Activity scaffolding (toolbar, container,
 * back-press as cancel, result plumbing) is correct and reusable — only
 * the Checkout SDK invocation in [startFlow] needs filling in.
 * ─────────────────────────────────────────────────────────────────────────
 */
class CheckoutFlowActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PAYMENT_SESSION = "payment_session_json"
        private const val EXTRA_PUBLIC_KEY = "public_key"
        private const val EXTRA_ENVIRONMENT = "environment"
        private const val EXTRA_LOCALE = "locale"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_RESULT = "checkout_flow_result"

        fun intent(
            context: Context,
            paymentSession: Map<String, Any?>,
            publicKey: String,
            environment: String,
            locale: String?,
            title: String?,
        ): Intent =
            Intent(context, CheckoutFlowActivity::class.java).apply {
                putExtra(EXTRA_PAYMENT_SESSION, JSONObject(paymentSession).toString())
                putExtra(EXTRA_PUBLIC_KEY, publicKey)
                putExtra(EXTRA_ENVIRONMENT, environment)
                putExtra(EXTRA_LOCALE, locale)
                putExtra(EXTRA_TITLE, title)
            }

        /** Translate `setResult` payload into a serialisable channel response. */
        fun parseResult(resultCode: Int, data: Intent?): ResultPayload {
            if (resultCode != Activity.RESULT_OK || data == null) {
                return ResultPayload(status = "cancelled")
            }
            val raw = data.getStringExtra(EXTRA_RESULT) ?: return ResultPayload("cancelled")
            return try {
                val obj = JSONObject(raw)
                ResultPayload(
                    status = obj.optString("status", "failed"),
                    paymentId = obj.optStringOrNull("paymentId"),
                    errorCode = obj.optStringOrNull("errorCode"),
                    errorMessage = obj.optStringOrNull("errorMessage"),
                )
            } catch (_: Throwable) {
                ResultPayload("failed", errorMessage = "Unparseable native result")
            }
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key) || !has(key)) null else optString(key, null)
    }

    data class ResultPayload(
        val status: String,
        val paymentId: String? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "status" to status,
            "paymentId" to paymentId,
            "errorCode" to errorCode,
            "errorMessage" to errorMessage,
        )
    }

    private var finished = false

    private lateinit var flowContainer: FrameLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())

        try {
            startFlow()
        } catch (t: Throwable) {
            finishWith(
                ResultPayload(
                    status = "failed",
                    errorCode = "init_failed",
                    errorMessage = t.message ?: "Failed to initialise Checkout SDK",
                ),
            )
        }
    }

    @Deprecated("Treat system back as explicit cancel")
    override fun onBackPressed() {
        finishWith(ResultPayload("cancelled"))
        super.onBackPressed()
    }

    // ── UI scaffolding ────────────────────────────────────────────────

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val toolbar = Toolbar(this).apply {
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Payment"
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationOnClickListener {
                finishWith(ResultPayload("cancelled"))
            }
        }
        flowContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
        }
        flowContainer.addView(progressBar)
        flowContainer.addView(
            TextView(this).apply {
                text =
                    "Checkout Flow not yet wired against SDK 1.0.0.\n" +
                    "See CheckoutFlowActivity.kt TODO."
                gravity = Gravity.CENTER
                setPadding(48, 0, 48, 0)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER }
            },
        )
        root.addView(toolbar)
        root.addView(flowContainer)
        return root
    }

    // ── Checkout SDK wiring (TODO: implement against 1.0.0 API) ───────

    private fun startFlow() {
        // Read the inputs so the wiring will be ready when the SDK call
        // is filled in. Currently they're only validated.
        intent.getStringExtra(EXTRA_PAYMENT_SESSION)
            ?: throw IllegalStateException("Missing payment session payload")
        intent.getStringExtra(EXTRA_PUBLIC_KEY)
            ?: throw IllegalStateException("Missing public key")
        intent.getStringExtra(EXTRA_ENVIRONMENT) ?: "sandbox"
        intent.getStringExtra(EXTRA_LOCALE)

        // Stub: report not_implemented so the Dart layer surfaces a clear
        // error instead of hanging. Replace this body with the actual
        // CheckoutComponentsFactory.create(...).render(flowContainer)
        // call once the 1.0.0 API is mapped.
        progressBar.visibility = View.GONE
        finishWith(
            ResultPayload(
                status = "failed",
                errorCode = "not_implemented",
                errorMessage =
                    "Android Checkout Flow not yet wired against SDK 1.0.0 — " +
                        "see CheckoutFlowActivity.kt TODO",
            ),
        )
    }

    // ── Result plumbing ───────────────────────────────────────────────

    private fun finishWith(payload: ResultPayload) {
        if (finished) return
        finished = true

        val data = Intent().putExtra(
            EXTRA_RESULT,
            JSONObject().apply {
                put("status", payload.status)
                payload.paymentId?.let { put("paymentId", it) }
                payload.errorCode?.let { put("errorCode", it) }
                payload.errorMessage?.let { put("errorMessage", it) }
            }.toString(),
        )
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
