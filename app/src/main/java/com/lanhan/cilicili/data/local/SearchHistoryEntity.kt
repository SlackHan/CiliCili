package com.lanhan.cilicili.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val keyword: String,
    val searchedAt: Long,
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun delete(keyword: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
