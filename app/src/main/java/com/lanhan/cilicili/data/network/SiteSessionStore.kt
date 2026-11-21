package com.lanhan.cilicili.data.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class HostSessionCredential(
    val host: String,
    val cookieHeader: String,
    val userAgent: String,
    val updatedAt: Long,
)

/**
 * Stores per-host Cloudflare clearance cookies paired with the exact WebView User-Agent
 * that solved the challenge (required because CF binds `cf_clearance` to IP + User-Agent).
 */
class SiteSessionStore(context: Context? = null) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context?.getSharedPreferences("cf_sessions", Context.MODE_PRIVATE)
    private val cache = ConcurrentHashMap<String, HostSessionCredential>()
    private val _hostsFlow = MutableStateFlow<Set<String>>(emptySet())
    val verifiedHostsFlow: StateFlow<Set<String>> = _hostsFlow.asStateFlow()

    init {
        prefs?.all?.forEach { (key, value) ->
            val raw = value as? String ?: return@forEach
            runCatching { json.decodeFromString<HostSessionCredential>(raw) }
                .getOrNull()
                ?.let { cache[key.lowercase()] = it }
        }
        _hostsFlow.value = cache.keys.toSet()
    }

    fun get(host: String): HostSessionCredential? {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return null
        return cache[normalized]
    }

    fun save(host: String, cookieHeader: String, userAgent: String) {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty() || cookieHeader.isBlank()) return
        val credential = HostSessionCredential(
            host = normalized,
            cookieHeader = cookieHeader.trim(),
            userAgent = userAgent.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        cache[normalized] = credential
        prefs?.edit()?.putString(normalized, json.encodeToString(credential))?.apply()
        _hostsFlow.value = cache.keys.toSet()
    }

    fun clear(host: String) {
        val normalized = normalizeHost(host)
        cache.remove(normalized)
        prefs?.edit()?.remove(normalized)?.apply()
        _hostsFlow.value = cache.keys.toSet()
    }

    fun clearAll() {
        cache.clear()
        prefs?.edit()?.clear()?.apply()
        _hostsFlow.value = emptySet()
    }

    private fun normalizeHost(host: String): String = host.trim().lowercase()
}
