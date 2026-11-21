package com.lanhan.cilicili.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetNormalizerTest {

    @Test
    fun `normalizes uppercase hex magnet preserving original uri`() {
        val raw = "magnet:?xt=urn:btih:ABCDEF0123456789ABCDEF0123456789ABCDEF01&dn=Ubuntu"
        val res = MagnetNormalizer.normalize(raw)
        assertNotNull(res)
        assertEquals("abcdef0123456789abcdef0123456789abcdef01", res!!.infoHash)
        assertEquals(raw, res.magnetUri)
    }

    @Test
    fun `converts base32 btih to lowercase 40-char hex`() {
        // 32-char Base32 of 20 bytes (0xda, 0x39, ...)
        val raw = "magnet:?xt=urn:btih:3I42H3S6NNFQ2MSVX7XZKYAYSCX5QBYJ"
        val res = MagnetNormalizer.normalize(raw)
        assertNotNull(res)
        assertEquals(40, res!!.infoHash.length)
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", res.infoHash)
    }

    @Test
    fun `builds magnet uri from slash hash path`() {
        val path = "/hash/0123456789abcdef0123456789abcdef01234567.html"
        val res = MagnetNormalizer.normalize(path, "Ubuntu 24.04 LTS")
        assertNotNull(res)
        assertEquals("0123456789abcdef0123456789abcdef01234567", res!!.infoHash)
        assertTrue(res.magnetUri.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
        assertTrue(res.magnetUri.contains("&dn=Ubuntu%2024.04%20LTS"))
    }

    @Test
    fun `returns null when no hash or magnet is present`() {
        assertNull(MagnetNormalizer.normalize("/detail/12345.html", "Test"))
        assertNull(MagnetNormalizer.normalize("   "))
        assertNull(MagnetNormalizer.normalize(null))
    }
}
