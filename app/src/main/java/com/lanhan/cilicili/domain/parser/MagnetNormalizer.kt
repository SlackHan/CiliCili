package com.lanhan.cilicili.domain.parser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class NormalizedMagnet(
    val infoHash: String,
    val magnetUri: String,
)

/**
 * Extracts and normalizes BitTorrent InfoHash (SHA-1 40-char hex or Base32) and magnet URIs
 * from raw links, `/hash/<hex>` paths, or arbitrary detail-page HTML fragments.
 */
object MagnetNormalizer {

    private val MAGNET_URI_REGEX = Regex("(?i)magnet:\\?xt=urn:btih:[a-z0-9]{32,40}[^\\s\"'<>]*")
    private val BTIH_HEX_REGEX = Regex("(?i)urn:btih:([a-f0-9]{40})(?![a-f0-9])")
    private val BTIH_BASE32_REGEX = Regex("(?i)urn:btih:([a-z2-7]{32})(?![a-z0-9])")
    private val STANDALONE_HEX40_REGEX = Regex("(?i)(?<![a-f0-9])([a-f0-9]{40})(?![a-f0-9])")

    /**
     * Normalizes a candidate string (magnet URI, `/hash/<hex>`, or page URL) into a
     * canonical lowercase 40-char hex [infoHash] and playable [magnetUri].
     */
    fun normalize(raw: String?, title: String? = null): NormalizedMagnet? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val matchedMagnet = MAGNET_URI_REGEX.find(trimmed)?.value
        val candidate = matchedMagnet ?: trimmed

        val hexHash = extractHexInfoHash(candidate) ?: return null
        val magnetUri = if (candidate.startsWith("magnet:?", ignoreCase = true)) {
            candidate
        } else {
            buildMagnetUri(hexHash, title)
        }
        return NormalizedMagnet(infoHash = hexHash, magnetUri = magnetUri)
    }

    /**
     * Extracts a lowercase 40-character hex InfoHash from [input], converting Base32 if needed.
     */
    fun extractHexInfoHash(input: String): String? {
        BTIH_HEX_REGEX.find(input)?.groupValues?.getOrNull(1)?.let {
            return it.lowercase()
        }
        BTIH_BASE32_REGEX.find(input)?.groupValues?.getOrNull(1)?.let { base32 ->
            decodeBase32ToHex(base32)?.let { return it }
        }
        STANDALONE_HEX40_REGEX.find(input)?.groupValues?.getOrNull(1)?.let {
            return it.lowercase()
        }
        return null
    }

    /**
     * Builds a standard `magnet:?xt=urn:btih:...` URI with an optional URL-encoded display name.
     */
    fun buildMagnetUri(hexInfoHash: String, title: String? = null): String {
        val cleanHash = hexInfoHash.trim().lowercase()
        val encodedName = title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { "&dn=${URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")}" }
            .orEmpty()
        return "magnet:?xt=urn:btih:$cleanHash$encodedName"
    }

    private fun decodeBase32ToHex(input: String): String? {
        val clean = input.trim().uppercase()
        if (clean.length != 32) return null
        val out = ByteArray(20)
        var buffer = 0
        var bitsLeft = 0
        var outIndex = 0
        for (ch in clean) {
            val value = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in '2'..'7' -> ch - '2' + 26
                else -> return null
            }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (outIndex >= out.size) return null
                out[outIndex++] = ((buffer shr bitsLeft) and 0xFF).toByte()
            }
        }
        if (outIndex != out.size) return null
        return out.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
