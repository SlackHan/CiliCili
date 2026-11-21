package com.lanhan.cilicili.data.network

import com.lanhan.cilicili.domain.model.DohProvider
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Builds [OkHttpClient] instances with configurable timeout and optional DoH resolution.
 */
object HttpClientFactory {

    private val PRIVATE_HOST_SUFFIXES = listOf(".ts.net", ".local", ".lan", ".internal", ".arpa")
    private val IPV4_LITERAL_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun create(
        dohProvider: DohProvider = DohProvider.SYSTEM,
        timeoutSeconds: Int = 12,
    ): OkHttpClient {
        val timeout = timeoutSeconds.coerceIn(5, 60).toLong()
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        if (dohProvider == DohProvider.SYSTEM || dohProvider.url.isBlank()) {
            return bootstrapClient
        }

        val bootstrapHosts = dohProvider.bootstrapIps.mapNotNull { ip ->
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }

        val dohDns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(dohProvider.url.toHttpUrl())
            .bootstrapDnsHosts(bootstrapHosts)
            .includeIPv6(false)
            .build()

        val hybridDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val lower = hostname.trim().lowercase()
                val isPrivateOrLiteral = lower == "localhost" ||
                    IPV4_LITERAL_REGEX.matches(lower) ||
                    lower.contains(':') ||
                    PRIVATE_HOST_SUFFIXES.any { lower.endsWith(it) }
                return if (isPrivateOrLiteral) {
                    Dns.SYSTEM.lookup(hostname)
                } else {
                    dohDns.lookup(hostname)
                }
            }
        }

        return bootstrapClient.newBuilder()
            .dns(hybridDns)
            .build()
    }
}
