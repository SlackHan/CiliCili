package com.lanhan.cilicili.domain.search

import com.lanhan.cilicili.data.network.HttpClientFactory
import com.lanhan.cilicili.data.network.SiteHttpFetcher
import com.lanhan.cilicili.data.network.SiteSessionStore
import com.lanhan.cilicili.domain.model.ExpressionModelDto
import com.lanhan.cilicili.domain.model.RawSiteResult
import com.lanhan.cilicili.domain.model.SiteRequestDto
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteRuleDto
import com.lanhan.cilicili.domain.model.SiteSearchStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchOrchestratorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `deduplicates identical infohash across multiple sites`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path?.startsWith("/s1") == true -> MockResponse().setBody(
                        """
                        <div class="row">
                          <a class="t" href="/hash/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.html">Ubuntu 24.04</a>
                          <span class="sz">4.5 GB</span>
                        </div>
                        """.trimIndent(),
                    )
                    request.path?.startsWith("/s2") == true -> MockResponse().setBody(
                        """
                        <div class="row">
                          <a class="t" href="magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&dn=Ubuntu">Ubuntu 24.04 LTS Server & Desktop</a>
                          <span class="sz">4.50 GB</span>
                        </div>
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val site1 = SiteRule.fromDto(
            SiteRuleDto(
                id = 1,
                name = "SiteOne",
                request = SiteRequestDto(mainUrl = baseUrl, searchUrl = "$baseUrl/s1?q=[keyword]&p=[page]"),
                expressionModel = ExpressionModelDto(
                    group = "//div[@class='row']",
                    title = "./a[@class='t']",
                    size = "./span[@class='sz']",
                    magnet = "./a[@class='t']/@href",
                ),
            ),
            0,
        )
        val site2 = SiteRule.fromDto(
            SiteRuleDto(
                id = 2,
                name = "SiteTwo",
                request = SiteRequestDto(mainUrl = baseUrl, searchUrl = "$baseUrl/s2?q=[keyword]&p=[page]"),
                expressionModel = ExpressionModelDto(
                    group = "//div[@class='row']",
                    title = "./a[@class='t']",
                    size = "./span[@class='sz']",
                    magnet = "./a[@class='t']/@href",
                ),
            ),
            1,
        )

        val sessionStore = SiteSessionStore()
        val client = HttpClientFactory.create()
        val orchestrator = SearchOrchestrator(SiteHttpFetcher({ client }, sessionStore))

        val events = orchestrator.searchStreaming("ubuntu", 1, listOf(site1, site2), 4).toList()
        val rawItems = events.filterIsInstance<SearchStreamEvent.ResultsBatch>().flatMap { it.items }
        assertEquals(2, rawItems.size)

        val merged = SearchOrchestrator.aggregateAndDeduplicate(rawItems)
        assertEquals(1, merged.size)
        assertEquals(2, merged.first().sources.size)
        assertEquals("Ubuntu 24.04 LTS Server & Desktop", merged.first().title)
    }

    @Test
    fun `detects cloudflare challenge and retries using saved cookie and webview user-agent`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cookie = request.getHeader("Cookie").orEmpty()
                val ua = request.getHeader("User-Agent").orEmpty()
                return if (cookie.contains("cf_clearance=verified123") && ua == "MockWebViewUA/1.0") {
                    MockResponse().setResponseCode(200).setBody(
                        """<div class="row"><a href="magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb">Solved Item</a></div>""",
                    )
                } else {
                    MockResponse()
                        .setResponseCode(403)
                        .addHeader("cf-mitigated", "challenge")
                        .setBody("<html><title>Just a moment...</title></html>")
                }
            }
        }

        val site = SiteRule.fromDto(
            SiteRuleDto(
                id = 3,
                name = "CfSite",
                request = SiteRequestDto(mainUrl = baseUrl, searchUrl = "$baseUrl/cf?q=[keyword]&p=[page]"),
                expressionModel = ExpressionModelDto(
                    group = "//div[@class='row']",
                    title = "./a",
                    magnet = "./a/@href",
                ),
            ),
            0,
        )

        val sessionStore = SiteSessionStore()
        val client = HttpClientFactory.create()
        val orchestrator = SearchOrchestrator(SiteHttpFetcher({ client }, sessionStore))

        val (firstStatus, firstItems) = orchestrator.searchSingleSite(site, "test")
        assertTrue(firstStatus is SiteSearchStatus.ChallengeRequired)
        assertTrue(firstItems.isEmpty())

        // Simulate WebView challenge completion saving cf_clearance + WebView UA
        sessionStore.save(server.hostName, "cf_clearance=verified123", "MockWebViewUA/1.0")

        val (secondStatus, secondItems) = orchestrator.searchSingleSite(site, "test")
        assertTrue(secondStatus is SiteSearchStatus.Success)
        assertEquals(1, secondItems.size)
        assertEquals("Solved Item", secondItems.first().title)
    }

    @Test
    fun `resolves prowlarr api results, merges seeders, and follows 302 magnet redirect`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path?.startsWith("/api/v1/search") == true -> {
                        if (request.getHeader("X-Api-Key") != "test-key") {
                            MockResponse().setResponseCode(401)
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """
                                [
                                  {
                                    "title": "Linux Mint 22",
                                    "size": 3221225472,
                                    "publishDate": "2026-10-15T10:00:00Z",
                                    "seeders": 40,
                                    "indexer": "IndexerA",
                                    "infoHash": "cccccccccccccccccccccccccccccccccccccccc"
                                  },
                                  {
                                    "title": "Linux Mint 22 (Cinnamon)",
                                    "size": 3221225472,
                                    "publishDate": "2026-10-15T10:00:00Z",
                                    "seeders": 95,
                                    "indexer": "IndexerB",
                                    "infoHash": "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC"
                                  },
                                  {
                                    "title": "Redirect Only Release",
                                    "size": 104857600,
                                    "publishDate": "2026-10-16T10:00:00Z",
                                    "seeders": 5,
                                    "indexer": "IndexerC",
                                    "downloadUrl": "/download/redirect1"
                                  }
                                ]
                                """.trimIndent(),
                            )
                        }
                    }
                    request.path == "/download/redirect1" -> {
                        MockResponse()
                            .setResponseCode(302)
                            .addHeader("Location", "magnet:?xt=urn:btih:dddddddddddddddddddddddddddddddddddddddd&dn=Redirect")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val prowlarrRule = SiteRule.fromDto(
            SiteRuleDto(
                id = 20001,
                name = "Prowlarr",
                sourceType = "PROWLARR",
                request = SiteRequestDto(
                    mainUrl = baseUrl,
                    searchUrl = "$baseUrl/api/v1/search?query=[keyword]&type=search&limit=50&offset=[offset]",
                    header = mapOf("X-Api-Key" to "test-key"),
                ),
            ),
            0,
        )

        val client = HttpClientFactory.create()
        val orchestrator = SearchOrchestrator(SiteHttpFetcher({ client }, SiteSessionStore()))

        val (status, rawItems) = orchestrator.searchSingleSite(prowlarrRule, "mint", page = 1)
        assertTrue(status is SiteSearchStatus.Success)
        assertEquals(3, rawItems.size)

        val merged = SearchOrchestrator.aggregateAndDeduplicate(rawItems)
        assertEquals(2, merged.size)

        val mint = merged.first { it.infoHash == "cccccccccccccccccccccccccccccccccccccccc" }
        assertEquals(95, mint.seeders)
        assertEquals(2, mint.sources.size)

        val redirectItem = merged.first { it.title == "Redirect Only Release" }
        val resolved = orchestrator.resolveMagnet(redirectItem, mapOf(prowlarrRule.id to prowlarrRule))
        assertTrue(resolved is DetailResolveResult.Success)
        val success = resolved as DetailResolveResult.Success
        assertEquals("dddddddddddddddddddddddddddddddddddddddd", success.infoHash)
    }
}
