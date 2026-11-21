package com.lanhan.cilicili.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.lanhan.cilicili.domain.model.AggregatedMagnetItem
import com.lanhan.cilicili.domain.model.SiteSourceRef
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val dedupKey: String,
    val title: String,
    val magnetUri: String? = null,
    val infoHash: String? = null,
    val sizeText: String = "",
    val sizeBytes: Long = -1L,
    val dateText: String = "",
    val seeders: Int? = null,
    val peers: Int? = null,
    val primarySiteId: Long = 0L,
    val primarySiteName: String = "",
    val detailUrl: String? = null,
    val sourceNames: String = "",
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toAggregatedMagnetItem(): AggregatedMagnetItem {
        val parsedNames = sourceNames
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val sources = if (parsedNames.isEmpty()) {
            listOf(
                SiteSourceRef(
                    siteId = primarySiteId,
                    siteName = primarySiteName,
                    detailUrl = detailUrl.orEmpty(),
                    magnetUri = magnetUri,
                ),
            )
        } else {
            parsedNames.mapIndexed { index, siteName ->
                SiteSourceRef(
                    siteId = if (index == 0) primarySiteId else 0L,
                    siteName = siteName,
                    detailUrl = if (index == 0) detailUrl.orEmpty() else "",
                    magnetUri = if (index == 0) magnetUri else null,
                )
            }
        }
        return AggregatedMagnetItem(
            dedupKey = dedupKey,
            infoHash = infoHash,
            title = title,
            sizeText = sizeText,
            sizeBytes = sizeBytes,
            dateText = dateText,
            magnetUri = magnetUri,
            sources = sources,
            seeders = seeders,
        )
    }

    fun toAggregatedItem(): AggregatedMagnetItem = toAggregatedMagnetItem()

    companion object {
        fun fromAggregatedItem(
            item: AggregatedMagnetItem,
            addedAt: Long = System.currentTimeMillis(),
        ): BookmarkEntity = item.toBookmarkEntity(addedAt = addedAt)
    }
}

fun AggregatedMagnetItem.toBookmarkEntity(
    addedAt: Long = System.currentTimeMillis(),
): BookmarkEntity {
    val primary = sources.firstOrNull()
    val names = sources
        .map { it.siteName.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
    return BookmarkEntity(
        dedupKey = dedupKey,
        title = title,
        magnetUri = magnetUri,
        infoHash = infoHash,
        sizeText = sizeText,
        sizeBytes = sizeBytes,
        dateText = dateText,
        seeders = seeders,
        peers = null,
        primarySiteId = primary?.siteId ?: 0L,
        primarySiteName = primary?.siteName.orEmpty(),
        detailUrl = primary?.detailUrl?.takeIf { it.isNotBlank() },
        sourceNames = names.ifBlank { primary?.siteName.orEmpty() },
        addedAt = addedAt,
    )
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE dedupKey = :dedupKey")
    suspend fun deleteByKey(dedupKey: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}
