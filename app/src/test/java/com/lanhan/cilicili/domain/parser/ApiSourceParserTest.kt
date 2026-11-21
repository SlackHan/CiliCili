package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.ExpressionModelDto
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

    @Test
    fun `parses generic json api root array and filters apibay empty sentinel`() {
        val rule = SiteRule.fromDto(
            SiteRuleDto(
                id = 10101,
                name = "海盗湾 API",
                sourceType = "JSON_API",
                request = SiteRequestDto(
                    mainUrl = "https://apibay.org",
                    searchUrl = "https://apibay.org/q.php?q=[keyword]",
                ),
                expressionModel = ExpressionModelDto(
                    group = "",
                    title = "name",
                    date = "added",
                    size = "size",
                    url = "",
                    magnet = "info_hash",
                ),
            ),
            sortOrder = 0,
        )

        val emptyJson = """
            [
              {
                "id": "0",
                "name": "No results returned",
                "info_hash": "0000000000000000000000000000000000000000",
                "size": "0",
                "added": "0",
                "seeders": "0"
              }
            ]
        """.trimIndent()
        assertTrue(RuleExtractor.parseSearchResponse(rule, emptyJson, rule.searchUrl).isEmpty())

        val validJson = """
            [
              {
                "id": "123",
                "name": "Ubuntu 24.04.1 LTS",
                "info_hash": "4444444444444444444444444444444444444444",
                "size": "4294967296",
                "added": "1700000000",
                "seeders": "210"
              }
            ]
        """.trimIndent()
        val items = RuleExtractor.parseSearchResponse(rule, validJson, rule.searchUrl)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu 24.04.1 LTS", item.title)
        assertEquals(4294967296L, item.sizeBytes)
        assertEquals("4.00 GB", item.sizeText)
        assertEquals("2023-11-14", item.dateText)
        assertEquals(210, item.seeders)
        assertEquals("4444444444444444444444444444444444444444", item.infoHash)
        assertTrue(item.magnetUri!!.startsWith("magnet:?xt=urn:btih:4444444444444444444444444444444444444444"))
    }

    @Test
    fun `parses nested json dot-path with fallback expression`() {
        val rule = SiteRule.fromDto(
            SiteRuleDto(
                id = 10104,
                name = "NestedApi",
                sourceType = "JSON_API",
                request = SiteRequestDto(
                    mainUrl = "https://feed.example.org",
                    searchUrl = "https://feed.example.org/json?q=[keyword]",
                ),
                expressionModel = ExpressionModelDto(
                    group = "data.movies",
                    title = "title",
                    date = "timestamp",
                    size = "total_size",
                    url = "link",
                    magnet = "magnet_uri|info_hash",
                ),
            ),
            sortOrder = 0,
        )

        val jsonBody = """
            {
              "data": {
                "movies": [
                  {
                    "title": "Movie One",
                    "timestamp": 1700000000,
                    "total_size": 1073741824,
                    "link": "/view/1",
                    "magnet_uri": null,
                    "info_hash": "5555555555555555555555555555555555555555"
                  }
                ]
              }
            }
        """.trimIndent()

        val items = RuleExtractor.parseSearchResponse(rule, jsonBody, rule.searchUrl)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Movie One", item.title)
        assertEquals("1.00 GB", item.sizeText)
        assertEquals("2023-11-14", item.dateText)
        assertEquals("https://feed.example.org/view/1", item.detailUrl)
        assertEquals("5555555555555555555555555555555555555555", item.infoHash)
    }

    @Test
    fun `parses rss feeds for nyaa, dmhy enclosure, and atom entries`() {
        val nyaaRule = SiteRule.fromDto(
            SiteRuleDto(
                id = 10102,
                name = "Nyaa",
                sourceType = "RSS",
                request = SiteRequestDto(
                    mainUrl = "https://nyaa.si",
                    searchUrl = "https://nyaa.si/?page=rss&q=[keyword]",
                ),
            ),
            sortOrder = 0,
        )

        val nyaaXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:nyaa="https://nyaa.si/xmlns/nyaa">
              <channel>
                <item>
                  <title>[SubsPlease] Frieren - 01 (1080p)</title>
                  <link>https://nyaa.si/view/1700000</link>
                  <pubDate>Fri, 29 Sep 2023 15:10:00 -0000</pubDate>
                  <nyaa:seeders>512</nyaa:seeders>
                  <nyaa:infoHash>6666666666666666666666666666666666666666</nyaa:infoHash>
                  <nyaa:size>1.4 GiB</nyaa:size>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val nyaaItems = RuleExtractor.parseSearchResponse(nyaaRule, nyaaXml, nyaaRule.searchUrl)
        assertEquals(1, nyaaItems.size)
        val nItem = nyaaItems.first()
        assertEquals("[SubsPlease] Frieren - 01 (1080p)", nItem.title)
        assertEquals(512, nItem.seeders)
        assertEquals("2023-09-29", nItem.dateText)
        assertTrue(nItem.sizeBytes > 1_000_000_000L)
        assertEquals("6666666666666666666666666666666666666666", nItem.infoHash)
        assertEquals("https://nyaa.si/view/1700000", nItem.detailUrl)

        val dmhyXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>DMHY Release</title>
                  <link>https://dmhy.anoneko.com/topics/view/123.html</link>
                  <pubDate>Tue, 20 Oct 2026 12:34:56 +0000</pubDate>
                  <enclosure url="magnet:?xt=urn:btih:7777777777777777777777777777777777777777&amp;dn=DMHY" length="0" type="application/x-bittorrent" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val dmhyItems = RuleExtractor.parseSearchResponse(nyaaRule, dmhyXml, nyaaRule.searchUrl)
        assertEquals(1, dmhyItems.size)
        assertEquals("7777777777777777777777777777777777777777", dmhyItems.first().infoHash)
        assertEquals("https://dmhy.anoneko.com/topics/view/123.html", dmhyItems.first().detailUrl)

        val atomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Atom Release</title>
                <id>urn:btih:8888888888888888888888888888888888888888</id>
                <link rel="alternate" href="https://example.org/entry/1" />
                <updated>2026-11-10T08:00:00Z</updated>
                <summary>magnet:?xt=urn:btih:8888888888888888888888888888888888888888</summary>
              </entry>
            </feed>
        """.trimIndent()

        val atomItems = RuleExtractor.parseSearchResponse(nyaaRule, atomXml, nyaaRule.searchUrl)
        assertEquals(1, atomItems.size)
        assertEquals("Atom Release", atomItems.first().title)
        assertEquals("2026-11-10", atomItems.first().dateText)
        assertEquals("8888888888888888888888888888888888888888", atomItems.first().infoHash)
        assertEquals("https://example.org/entry/1", atomItems.first().detailUrl)
    }
}
