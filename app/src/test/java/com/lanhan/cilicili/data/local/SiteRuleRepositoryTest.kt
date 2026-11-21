package com.lanhan.cilicili.data.local

import com.lanhan.cilicili.domain.model.SiteSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRuleRepositoryTest {

    @Test
    fun `incremental subscription sync deactivates missing public sites while preserving custom and api sources`() = runTest {
        val dao = FakeSiteRuleDao(
            initial = listOf(
                makeEntity(
                    id = 1L,
                    name = "ActivePublicSite",
                    enabled = true,
                    sortOrder = 0,
                    sourceType = SiteSourceType.HTML.name,
                    searchUrl = "https://old1.example/s?q=[keyword]",
                ),
                makeEntity(
                    id = 2L,
                    name = "StalePublicHtml",
                    enabled = true,
                    sortOrder = 1,
                    sourceType = SiteSourceType.HTML.name,
                    searchUrl = "https://stale2.example/s?q=[keyword]",
                ),
                makeEntity(
                    id = 10101L,
                    name = "StalePublicJsonApi",
                    enabled = true,
                    sortOrder = 2,
                    sourceType = SiteSourceType.JSON_API.name,
                    searchUrl = "https://stale-api.example/q?q=[keyword]",
                ),
                makeEntity(
                    id = 10102L,
                    name = "StalePublicRss",
                    enabled = true,
                    sortOrder = 3,
                    sourceType = SiteSourceType.RSS.name,
                    searchUrl = "https://stale-rss.example/rss?q=[keyword]",
                ),
                makeEntity(
                    id = 20001L,
                    name = "UserCustomHtmlRule",
                    enabled = true,
                    sortOrder = -1,
                    sourceType = SiteSourceType.HTML.name,
                    searchUrl = "https://my-custom.example/s?q=[keyword]",
                ),
                makeEntity(
                    id = 20002L,
                    name = "SelfHostedProwlarr",
                    enabled = true,
                    sortOrder = -2,
                    sourceType = SiteSourceType.PROWLARR.name,
                    searchUrl = "http://100.64.0.1:9696/api/v1/search?query=[keyword]",
                    headersJson = """{"X-Api-Key":"secret-token"}""",
                ),
                makeEntity(
                    id = 888L,
                    name = "SelfHostedTorznabLowId",
                    enabled = true,
                    sortOrder = -3,
                    sourceType = SiteSourceType.TORZNAB.name,
                    searchUrl = "http://100.64.0.1:9696/1/api?t=search&q=[keyword]",
                ),
            ),
        )
        val repository = SiteRuleRepository(context = null, dao = dao)

        val incomingLiveJson = """
            [
              {
                "id": 1,
                "name": "ActivePublicSite",
                "state": true,
                "sourceType": "HTML",
                "request": {
                  "method": "GET",
                  "mainUrl": "https://new1.example",
                  "searchUrl": "https://new1.example/s?q=[keyword]"
                },
                "expressionModel": {
                  "group": "//tr",
                  "title": "./td[1]",
                  "magnet": "./td[2]/a/@href"
                }
              },
              {
                "id": 3,
                "name": "NewlyAddedPublicSite",
                "state": true,
                "sourceType": "HTML",
                "request": {
                  "method": "GET",
                  "mainUrl": "https://new3.example",
                  "searchUrl": "https://new3.example/s?q=[keyword]"
                }
              },
              {
                "id": 20002,
                "name": "MaliciousCollisionAttempt",
                "state": true,
                "sourceType": "HTML",
                "request": {
                  "method": "GET",
                  "mainUrl": "https://bad.example",
                  "searchUrl": "https://bad.example/s?q=[keyword]"
                }
              }
            ]
        """.trimIndent()

        val importedCount = repository.importFromJson(
            rawJson = incomingLiveJson,
            replaceAll = false,
            preserveApiSources = true,
            deactivateMissingPublicSites = true,
        )

        assertEquals(2, importedCount)

        val byId = dao.getAll().associateBy { it.id }
        assertEquals(8, byId.size)

        // 1. Existing live public site is updated and keeps its original sortOrder
        assertNotNull(byId[1L])
        val site1 = byId.getValue(1L)
        assertTrue(site1.enabled)
        assertEquals(0, site1.sortOrder)
        assertEquals("https://new1.example/s?q=[keyword]", site1.searchUrl)

        // 2. Newly discovered public site is appended after max existing sortOrder
        assertNotNull(byId[3L])
        val site3 = byId.getValue(3L)
        assertTrue(site3.enabled)
        assertEquals(4, site3.sortOrder)

        // 3. Stale public sites (HTML / JSON_API / RSS with id < 20000) are automatically disabled but retained in DB
        assertNotNull(byId[2L])
        val staleHtml = byId.getValue(2L)
        assertFalse(staleHtml.enabled)
        assertEquals(1, staleHtml.sortOrder)

        assertNotNull(byId[10101L])
        val staleJsonApi = byId.getValue(10101L)
        assertFalse(staleJsonApi.enabled)

        assertNotNull(byId[10102L])
        val staleRss = byId.getValue(10102L)
        assertFalse(staleRss.enabled)

        // 4. User custom rule (id >= 20000) and self-hosted Prowlarr/Torznab rules remain enabled and untouched
        assertNotNull(byId[20001L])
        val customRule = byId.getValue(20001L)
        assertTrue(customRule.enabled)
        assertEquals(-1, customRule.sortOrder)
        assertEquals("https://my-custom.example/s?q=[keyword]", customRule.searchUrl)

        assertNotNull(byId[20002L])
        val prowlarrRule = byId.getValue(20002L)
        assertTrue(prowlarrRule.enabled)
        assertEquals(SiteSourceType.PROWLARR.name, prowlarrRule.sourceType)
        assertEquals("http://100.64.0.1:9696/api/v1/search?query=[keyword]", prowlarrRule.searchUrl)
        assertTrue(prowlarrRule.headersJson.contains("secret-token"))

        assertNotNull(byId[888L])
        val torznabRule = byId.getValue(888L)
        assertTrue(torznabRule.enabled)
        assertEquals(SiteSourceType.TORZNAB.name, torznabRule.sourceType)
    }

    @Test
    fun `manual json import without deactivateMissingPublicSites keeps existing public sites enabled`() = runTest {
        val dao = FakeSiteRuleDao(
            initial = listOf(
                makeEntity(
                    id = 1L,
                    name = "PublicOne",
                    enabled = true,
                    sortOrder = 0,
                    sourceType = SiteSourceType.HTML.name,
                    searchUrl = "https://one.example/s?q=[keyword]",
                ),
            ),
        )
        val repository = SiteRuleRepository(context = null, dao = dao)

        val singleRuleJson = """
            {
              "id": 2,
              "name": "PublicTwo",
              "state": true,
              "sourceType": "HTML",
              "request": {
                "method": "GET",
                "mainUrl": "https://two.example",
                "searchUrl": "https://two.example/s?q=[keyword]"
              }
            }
        """.trimIndent()

        val count = repository.importFromJson(
            rawJson = singleRuleJson,
            replaceAll = false,
            preserveApiSources = true,
            deactivateMissingPublicSites = false,
        )

        assertEquals(1, count)
        val byId = dao.getAll().associateBy { it.id }
        assertTrue(byId.getValue(1L).enabled)
        assertTrue(byId.getValue(2L).enabled)
    }

    @Test
    fun `official subscription presets and default url are configured`() {
        assertEquals(
            "https://raw.githubusercontent.com/SlackHan/CiliCili/main/rules/sites_live.json",
            SettingsRepository.OFFICIAL_GITHUB_RAW_SUBSCRIPTION_URL,
        )
        assertEquals(
            "https://fastly.jsdelivr.net/gh/SlackHan/CiliCili@main/rules/sites_live.json",
            SettingsRepository.OFFICIAL_CDN_SUBSCRIPTION_URL,
        )
        assertEquals(
            SettingsRepository.OFFICIAL_GITHUB_RAW_SUBSCRIPTION_URL,
            AppSettings().subscriptionUrl,
        )
        assertEquals(2, SettingsRepository.OFFICIAL_SUBSCRIPTION_PRESETS.size)
    }

    private fun makeEntity(
        id: Long,
        name: String,
        enabled: Boolean,
        sortOrder: Int,
        sourceType: String,
        searchUrl: String,
        headersJson: String = "{}",
    ): SiteRuleEntity = SiteRuleEntity(
        id = id,
        name = name,
        info = "",
        enabled = enabled,
        sortOrder = sortOrder,
        time = "",
        updateTime = "",
        method = "GET",
        mainUrl = "https://example.org",
        searchUrl = searchUrl,
        headersJson = headersJson,
        groupExpr = "",
        titleExpr = "",
        dateExpr = "",
        sizeExpr = "",
        urlExpr = "",
        magnetExpr = "",
        sourceType = sourceType,
    )

    private class FakeSiteRuleDao(initial: List<SiteRuleEntity> = emptyList()) : SiteRuleDao {
        private val store = LinkedHashMap<Long, SiteRuleEntity>().apply {
            initial.forEach { put(it.id, it) }
        }
        private val stateFlow = MutableStateFlow(sortedSnapshot())

        private fun sortedSnapshot(): List<SiteRuleEntity> =
            store.values.sortedWith(compareBy<SiteRuleEntity> { it.sortOrder }.thenBy { it.id })

        private fun emit() {
            stateFlow.value = sortedSnapshot()
        }

        override fun observeAll(): Flow<List<SiteRuleEntity>> = stateFlow

        override fun observeEnabled(): Flow<List<SiteRuleEntity>> =
            stateFlow.map { list -> list.filter { it.enabled } }

        override suspend fun getAll(): List<SiteRuleEntity> = sortedSnapshot()

        override suspend fun count(): Int = store.size

        override suspend fun upsertAll(entities: List<SiteRuleEntity>) {
            entities.forEach { store[it.id] = it }
            emit()
        }

        override suspend fun upsert(entity: SiteRuleEntity) {
            store[entity.id] = entity
            emit()
        }

        override suspend fun setEnabled(id: Long, enabled: Boolean) {
            store[id]?.let { store[id] = it.copy(enabled = enabled) }
            emit()
        }

        override suspend fun disableByIds(ids: List<Long>) {
            ids.forEach { id ->
                store[id]?.let { store[id] = it.copy(enabled = false) }
            }
            emit()
        }

        override suspend fun updateHealth(
            id: Long,
            status: String,
            latencyMs: Long?,
            checkedAt: Long,
            errorMessage: String?,
        ) {
            store[id]?.let {
                store[id] = it.copy(
                    healthStatus = status,
                    lastLatencyMs = latencyMs,
                    lastCheckedAt = checkedAt,
                    lastErrorMessage = errorMessage,
                )
            }
            emit()
        }

        override suspend fun enableOnlyHealthy() {
            store.replaceAll { _, entity -> entity.copy(enabled = entity.healthStatus == "OK") }
            emit()
        }

        override suspend fun enableAll() {
            store.replaceAll { _, entity -> entity.copy(enabled = true) }
            emit()
        }

        override suspend fun deleteById(id: Long) {
            store.remove(id)
            emit()
        }

        override suspend fun deleteNonApiRules() {
            val toRemove = store.values
                .filter { it.sourceType !in setOf("PROWLARR", "TORZNAB") }
                .map { it.id }
            toRemove.forEach { store.remove(it) }
            emit()
        }

        override suspend fun getSelfHostedApiIds(): List<Long> =
            store.values
                .filter { it.sourceType in setOf("PROWLARR", "TORZNAB") }
                .map { it.id }

        override suspend fun deleteAll() {
            store.clear()
            emit()
        }
    }
}
