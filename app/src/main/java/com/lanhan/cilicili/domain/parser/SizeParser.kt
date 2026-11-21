package com.lanhan.cilicili.domain.parser

/**
 * Parses human-readable file sizes (e.g. `1.45 GB`, `680MB`, `2.3 GiB`) into bytes for sorting and filtering.
 */
object SizeParser {

    private val SIZE_REGEX = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(tb|tib|gb|gib|mb|mib|kb|kib|b|t|g|m|k)\\b")

    /**
     * Returns file size in bytes, or `-1L` when [raw] does not contain a recognizable size.
     */
    fun parseBytes(raw: String?): Long {
        if (raw.isNullOrBlank()) return -1L
        val match = SIZE_REGEX.find(raw.replace(",", "")) ?: return -1L
        val number = match.groupValues[1].toDoubleOrNull() ?: return -1L
        val multiplier = when (match.groupValues[2].lowercase()) {
            "b" -> 1.0
            "k", "kb", "kib" -> 1024.0
            "m", "mb", "mib" -> 1024.0 * 1024.0
            "g", "gb", "gib" -> 1024.0 * 1024.0 * 1024.0
            "t", "tb", "tib" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> return -1L
        }
        return (number * multiplier).toLong().coerceAtLeast(0L)
    }

    /**
     * Cleans noisy size prefixes/whitespace returned by some scrapers while preserving readable unit text.
     */
    fun cleanSizeLabel(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        val match = SIZE_REGEX.find(text)
        return match?.let { "${it.groupValues[1]} ${it.groupValues[2].uppercase()}" } ?: text
    }

    /**
     * Formats a byte count into a concise human-readable label (e.g. `4.50 GB`, `680.2 MB`).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        return when {
            bytes >= tb -> String.format(java.util.Locale.US, "%.2f TB", bytes / tb)
            bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
