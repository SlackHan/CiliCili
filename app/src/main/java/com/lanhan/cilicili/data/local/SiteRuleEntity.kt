package com.lanhan.cilicili.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "site_rules")
data class SiteRuleEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val info: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val time: String,
    val updateTime: String,
    val method: String,
    val mainUrl: String,
    val searchUrl: String,
    val headersJson: String,
    val groupExpr: String,
    val titleExpr: String,
    val dateExpr: String,
    val sizeExpr: String,
    val urlExpr: String,
    val magnetExpr: String,
    val healthStatus: String = SiteHealthStatus.UNKNOWN.name,
    val lastLatencyMs: Long? = null,
    val lastCheckedAt: Long? = null,
    val lastErrorMessage: String? = null,
    val sourceType: String = SiteSourceType.HTML.name,
) {
    fun toDomain(json: Json): SiteRule {
        val headers = runCatching {
            json.decodeFromString<Map<String, String>>(headersJson)
        }.getOrDefault(emptyMap())
        val status = runCatching {
            SiteHealthStatus.valueOf(healthStatus)
        }.getOrDefault(SiteHealthStatus.UNKNOWN)

        return SiteRule(
            id = id,
            name = name,
            info = info,
            enabled = enabled,
            sortOrder = sortOrder,
            time = time,
            updateTime = updateTime,
            sourceType = SiteSourceType.fromRaw(sourceType),
            method = method,
            mainUrl = mainUrl,
            searchUrl = searchUrl,
            headers = headers,
            groupExpr = groupExpr,
            titleExpr = titleExpr,
            dateExpr = dateExpr,
            sizeExpr = sizeExpr,
            urlExpr = urlExpr,
            magnetExpr = magnetExpr,
            healthStatus = status,
            lastLatencyMs = lastLatencyMs,
            lastCheckedAt = lastCheckedAt,
            lastErrorMessage = lastErrorMessage,
        )
    }

    companion object {
        fun fromDomain(rule: SiteRule, json: Json): SiteRuleEntity = SiteRuleEntity(
            id = rule.id,
            name = rule.name,
            info = rule.info,
            enabled = rule.enabled,
            sortOrder = rule.sortOrder,
            time = rule.time,
            updateTime = rule.updateTime,
            method = rule.method,
            mainUrl = rule.mainUrl,
            searchUrl = rule.searchUrl,
            headersJson = json.encodeToString(rule.headers),
            groupExpr = rule.groupExpr,
            titleExpr = rule.titleExpr,
            dateExpr = rule.dateExpr,
            sizeExpr = rule.sizeExpr,
            urlExpr = rule.urlExpr,
            magnetExpr = rule.magnetExpr,
            healthStatus = rule.healthStatus.name,
            lastLatencyMs = rule.lastLatencyMs,
            lastCheckedAt = rule.lastCheckedAt,
            lastErrorMessage = rule.lastErrorMessage,
            sourceType = rule.sourceType.name,
        )
    }
}

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_rules ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SiteRuleEntity>>

    @Query("SELECT * FROM site_rules WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeEnabled(): Flow<List<SiteRuleEntity>>

    @Query("SELECT * FROM site_rules ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<SiteRuleEntity>

    @Query("SELECT COUNT(*) FROM site_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SiteRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SiteRuleEntity)

    @Query("UPDATE site_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE site_rules SET enabled = 0 WHERE id IN (:ids)")
    suspend fun disableByIds(ids: List<Long>)

    @Query(
        "UPDATE site_rules SET healthStatus = :status, lastLatencyMs = :latencyMs, " +
            "lastCheckedAt = :checkedAt, lastErrorMessage = :errorMessage WHERE id = :id",
    )
    suspend fun updateHealth(
        id: Long,
        status: String,
        latencyMs: Long?,
        checkedAt: Long,
        errorMessage: String?,
    )

    @Query("UPDATE site_rules SET enabled = CASE WHEN healthStatus = 'OK' THEN 1 ELSE 0 END")
    suspend fun enableOnlyHealthy()

    @Query("UPDATE site_rules SET enabled = 1")
    suspend fun enableAll()

    @Query("DELETE FROM site_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM site_rules WHERE sourceType NOT IN ('PROWLARR', 'TORZNAB')")
    suspend fun deleteNonApiRules()

    @Query("SELECT id FROM site_rules WHERE sourceType IN ('PROWLARR', 'TORZNAB')")
    suspend fun getSelfHostedApiIds(): List<Long>

    @Query("DELETE FROM site_rules")
    suspend fun deleteAll()
}
