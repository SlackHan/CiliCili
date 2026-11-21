package com.lanhan.cilicili.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeParserTest {

    @Test
    fun `parses gb mb kb and commas accurately`() {
        assertEquals(1024L * 1024L * 1024L, SizeParser.parseBytes("1 GB"))
        assertEquals((1.5 * 1024 * 1024 * 1024).toLong(), SizeParser.parseBytes("1.50 GiB"))
        assertEquals(1024L * 1024L * 1024L, SizeParser.parseBytes("1,024 MB"))
        assertEquals(512L * 1024L, SizeParser.parseBytes("512KB"))
        assertEquals(-1L, SizeParser.parseBytes("未知大小"))
    }

    @Test
    fun `cleans noisy size labels`() {
        assertEquals("2.4 GB", SizeParser.cleanSizeLabel("文件大小: 2.4 gb "))
        assertEquals("", SizeParser.cleanSizeLabel("   "))
    }

    @Test
    fun `formats byte counts into readable labels`() {
        assertEquals("", SizeParser.formatBytes(-1L))
        assertEquals("", SizeParser.formatBytes(0L))
        assertEquals("512 B", SizeParser.formatBytes(512L))
        assertEquals("680 KB", SizeParser.formatBytes(680L * 1024L))
        assertEquals("125.5 MB", SizeParser.formatBytes((125.5 * 1024L * 1024L).toLong()))
        assertEquals("4.50 GB", SizeParser.formatBytes((4.5 * 1024L * 1024L * 1024L).toLong()))
    }
}
