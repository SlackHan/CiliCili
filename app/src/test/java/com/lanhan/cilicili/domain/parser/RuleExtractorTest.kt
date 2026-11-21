package com.lanhan.cilicili.domain.parser

import com.lanhan.cilicili.domain.model.ExpressionModelDto
import com.lanhan.cilicili.domain.model.SiteRequestDto
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteRuleDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleExtractorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses one-step hash path rule and ignores malformed date xpath`() {
        val rule = SiteRule.fromDto(
            SiteRuleDto(
                id = 10009,
                name = "Test1024",
                request = SiteRequestDto(
                    mainUrl = "https://example.org",
                    searchUrl = "https://example.org/search-[keyword]-[page].html",
                ),
                expressionModel = ExpressionModelDto(
                    group = "//article[@class='card']",
                    title = "./h2/a",
                    date = ".//dd[@class'broken']", // intentional syntax error like dmhy rule
                    size = "./div/span[2]",
                    url = "",
                    magnet = "./h2/a[starts-with(@href, '/hash/')]/@href",
                ),
            ),
            sortOrder = 0,
        )

        val html = """
            <html><body>
              <article class="card">
                <h2><a href="/hash/0123456789abcdef0123456789abcdef01234567.html">Ubuntu Desktop ISO</a></h2>
                <div><span>2026-10-01</span><span>4.2 GB</span></div>
              </article>
            </body></html>
        """.trimIndent()

        val items = RuleExtractor.parseSearchPage(rule, html, "https://example.org/search-ubuntu-1.html")
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("Ubuntu Desktop ISO", item.title)
        assertEquals("", item.dateText)
        assertEquals("4.2 GB", item.sizeText)
        assertEquals("0123456789abcdef0123456789abcdef01234567", item.infoHash)
        assertTrue(item.magnetUri!!.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    }

    @Test
    fun `parses two-step rule and extracts magnet from element without explicit href attr`() {
        val rule = SiteRule.fromDto(
            SiteRuleDto(
                id = 10024,
                name = "TwoStepSite",
                request = SiteRequestDto(
                    mainUrl = "https://two.example.org",
                    searchUrl = "https://two.example.org/s?q=[keyword]&p=[page]",
                ),
                expressionModel = ExpressionModelDto(
                    group = "//div[@class='item']",
                    title = ".//h3/a",
                    date = ".//span[@class='date']",
                    size = ".//span[@class='size']",
                    url = ".//h3/a/@href",
                    magnet = "//a[@id='magnet2']",
                ),
            ),
            sortOrder = 0,
        )

        val searchHtml = """
            <html><body>
              <div class="item">
                <h3><a href="/topic/888.html">Debian 13 Netinst</a></h3>
                <span class="date">2026-11-01</span>
                <span class="size">650 MB</span>
              </div>
            </body></html>
        """.trimIndent()

        val searchItems = RuleExtractor.parseSearchPage(rule, searchHtml, "https://two.example.org/s?q=debian&p=1")
        assertEquals(1, searchItems.size)
        assertNull(searchItems.first().magnetUri)
        assertEquals("https://two.example.org/topic/888.html", searchItems.first().detailUrl)

        val detailHtml = """
            <html><body>
              <a id="magnet2" href="magnet:?xt=urn:btih:fedcba9876543210fedcba9876543210fedcba98&dn=Debian">Magnet</a>
            </body></html>
        """.trimIndent()

        val magnet = RuleExtractor.parseDetailMagnet(rule, detailHtml, searchItems.first().detailUrl, "Debian 13 Netinst")
        assertNotNull(magnet)
        assertTrue(magnet!!.contains("fedcba9876543210fedcba9876543210fedcba98"))
    }

    @Test
    fun `bundled sites_default json parses cleanly into 39 rules`() {
        val candidates = listOf(
            File("src/main/assets/sites_default.json"),
            File("app/src/main/assets/sites_default.json"),
        )
        val jsonFile = candidates.firstOrNull { it.exists() }
        assertNotNull("sites_default.json should exist", jsonFile)
        val dtos = json.decodeFromString<List<SiteRuleDto>>(jsonFile!!.readText())
        assertEquals(39, dtos.size)
    }
}
