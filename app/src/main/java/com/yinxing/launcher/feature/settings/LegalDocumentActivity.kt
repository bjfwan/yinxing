package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.yinxing.launcher.R
import com.yinxing.launcher.common.ui.FontScaleActivity
import kotlin.math.roundToInt

class LegalDocumentActivity : FontScaleActivity() {
    private lateinit var webView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var errorGroup: View
    private lateinit var document: LegalDocument
    private var mainFrameFailed = false
    private var requestedUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        document = LegalDocument.from(intent.getStringExtra(EXTRA_DOCUMENT)) ?: run {
            finish()
            return
        }

        setContentView(R.layout.activity_legal_document)
        webView = findViewById(R.id.legal_document_web_view)
        loading = findViewById(R.id.legal_document_loading)
        errorGroup = findViewById(R.id.legal_document_error)

        findViewById<TextView>(R.id.legal_document_title).setText(document.titleRes)
        findViewById<View>(R.id.legal_document_back).setOnClickListener { navigateBack() }
        findViewById<View>(R.id.legal_document_retry).setOnClickListener {
            loadDocument(requestedUrl.ifBlank { document.url })
        }
        onBackPressedDispatcher.addCallback(this) { navigateBack() }

        applySystemInsets()
        configureWebView()
        val restored = savedInstanceState?.let(webView::restoreState)
        if (restored == null) loadDocument(document.url)
    }

    @Suppress("DEPRECATION")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            textZoom = (resources.configuration.fontScale * 100).roundToInt().coerceIn(50, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.setBackgroundColor(getColor(R.color.launcher_surface))
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleNavigation(request.url)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleNavigation(Uri.parse(url))
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                mainFrameFailed = false
                requestedUrl = url
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!mainFrameFailed) showDocument()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showError()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) showError()
            }
        }
    }

    private fun handleNavigation(uri: Uri): Boolean {
        if (uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(LEGAL_DOCUMENT_HOST, ignoreCase = true)
        ) {
            return false
        }
        if (runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isFailure) {
            Toast.makeText(this, R.string.settings_about_open_failed, Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun loadDocument(url: String) {
        requestedUrl = url
        mainFrameFailed = false
        showLoading()
        webView.loadUrl(url)
    }

    private fun showLoading() {
        loading.visibility = View.VISIBLE
        errorGroup.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showDocument() {
        loading.visibility = View.GONE
        errorGroup.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showError() {
        mainFrameFailed = true
        loading.visibility = View.GONE
        webView.visibility = View.INVISIBLE
        errorGroup.visibility = View.VISIBLE
    }

    private fun navigateBack() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.legal_document_root)) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        internal const val EXTRA_DOCUMENT = "legal_document_kind"

        internal fun createIntent(context: Context, document: LegalDocument): Intent =
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(EXTRA_DOCUMENT, document.value)
    }
}

internal enum class LegalDocument(
    val value: String,
    @param:StringRes val titleRes: Int,
    val url: String
) {
    PRIVACY("privacy", R.string.settings_about_privacy_title, "https://yinxing.722688.xyz/privacy"),
    TERMS("terms", R.string.settings_about_terms_title, "https://yinxing.722688.xyz/terms");

    companion object {
        fun from(value: String?): LegalDocument? = entries.firstOrNull { it.value == value }
    }
}

private const val LEGAL_DOCUMENT_HOST = "yinxing.722688.xyz"
