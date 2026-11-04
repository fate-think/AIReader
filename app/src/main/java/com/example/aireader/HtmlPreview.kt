package com.example.aireader

import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@Composable
fun HtmlPreview(html: String, fontSizePx: Int, onFontSizeChange: (Int) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val webBackground = MaterialTheme.colorScheme.background.toArgb()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .statusBarsPadding().navigationBarsPadding()) {
        Surface(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                TextButton(onClick = onClose) { Text("返回") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onFontSizeChange(fontSizePx - 1) }, enabled = fontSizePx > 14) { Text("A−") }
                Text("${fontSizePx}px", modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { onFontSizeChange(fontSizePx + 1) }, enabled = fontSizePx < 36) { Text("A+") }
            }
        }
        key(html) { AndroidView(factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(webBackground)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.loadsImagesAutomatically = false
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.isForMainFrame && request.hasGesture()) {
                            val uri = request.url
                            if (uri.scheme == "https" || uri.scheme == "http" || uri.scheme == "mailto") {
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                catch (_: Exception) { /* Stay on the preview. */ }
                            }
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.url.scheme == "data" || request.url.scheme == "about") null
                        else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        if (url != null && !url.startsWith("about:blank") && !url.startsWith("data:")) view.stopLoading()
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }, update = { /* Snapshot HTML stays fixed while this screen is open. */ },
            modifier = Modifier.weight(1f), onRelease = { it.destroy() }) }
    }
}
