package com.alfapay.checkout_flow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.checkout.components.core.CheckoutComponentsFactory
import com.checkout.components.interfaces.Environment
import com.checkout.components.interfaces.api.CheckoutComponents
import com.checkout.components.interfaces.api.PaymentMethodComponent
import com.checkout.components.interfaces.component.CheckoutComponentConfiguration
import com.checkout.components.interfaces.component.ComponentCallback
import com.checkout.components.interfaces.component.FlowCoordinator
import com.checkout.components.interfaces.error.CheckoutError
import com.checkout.components.interfaces.model.ComponentName
import com.checkout.components.interfaces.model.PaymentMethodName
import com.checkout.components.interfaces.model.PaymentSessionResponse
import com.checkout.components.wallet.wrapper.GooglePayFlowCoordinator
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Hosts the native Checkout.com Flow component. Returns the user's outcome
 * to the caller via `setResult` → [CheckoutFlowPlugin.onActivityResult].
 *
 * Wired against `com.checkout:checkout-android-components:1.0.0+`. The
 * SDK is an `implementation` dep at the plugin level and consumer apps
 * must additionally declare it (or `mavenCentral()` + `JitPack` repos
 * for the transitive Risk SDK + Fingerprint Pro) in their settings.
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

    private var components: CheckoutComponents? = null
    private var finished = false

    private lateinit var flowContainer: FrameLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 (API 35) draws apps edge-to-edge by default — pad
        // the root view by status bar / nav bar / cutout insets so the
        // close button isn't hidden under the status bar.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(buildContentView())

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        lifecycleScope.launch {
            try {
                startFlow()
            } catch (t: Throwable) {
                android.util.Log.e(
                    "checkout_flow",
                    "startFlow() failed: ${t.javaClass.name}: ${t.message}",
                    t,
                )
                finishWith(
                    ResultPayload(
                        status = "failed",
                        errorCode = "init_failed",
                        errorMessage = t.message ?: "Failed to initialise Checkout SDK",
                    ),
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWith(ResultPayload("cancelled"))
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val toolbar = Toolbar(this).apply {
            title = intent.getStringExtra(EXTRA_TITLE) ?: "Payment"
            setTitleTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationContentDescription(android.R.string.cancel)
            setNavigationOnClickListener {
                finishWith(ResultPayload("cancelled"))
            }
            elevation = resources.displayMetrics.density * 2f
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
            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.gravity = android.view.Gravity.CENTER
            layoutParams = params
        }
        flowContainer.addView(progressBar)
        root.addView(toolbar)
        root.addView(flowContainer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        return root
    }

    private suspend fun startFlow() {
        val sessionJson = intent.getStringExtra(EXTRA_PAYMENT_SESSION)
            ?: throw IllegalStateException("Missing payment session payload")
        val publicKey = intent.getStringExtra(EXTRA_PUBLIC_KEY)
            ?: throw IllegalStateException("Missing public key")
        val envName = intent.getStringExtra(EXTRA_ENVIRONMENT) ?: "sandbox"

        val environment = if (envName.equals("production", ignoreCase = true)) {
            Environment.PRODUCTION
        } else {
            Environment.SANDBOX
        }

        // Both spellings (snake/camel + short alias) come over from Dart;
        // the SDK requires id + secret + token to skip the device-API
        // hydration call (which 401s for some merchant configs).
        val sessionObj = JSONObject(sessionJson)
        val sessionId = sessionObj.optString("id")
        val sessionSecret = sessionObj.optString("secret")
        val sessionToken = sessionObj.optString("token")

        require(sessionId.isNotEmpty() && sessionSecret.isNotEmpty()) {
            "payment_session_json must contain 'id' and 'secret'"
        }
        require(sessionToken.isNotEmpty()) {
            "payment_session_json must contain 'token' (alias of payment_session_token)"
        }

        val callback = ComponentCallback(
            onReady = {
                progressBar.visibility = View.GONE
            },
            onChange = { },
            onSubmit = { },
            onSuccess = { _: PaymentMethodComponent, paymentId: String ->
                finishWith(
                    ResultPayload(
                        status = "completed",
                        paymentId = paymentId,
                    ),
                )
            },
            onError = { _: PaymentMethodComponent, error: CheckoutError ->
                android.util.Log.e(
                    "checkout_flow",
                    "Checkout onError: code=${error.code} | message=${error.message}",
                )
                finishWith(
                    ResultPayload(
                        status = "failed",
                        errorCode = error.code.name,
                        errorMessage = "${error.code.name}: ${error.message}",
                    ),
                )
            },
            onTokenized = { },
            // Returning `true` tells the SDK to process the Pay-button tap
            // itself (tokenize → call payment-session → 3DS → invoke
            // onSuccess / onError). With `false` the SDK expects the
            // integrator to call `flow.submit()` manually — which we
            // don't, so the button does nothing visible to the user.
            handleTap = { true },
        )

        // Flow coordinators wire in alternative payment methods that
        // need extra setup beyond the standard card form. Today: Google
        // Pay. (Apple Pay isn't relevant on Android.) The Checkout SDK
        // throws "GooglePayFlowCoordinator is required" if Google Pay
        // is enabled in the payment session and we don't pass one,
        // even if the user only intends to pay with a card. Wiring it
        // unconditionally is harmless — the SDK only invokes it when
        // Google Pay is actually picked.
        val flowCoordinators: Map<PaymentMethodName, FlowCoordinator> = mapOf(
            PaymentMethodName.GooglePay to GooglePayFlowCoordinator(
                this,
            ) { _, _ ->
                // The SDK consumes the payment-data callback internally
                // (status code + tokenized payment data string). We
                // don't need custom handling here — the umbrella
                // `componentCallback.onSuccess` / `onError` still fires
                // with the final payment outcome.
            },
        )

        val configuration = CheckoutComponentConfiguration(
            context = this,
            publicKey = publicKey,
            environment = environment,
            paymentSession = PaymentSessionResponse(
                id = sessionId,
                paymentSessionToken = sessionToken,
                paymentSessionSecret = sessionSecret,
            ),
            componentOptions = emptyMap(),
            flowCoordinators = flowCoordinators,
            locale = null,
            translations = null,
            appearance = null,
            componentCallback = callback,
        )

        val components = CheckoutComponentsFactory(configuration).create()
        this.components = components

        val flow: PaymentMethodComponent = components.create(ComponentName.Flow)
        val flowView = flow.provideView(flowContainer)
        flowContainer.addView(flowView)
    }

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
