package com.lanhan.cilicili.ui.search

import com.lanhan.cilicili.data.local.BookmarkEntity
import com.lanhan.cilicili.data.local.toBookmarkEntity
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.MinSizeFilter
import com.lanhan.cilicili.domain.model.SiteSourceRef
import com.lanhan.cilicili.domain.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterAndBookmarkTest {

    private val sampleItems = listOf(
        AggregatedMagnetItem(
            dedupKey = "hash_4k_remux",
            infoHash = "1111111111111111111111111111111111111111",
            title = "Dune.Part.Two.2024.2160p.4K.UHD.BluRay.REMUX.HEVC-GROUP",
            sizeText = "58.4 GB",
            sizeBytes = 58L * 1024 * 1024 * 1024,
            dateText = "2026-03-10",
            magnetUri = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111",
            sources = listOf(
                SiteSourceRef(1L, "Nyaa", "https://nyaa.si/view/1", "magnet:?xt=urn:btih:1111111111111111111111111111111111111111"),
                SiteSourceRef(2L, "UIndex", "https://uindex.org/1", null),
            ),
            seeders = 120,
        ),
        AggregatedMagnetItem(
            dedupKey = "hash_1080p_web",
            infoHash = "2222222222222222222222222222222222222222",
            title = "Dune.Part.Two.2024.1080p.WEB-DL.DDP5.1.H264",
            sizeText = "4.2 GB",
            sizeBytes = 4L * 1024 * 1024 * 1024,
            dateText = "2026-03-12",
            magnetUri = "magnet:?xt=urn:btih:2222222222222222222222222222222222222222",
            sources = listOf(
                SiteSourceRef(2L, "UIndex", "https://uindex.org/2", "magnet:?xt=urn:btih:2222222222222222222222222222222222222222"),
            ),
            seeders = 340,
        ),
        AggregatedMagnetItem(
            dedupKey = "hash_720p_cam",
            infoHash = "3333333333333333333333333333333333333333",
            title = "Dune.Part.Two.2024.720p.HDCAM.x264",
            sizeText = "850 MB",
            sizeBytes = 850L * 1024 * 1024,
            dateText = "2026-02-20",
            magnetUri = "magnet:?xt=urn:btih:3333333333333333333333333333333333333333",
            sources = listOf(
                SiteSourceRef(3L, "BT4G", "https://bt4gprx.com/3", "magnet:?xt=urn:btih:3333333333333333333333333333333333333333"),
            ),
            seeders = 15,
        ),
        AggregatedMagnetItem(
            dedupKey = "hash_ost_flac",
            infoHash = "4444444444444444444444444444444444444444",
            title = "Dune.Part.Two.OST.Soundtrack.FLAC",
            sizeText = "420 MB",
            sizeBytes = 420L * 1024 * 1024,
            dateText = "2026-03-01",
            magnetUri = null,
            sources = listOf(
                SiteSourceRef(1L, "Nyaa", "https://nyaa.si/view/4", null),
            ),
            seeders = 45,
        ),
    )

    @Test
    fun `filterAndSortResults filters by include keywords across title and siteName`() {
        val byTitle = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            secondaryQuery = "2160p remux",
        )
        assertEquals(listOf("hash_4k_remux"), byTitle.map { it.dedupKey })

        val bySiteAndTitle = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            secondaryQuery = "nyaa flac",
        )
        assertEquals(listOf("hash_ost_flac"), bySiteAndTitle.map { it.dedupKey })
    }

    @Test
    fun `filterAndSortResults excludes items matching minus or bang prefixes`() {
        val filtered = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            secondaryQuery = "Dune -720p !FLAC",
        )
        assertEquals(
            listOf("hash_4k_remux", "hash_1080p_web"),
            filtered.map { it.dedupKey },
        )

        // Bare '-' or '!' while user is mid-typing should not exclude everything
        val bareDash = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            secondaryQuery = "Dune - !",
        )
        assertEquals(4, bareDash.size)
    }

    @Test
    fun `filterAndSortResults filters by selectedSiteName`() {
        val nyaaOnly = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            selectedSiteName = "Nyaa",
        )
        assertEquals(
            listOf("hash_4k_remux", "hash_ost_flac"),
            nyaaOnly.map { it.dedupKey },
        )

        val uindexOnly = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            selectedSiteName = "UIndex",
        )
        assertEquals(
            listOf("hash_4k_remux", "hash_1080p_web"),
            uindexOnly.map { it.dedupKey },
        )
    }

    @Test
    fun `filterAndSortResults combines minSizeFilter secondaryQuery selectedSiteName and sortOption`() {
        val result = SearchViewModel.filterAndSortResults(
            items = sampleItems,
            minSizeFilter = MinSizeFilter.OVER_1_GB,
            sortOption = SortOption.SEEDERS_DESC,
            secondaryQuery = "Dune -cam",
            selectedSiteName = "UIndex",
        )
        assertEquals(
            listOf("hash_1080p_web", "hash_4k_remux"),
            result.map { it.dedupKey },
        )
    }

    @Test
    fun `BookmarkEntity converts to and from AggregatedMagnetItem preserving sources and detailUrl`() {
        val original = sampleItems.first()
        val entity = original.toBookmarkEntity(addedAt = 1700000000000L)

        assertEquals(original.dedupKey, entity.dedupKey)
        assertEquals(original.title, entity.title)
        assertEquals(original.magnetUri, entity.magnetUri)
        assertEquals(original.infoHash, entity.infoHash)
        assertEquals(original.sizeText, entity.sizeText)
        assertEquals(original.sizeBytes, entity.sizeBytes)
        assertEquals(original.dateText, entity.dateText)
        assertEquals(original.seeders, entity.seeders)
        assertNull(entity.peers)
        assertEquals(1L, entity.primarySiteId)
        assertEquals("Nyaa", entity.primarySiteName)
        assertEquals("https://nyaa.si/view/1", entity.detailUrl)
        assertEquals("Nyaa,UIndex", entity.sourceNames)
        assertEquals(1700000000000L, entity.addedAt)

        val roundTripped = entity.toAggregatedMagnetItem()
        assertEquals(original.dedupKey, roundTripped.dedupKey)
        assertEquals(original.title, roundTripped.title)
        assertEquals(original.magnetUri, roundTripped.magnetUri)
        assertEquals(original.infoHash, roundTripped.infoHash)
        assertEquals(original.sizeBytes, roundTripped.sizeBytes)
        assertEquals(original.seeders, roundTripped.seeders)
        assertEquals(2, roundTripped.sources.size)
        assertEquals(1L, roundTripped.primarySource.siteId)
        assertEquals("Nyaa", roundTripped.primarySource.siteName)
        assertEquals("https://nyaa.si/view/1", roundTripped.primarySource.detailUrl)
        assertEquals("UIndex", roundTripped.sources[1].siteName)

        val fromCompanion = BookmarkEntity.fromAggregatedItem(original, addedAt = 1700000000000L)
        assertEquals(entity, fromCompanion)
        assertTrue(fromCompanion.toAggregatedItem().sources.any { it.siteName == "UIndex" })
    }
}
