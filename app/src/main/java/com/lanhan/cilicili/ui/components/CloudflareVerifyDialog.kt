package com.lanhan.cilicili.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Sandboxed on-demand WebView modal for solving Cloudflare / anti-bot JS challenges.
 * Blocks popups and third-party ad redirects, and extracts `cf_clearance` + exact WebView UA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareVerifyDialog(
    siteName: String,
    targetUrl: String,
    onVerified: (host: String, cookieHeader: String, userAgent: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val targetHost = remember(targetUrl) {
        runCatching { Uri.parse(targetUrl).host.orEmpty().lowercase() }.getOrDefault("")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusTip by remember { mutableStateOf("正在加载验证页，如出现人机校验请点击完成...") }
    var alreadyCaptured by remember { mutableStateOf(false) }

    fun tryCaptureSession(manual: Boolean = false) {
        if (alreadyCaptured) return
        val wv = webViewRef ?: return
        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie(targetUrl).orEmpty()
        val hasClearance = cookie.contains("cf_clearance=", ignoreCase = true)
        val title = wv.title.orEmpty()
        val isStillChallenge = title.contains("Just a moment", ignoreCase = true)

        if (hasClearance && !isStillChallenge) {
            alreadyCaptured = true
            onVerified(targetHost, cookie, wv.settings.userAgentString)
        } else if (manual && cookie.isNotBlank()) {
            alreadyCaptured = true
            onVerified(targetHost, cookie, wv.settings.userAgentString)
        }
    }

    LaunchedEffect(targetUrl) {
        while (!alreadyCaptured) {
            delay(700L)
            tryCaptureSession(manual = false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "过盾验证 · $siteName",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = targetHost,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                    },
                    actions = {
                        Button(
                            onClick = { tryCaptureSession(manual = true) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("完成并重试")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusTip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        CookieManager.getInstance().setAcceptCookie(true)
                        WebView(ctx).apply {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url ?: return true
                                    val scheme = url.scheme?.lowercase() ?: return true
                                    if (scheme != "http" && scheme != "https") return true
                                    val host = url.host?.lowercase().orEmpty()
                                    val allowed = isAllowedChallengeHost(host, targetHost)
                                    return !allowed
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    val cookie = CookieManager.getInstance().getCookie(targetUrl).orEmpty()
                                    if (cookie.contains("cf_clearance=", ignoreCase = true)) {
                                        statusTip = "已检测到验证凭证，正在自动提取..."
                                    } else {
                                        statusTip = "若页面已正常显示内容，可点击右上角「完成并重试」"
                                    }
                                    tryCaptureSession(manual = false)
                                }
                            }
                            webViewRef = this
                            loadUrl(targetUrl)
                        }
                    },
                )
            }
        }
    }
}

private fun isAllowedChallengeHost(host: String, targetHost: String): Boolean {
    if (host.isEmpty() || targetHost.isEmpty()) return false
    if (host == targetHost || host.endsWith(".$targetHost")) return true
    if (host == "challenges.cloudflare.com" || host.endsWith(".cloudflare.com")) return true
    // Also allow same apex domain (e.g. www.example.com <-> example.com)
    val targetApex = targetHost.split('.').takeLast(2).joinToString(".")
    return host == targetApex || host.endsWith(".$targetApex")
}
