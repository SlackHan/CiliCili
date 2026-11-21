package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.SiteRequestDto
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteRuleDto
import com.lanhan.cilicili.domain.model.SiteSourceType
import com.lanhan.cilicili.ui.sites.SitesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSourceParserTest {

    @Test
    fun `parses prowlarr json response with magnetUrl, infoHash fallback, and seeders`() {
        val rule = SiteRule.fromDto(
            SiteRuleDto(
                id = 20001,
                name = "Prowlarr",
                sourceType = "PROWLARR",
                request = SiteRequestDto(
                    mainUrl = "https://prowlarr.example.com",
                    searchUrl = "https://prowlarr.example.com/api/v1/search?query=[keyword]&type=search&limit=50&offset=[offset]",
                    header = mapOf("X-Api-Key" to "secret-key"),
                ),
            ),
            sortOrder = 0,
        )

        val jsonBody = """
            [
              {
                "title": "Ubuntu 24.04.1 Desktop amd64",
                "size": 5368709120,
                "publishDate": "2026-10-20T14:22:10Z",
                "seeders": 320,
                "indexer": "1337x",
                "magnetUrl": "magnet:?xt=urn:btih:1111111111111111111111111111111111111111&dn=Ubuntu",
                "infoUrl": "https://1337x.to/torrent/123"
              },
              {
                "title": "Arch Linux 2026.10 ISO",
                "size": 1073741824,
                "publishDate": "2026-10-21T08:00:00Z",
                "seeders": 85,
                "indexer": "Nyaa",
                "infoHash": "2222222222222222222222222222222222222222",
                "downloadUrl": "https://prowlarr.example.com/1/download?link=abc"
              },
              {
                "title": "Debian Redirect Only",
                "size": 734003200,
                "publishDate": "2026-10-22T09:15:00Z",
                "seeders": 12,
                "indexer": "TGx",
                "downloadUrl": "/2/download?link=redirect-me"
              }
            ]
        """.trimIndent()

        val items = RuleExtractor.parseSearchResponse(
            rule = rule,
            body = jsonBody,
            requestUrl = "https://prowlarr.example.com/api/v1/search?query=linux&type=search&limit=50&offset=0",
        )

        assertEquals(3, items.size)

        assertEquals("Prowlarr · 1337x", items[0].siteName)
        assertEquals("5.00 GB", items[0].sizeText)
        assertEquals("2026-10-20", items[0].dateText)
        assertEquals(320, items[0].seeders)
        assertEquals("1111111111111111111111111111111111111111", items[0].infoHash)

        assertEquals("Prowlarr · Nyaa", items[1].siteName)
        assertEquals("1.00 GB", items[1].sizeText)
        assertEquals(85, items[1].seeders)
        assertEquals("2222222222222222222222222222222222222222", items[1].infoHash)
        assertTrue(items[1].magnetUri!!.startsWith("magnet:?xt=urn:btih:2222222222222222222222222222222222222222"))

        assertEquals("Prowlarr · TGx", items[2].siteName)
        assertNull(items[2].magnetUri)
        assertEquals("https://prowlarr.example.com/2/download?link=redirect-me", items[2].detailUrl)
    }

    @Test
    fun `parses torznab xml response with torznab attributes`() {
        val rule = SiteRule.fromDto(
            SiteRuleDto(
                id = 20002,
                name = "TorznabSrc",
                sourceType = "TORZNAB",
                request = SiteRequestDto(
                    mainUrl = "https://prowlarr.example.com",
                    searchUrl = "https://prowlarr.example.com/1/api?t=search&q=[keyword]&limit=50&offset=[offset]",
                ),
            ),
            sortOrder = 0,
        )

        val xmlBody = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/database">
              <channel>
                <item>
                  <title>Fedora Workstation 41</title>
                  <pubDate>Tue, 20 Oct 2026 12:34:56 +0000</pubDate>
                  <size>2147483648</size>
                  <link>https://tracker.example.org/item/99</link>
                  <prowlarrindexer>Nyaa.si</prowlarrindexer>
                  <torznab:attr name="seeders" value="44" />
                  <torznab:attr name="infohash" value="3333333333333333333333333333333333333333" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val items = RuleExtractor.parseSearchResponse(rule, xmlBody, rule.searchUrl)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("TorznabSrc · Nyaa.si", item.siteName)
        assertEquals("2.00 GB", item.sizeText)
        assertEquals("2026-10-20", item.dateText)
        assertEquals(44, item.seeders)
        assertEquals("3333333333333333333333333333333333333333", item.infoHash)
        assertTrue(item.magnetUri!!.startsWith("magnet:?xt=urn:btih:3333333333333333333333333333333333333333"))
    }

    @Test(expected = IllegalStateException::class)
    fun `throws descriptive error on torznab error element`() {
        val rule = SitesViewModel.buildApiSourceRule(
            id = 20003,
            sortOrder = -1,
            name = "Torznab",
            sourceType = SiteSourceType.TORZNAB,
            serverUrl = "http://100.64.0.2:9696",
            apiKey = "bad",
            indexerId = "1",
        )
        val errXml = """<?xml version="1.0"?><error code="100" description="Invalid API Key"/>"""
        RuleExtractor.parseSearchResponse(rule, errXml, rule.searchUrl)
    }

    @Test
    fun `builds prowlarr and torznab rules cleanly from base url`() {
        val prowlarr = SitesViewModel.buildApiSourceRule(
            id = 20010,
            sortOrder = -1,
            name = "",
            sourceType = SiteSourceType.PROWLARR,
            serverUrl = "100.64.1.2:9696/",
            apiKey = "abc123",
        )
        assertEquals("https://100.64.1.2:9696/api/v1/search?query=[keyword]&type=search&limit=50&offset=[offset]", prowlarr.searchUrl)
        assertEquals("abc123", prowlarr.headers["X-Api-Key"])
        assertEquals(SiteSourceType.PROWLARR, prowlarr.sourceType)

        val builtUrl = RuleExtractor.buildSearchUrl(prowlarr, "ubuntu iso", page = 2)
        assertTrue(builtUrl.contains("query=ubuntu%20iso"))
        assertTrue(builtUrl.contains("offset=50"))
    }
}
