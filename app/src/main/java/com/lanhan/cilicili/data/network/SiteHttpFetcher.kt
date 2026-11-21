package com.lanhan.cilicili.data.network

import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

sealed interface FetchResult {
    data class Success(
        val html: String,
        val finalUrl: String,
        val latencyMs: Long,
    ) : FetchResult

    data class RedirectToMagnet(
        val magnetUri: String,
        val latencyMs: Long,
    ) : FetchResult

    data class ChallengeRequired(
        val url: String,
        val statusCode: Int,
        val latencyMs: Long,
    ) : FetchResult

    data class HttpFailure(
        val statusCode: Int,
        val message: String,
        val latencyMs: Long,
    ) : FetchResult

    data class NetworkFailure(
        val message: String,
        val latencyMs: Long,
    ) : FetchResult
}

/**
 * Executes HTTP requests for [SiteRule] search and detail pages using [OkHttpClient],
 * injecting rule headers and any verified Cloudflare session credentials from [SiteSessionStore].
 */
class SiteHttpFetcher(
    private val clientProvider: () -> OkHttpClient,
    private val sessionStore: SiteSessionStore,
) {

    suspend fun fetch(rule: SiteRule, targetUrl: String): FetchResult = withContext(Dispatchers.IO) {
        val startNanos = System.nanoTime()
        val parsedUrl = targetUrl.toHttpUrlOrNull()
            ?: return@withContext FetchResult.NetworkFailure("无效 URL: $targetUrl", 0L)

        val requestBuilder = Request.Builder().url(parsedUrl)
        var hasUserAgent = false
        var hasAccept = false

        for ((rawKey, rawVal) in rule.headers) {
            val key = rawKey.trim()
            if (key.isEmpty()) continue
            // Skip stale Host header if searchUrl host differs or let OkHttp set Host automatically
            if (key.equals("Host", ignoreCase = true)) continue
            // Skip stale hardcoded cf_clearance cookies from static JSON if we have a fresh session
            if (key.equals("Cookie", ignoreCase = true) && sessionStore.get(parsedUrl.host) != null) {
                continue
            }
            if (key.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            if (key.equals("Accept", ignoreCase = true)) hasAccept = true
            runCatching { requestBuilder.header(key, rawVal) }
        }

        // Apply WebView-acquired CF session (both Cookie AND matching User-Agent!)
        val cfSession = sessionStore.get(parsedUrl.host)
        if (cfSession != null) {
            requestBuilder.header("Cookie", cfSession.cookieHeader)
            if (cfSession.userAgent.isNotBlank()) {
                requestBuilder.header("User-Agent", cfSession.userAgent)
                hasUserAgent = true
            }
        }

        if (!hasUserAgent) {
            requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        if (!hasAccept) {
            val defaultAccept = when (rule.sourceType) {
                SiteSourceType.PROWLARR, SiteSourceType.JSON_API -> "application/json, */*;q=0.8"
                SiteSourceType.TORZNAB, SiteSourceType.RSS -> "application/xml, application/rss+xml, text/xml;q=0.9, */*;q=0.8"
                SiteSourceType.HTML -> "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            }
            requestBuilder.header("Accept", defaultAccept)
        }
        requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

        try {
            val baseClient = clientProvider()
            val effectiveClient = if (rule.sourceType.isSelfHostedApi) {
                val minApiReadMs = 45_000L
                if (baseClient.readTimeoutMillis < minApiReadMs) {
                    baseClient.newBuilder()
                        .readTimeout(minApiReadMs, TimeUnit.MILLISECONDS)
                        .build()
                } else {
                    baseClient
                }
            } else {
                baseClient
            }
            val call = effectiveClient.newCall(requestBuilder.build())
            call.execute().use { response ->
                val latencyMs = (System.nanoTime() - startNanos) / 1_000_000L
                val location = response.header("Location")?.trim()
                if (response.code in 300..399 && location?.startsWith("magnet:", ignoreCase = true) == true) {
                    return@withContext FetchResult.RedirectToMagnet(
                        magnetUri = location,
                        latencyMs = latencyMs,
                    )
                }
                val body = decodeBody(response)
                if (isCloudflareChallenge(response, body)) {
                    return@withContext FetchResult.ChallengeRequired(
                        url = response.request.url.toString(),
                        statusCode = response.code,
                        latencyMs = latencyMs,
                    )
                }
                if (!response.isSuccessful) {
                    val detail = when (response.code) {
                        401, 403 -> if (rule.sourceType.isSelfHostedApi) "鉴权失败 (HTTP ${response.code})" else "HTTP ${response.code}"
                        else -> "HTTP ${response.code}"
                    }
                    return@withContext FetchResult.HttpFailure(
                        statusCode = response.code,
                        message = detail,
                        latencyMs = latencyMs,
                    )
                }
                FetchResult.Success(
                    html = body,
                    finalUrl = response.request.url.toString(),
                    latencyMs = latencyMs,
                )
            }
        } catch (e: IOException) {
            val latencyMs = (System.nanoTime() - startNanos) / 1_000_000L
            FetchResult.NetworkFailure(
                message = simplifyNetworkError(e),
                latencyMs = latencyMs,
            )
        } catch (e: Exception) {
            val latencyMs = (System.nanoTime() - startNanos) / 1_000_000L
            FetchResult.NetworkFailure(
                message = e.message ?: "请求异常",
                latencyMs = latencyMs,
            )
        }
    }

    private fun decodeBody(response: Response): String {
        val rawBody = response.body ?: return ""
        val bytes = rawBody.bytes()
        if (bytes.isEmpty()) return ""
        val headerCharset = response.body?.contentType()?.charset()
        if (headerCharset != null) {
            return String(bytes, headerCharset)
        }
        // Sniff <meta charset="..."> from the first 2 KB before decoding full document
        val preview = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val metaCharset = META_CHARSET_REGEX.find(preview)?.groupValues?.getOrNull(1)
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
        return String(bytes, metaCharset ?: Charsets.UTF_8)
    }

    internal fun isCloudflareChallenge(response: Response, body: String): Boolean {
        val code = response.code
        if (code != 403 && code != 503 && code != 429) return false
        val cfMitigated = response.header("cf-mitigated")
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true
        return body.contains("Just a moment...", ignoreCase = true) ||
            body.contains("challenge-platform", ignoreCase = true) ||
            body.contains("__cf_chl_", ignoreCase = true) ||
            body.contains("Enable JavaScript and cookies to continue", ignoreCase = true)
    }

    private fun simplifyNetworkError(e: IOException): String {
        val msg = e.message.orEmpty()
        return when {
            e is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) -> "连接超时"
            e is java.net.UnknownHostException -> "域名无法解析"
            e is javax.net.ssl.SSLException -> "SSL 握手失败"
            e is java.net.ConnectException -> "拒绝连接/端口不可达"
            else -> msg.ifBlank { e.javaClass.simpleName }
        }
    }

    companion object {
        private val META_CHARSET_REGEX = Regex("(?i)charset=[\"']?([a-zA-Z0-9\\-_]+)")
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    }
}
