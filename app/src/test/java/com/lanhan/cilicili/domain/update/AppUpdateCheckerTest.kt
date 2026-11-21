package com.lanhan.cilicili.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun `isNewerVersion handles v prefix, equal versions, and multi-digit segments`() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.2", "v1.3.0"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.2.2", "V1.3.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.2.2", "1.2.2"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.2.2", "v1.2.2"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.2.10", "1.2.2"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.2", "1.2.10"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.3.0", "v1.2.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.9.9", "v2.0.0"))
    }

    @Test
    fun `isNewerVersion strips pre-release and build metadata suffixes`() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.2", "v1.3.0-rc.1"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.2-beta.1", "v1.2.3+20261121"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.3.0", "v1.3.0-alpha.2+build.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.3.0", ""))
        assertFalse(AppUpdateChecker.isNewerVersion("1.3.0", "invalid-tag"))
    }

    @Test
    fun `parseReleaseJson selects apk asset download url and marks update available`() {
        val sampleJson = """
            {
              "tag_name": "v1.3.0",
              "name": "CiliCili v1.3.0 · 精密仪表体验升级",
              "body": "- 新增应用内检查更新\n- 新增本地稍后下载收藏夹\n- 新增搜索结果二次过滤与排除词",
              "published_at": "2026-11-21T12:00:00Z",
              "html_url": "https://github.com/SlackHan/CiliCili/releases/tag/v1.3.0",
              "assets": [
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/SlackHan/CiliCili/releases/download/v1.3.0/checksums.txt",
                  "content_type": "text/plain"
                },
                {
                  "name": "CiliCili-v1.3.0-release.apk",
                  "browser_download_url": "https://github.com/SlackHan/CiliCili/releases/download/v1.3.0/CiliCili-v1.3.0-release.apk",
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateChecker.parseReleaseJson(
            rawJson = sampleJson,
            currentVersion = "1.2.2",
        )

        assertEquals("1.3.0", info.versionName)
        assertEquals("v1.3.0", info.tagName)
        assertEquals("CiliCili v1.3.0 · 精密仪表体验升级", info.title)
        assertTrue(info.changelog.contains("新增应用内检查更新"))
        assertEquals(
            "https://github.com/SlackHan/CiliCili/releases/download/v1.3.0/CiliCili-v1.3.0-release.apk",
            info.downloadUrl,
        )
        assertEquals("https://github.com/SlackHan/CiliCili/releases/tag/v1.3.0", info.htmlUrl)
        assertEquals("2026-11-21T12:00:00Z", info.publishedAt)
        assertTrue(info.hasUpdate)
    }

    @Test
    fun `parseReleaseJson falls back to html_url when no apk asset is attached`() {
        val sampleJson = """
            {
              "tag_name": "v1.2.2",
              "name": null,
              "body": null,
              "published_at": "2026-11-20T08:30:00Z",
              "html_url": "https://github.com/SlackHan/CiliCili/releases/tag/v1.2.2",
              "assets": []
            }
        """.trimIndent()

        val info = AppUpdateChecker.parseReleaseJson(
            rawJson = sampleJson,
            currentVersion = "1.2.2",
        )

        assertEquals("1.2.2", info.versionName)
        assertEquals("v1.2.2", info.tagName)
        assertEquals("v1.2.2", info.title)
        assertEquals("暂无版本变更说明", info.changelog)
        assertEquals("https://github.com/SlackHan/CiliCili/releases/tag/v1.2.2", info.downloadUrl)
        assertFalse(info.hasUpdate)
    }
}
