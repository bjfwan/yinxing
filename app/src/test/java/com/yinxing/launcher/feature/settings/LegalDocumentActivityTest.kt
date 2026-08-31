package com.yinxing.launcher.feature.settings

import android.content.Context
import android.content.Intent
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.yinxing.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LegalDocumentActivityTest {
    @Test
    fun privacyDocumentLoadsInsideRestrictedWebView() {
        val activity = buildLegalActivity(LegalDocument.PRIVACY)
        val webView = activity.findViewById<WebView>(R.id.legal_document_web_view)

        assertEquals("隐私政策", activity.findViewById<TextView>(R.id.legal_document_title).text.toString())
        assertEquals("https://yinxing.722688.xyz/privacy", shadowOf(webView).lastLoadedUrl)
        assertFalse(webView.settings.javaScriptEnabled)
        assertFalse(webView.settings.allowFileAccess)
        assertFalse(webView.settings.allowContentAccess)
    }

    @Test
    fun termsDocumentUsesTheTermsUrl() {
        val activity = buildLegalActivity(LegalDocument.TERMS)
        val webView = activity.findViewById<WebView>(R.id.legal_document_web_view)

        assertEquals("服务条款", activity.findViewById<TextView>(R.id.legal_document_title).text.toString())
        assertEquals("https://yinxing.722688.xyz/terms", shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun invalidDocumentFinishesWithoutDestroyCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildActivity(
            LegalDocumentActivity::class.java,
            Intent(context, LegalDocumentActivity::class.java)
                .putExtra(LegalDocumentActivity.EXTRA_DOCUMENT, "unknown")
        ).setup()

        assertTrue(controller.get().isFinishing)
        controller.destroy()
    }

    private fun buildLegalActivity(document: LegalDocument): LegalDocumentActivity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Robolectric.buildActivity(
            LegalDocumentActivity::class.java,
            LegalDocumentActivity.createIntent(context, document)
        ).setup().get()
    }
}
