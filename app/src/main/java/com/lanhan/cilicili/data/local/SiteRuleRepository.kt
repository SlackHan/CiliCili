package com.lanhan.cilicili.data.local

import android.content.Context
import com.lanhan.cilicili.domain.model.SiteHealthStatus
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteRuleDto
import com.lanhan.cilicili.domain.model.SiteSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SiteRuleRepository(
    private val context: Context,
    private val dao: SiteRuleDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val allRulesFlow: Flow<List<SiteRule>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain(json) }
    }

    val enabledRulesFlow: Flow<List<SiteRule>> = dao.observeEnabled().map { entities ->
        entities.map { it.toDomain(json) }
    }

    private fun loadBundledDtos(): List<SiteRuleDto> {
        val rawJson = context.assets.open("sites_default.json")
            .bufferedReader()
            .use { it.readText() }
        return json.decodeFromString<List<SiteRuleDto>>(rawJson)
    }

    /**
     * Seeds the Room database from `assets/sites_default.json` on first launch.
     */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        val dtos = loadBundledDtos()
        val entities = dtos.mapIndexed { index, dto ->
            SiteRuleEntity.fromDomain(SiteRule.fromDto(dto, sortOrder = index), json)
        }
        dao.upsertAll(entities)
    }

    /**
     * Appends built-in public JSON_API/RSS sources (id >= [BUILTIN_PUBLIC_MIN_ID]) for existing
     * installs without overwriting user-configured rules.
     */
    suspend fun seedMissingPublicSources(): Int = withContext(Dispatchers.IO) {
        val existing = dao.getAll()
        val existingIds = existing.map { it.id }.toSet()
        val missing = loadBundledDtos().filter { dto ->
            dto.id >= BUILTIN_PUBLIC_MIN_ID && dto.id !in existingIds
        }
        if (missing.isEmpty()) return@withContext 0
        val startOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val entities = missing.mapIndexed { index, dto ->
            SiteRuleEntity.fromDomain(SiteRule.fromDto(dto, sortOrder = startOrder + index), json)
        }
        dao.upsertAll(entities)
        entities.size
    }

    suspend fun resetToDefaults(preserveApiSources: Boolean = true): Int = withContext(Dispatchers.IO) {
        val dtos = loadBundledDtos()
        val safeDtos = if (preserveApiSources) {
            dao.deleteNonApiRules()
            val protectedIds = dao.getSelfHostedApiIds().toSet()
            filterDtosForSafeImport(dtos, protectedIds, preserveApiSources = true)
        } else {
            dao.deleteAll()
            dtos
        }
        val existingMaxOrder = if (preserveApiSources) {
            dao.getAll().maxOfOrNull { it.sortOrder } ?: -1
        } else {
            -1
        }
        val entities = safeDtos.mapIndexed { index, dto ->
            SiteRuleEntity.fromDomain(SiteRule.fromDto(dto, sortOrder = existingMaxOrder + 1 + index), json)
        }
        dao.upsertAll(entities)
        entities.size
    }

    /**
     * Imports rules from a JSON string (`site-1.0.0.json` array or single object),
     * merging by site `id` and returning the imported count.
     */
    suspend fun importFromJson(
        rawJson: String,
        replaceAll: Boolean = false,
        preserveApiSources: Boolean = true,
    ): Int = withContext(Dispatchers.IO) {
        val trimmed = rawJson.trim()
        val dtos = if (trimmed.startsWith("[")) {
            json.decodeFromString<List<SiteRuleDto>>(trimmed)
        } else {
            listOf(json.decodeFromString<SiteRuleDto>(trimmed))
        }
        val protectedIds = if (preserveApiSources) dao.getSelfHostedApiIds().toSet() else emptySet()
        val safeDtos = filterDtosForSafeImport(dtos, protectedIds, preserveApiSources)
        if (replaceAll) {
            if (preserveApiSources) dao.deleteNonApiRules() else dao.deleteAll()
        }
        val existingCount = if (replaceAll && !preserveApiSources) 0 else dao.count()
        val entities = safeDtos.mapIndexed { index, dto ->
            SiteRuleEntity.fromDomain(
                SiteRule.fromDto(dto, sortOrder = existingCount + index),
                json,
            )
        }
        dao.upsertAll(entities)
        entities.size
    }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val dtos = dao.getAll().map { it.toDomain(json).toDto() }
        json.encodeToString(dtos)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    suspend fun upsertRule(rule: SiteRule) {
        dao.upsert(SiteRuleEntity.fromDomain(rule, json))
    }

    suspend fun deleteRule(id: Long) {
        dao.deleteById(id)
    }

    suspend fun nextCustomRuleId(): Long = withContext(Dispatchers.IO) {
        val maxId = dao.getAll().maxOfOrNull { it.id } ?: 20000L
        maxOf(20001L, maxId + 1L)
    }

    suspend fun nextSortOrder(): Int = withContext(Dispatchers.IO) {
        val minOrder = dao.getAll().minOfOrNull { it.sortOrder } ?: 0
        minOrder - 1
    }

    suspend fun updateHealth(
        id: Long,
        status: SiteHealthStatus,
        latencyMs: Long?,
        errorMessage: String?,
    ) {
        dao.updateHealth(
            id = id,
            status = status.name,
            latencyMs = latencyMs,
            checkedAt = System.currentTimeMillis(),
            errorMessage = errorMessage,
        )
    }

    suspend fun enableOnlyHealthy() = dao.enableOnlyHealthy()

    suspend fun enableAll() = dao.enableAll()

    companion object {
        const val BUILTIN_PUBLIC_MIN_ID = 10100L

        fun filterDtosForSafeImport(
            incoming: List<SiteRuleDto>,
            protectedApiIds: Set<Long>,
            preserveApiSources: Boolean,
        ): List<SiteRuleDto> {
            if (!preserveApiSources || protectedApiIds.isEmpty()) return incoming
            return incoming.filter { dto ->
                dto.id !in protectedApiIds || SiteSourceType.fromRaw(dto.sourceType).isSelfHostedApi
            }
        }
    }
}
