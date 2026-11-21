package com.lanhan.cilicili.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SiteSourceType(val label: String) {
    HTML("网页 XPath"),
    PROWLARR("Prowlarr API"),
    TORZNAB("Torznab XML"),
    ;

    companion object {
        fun fromRaw(raw: String?): SiteSourceType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: HTML
    }
}

/**
 * Raw JSON-compatible site rule schema matching `site-1.0.0.json` with optional `sourceType`.
 */
@Serializable
data class SiteRuleDto(
    val id: Long,
    val name: String,
    val info: String = "",
    val state: Boolean = true,
    val time: String = "",
    val updateTime: String = "",
    @SerialName("sourceType") val sourceType: String = "HTML",
    val request: SiteRequestDto,
    val expressionModel: ExpressionModelDto = ExpressionModelDto(),
)

@Serializable
data class SiteRequestDto(
    val method: String = "GET",
    val mainUrl: String = "",
    val searchUrl: String,
    val header: Map<String, String> = emptyMap(),
)

@Serializable
data class ExpressionModelDto(
    val group: String = "",
    val title: String = "",
    val date: String = "",
    val size: String = "",
    val url: String = "",
    val magnet: String = "",
)

enum class SiteHealthStatus(val label: String) {
    UNKNOWN("未检测"),
    OK("可用"),
    NO_RESULTS("无结果"),
    CF_CHALLENGE("需过盾"),
    HTTP_ERROR("HTTP错误"),
    NETWORK_ERROR("网络不可达"),
    PARSE_ERROR("解析异常"),
}

/**
 * Domain model representing a configured search site along with its health metadata.
 */
data class SiteRule(
    val id: Long,
    val name: String,
    val info: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val time: String,
    val updateTime: String,
    val sourceType: SiteSourceType = SiteSourceType.HTML,
    val method: String,
    val mainUrl: String,
    val searchUrl: String,
    val headers: Map<String, String>,
    val groupExpr: String,
    val titleExpr: String,
    val dateExpr: String,
    val sizeExpr: String,
    val urlExpr: String,
    val magnetExpr: String,
    val healthStatus: SiteHealthStatus = SiteHealthStatus.UNKNOWN,
    val lastLatencyMs: Long? = null,
    val lastCheckedAt: Long? = null,
    val lastErrorMessage: String? = null,
) {
    val host: String
        get() = runCatching {
            val candidate = searchUrl.ifBlank { mainUrl }
            java.net.URI(candidate).host.orEmpty()
        }.getOrDefault("")

    fun toDto(): SiteRuleDto = SiteRuleDto(
        id = id,
        name = name,
        info = info,
        state = enabled,
        time = time,
        updateTime = updateTime,
        sourceType = sourceType.name,
        request = SiteRequestDto(
            method = method,
            mainUrl = mainUrl,
            searchUrl = searchUrl,
            header = headers,
        ),
        expressionModel = ExpressionModelDto(
            group = groupExpr,
            title = titleExpr,
            date = dateExpr,
            size = sizeExpr,
            url = urlExpr,
            magnet = magnetExpr,
        ),
    )

    companion object {
        fun fromDto(dto: SiteRuleDto, sortOrder: Int): SiteRule = SiteRule(
            id = dto.id,
            name = dto.name.trim(),
            info = dto.info.trim(),
            enabled = dto.state,
            sortOrder = sortOrder,
            time = dto.time,
            updateTime = dto.updateTime,
            sourceType = SiteSourceType.fromRaw(dto.sourceType),
            method = dto.request.method.uppercase().ifBlank { "GET" },
            mainUrl = dto.request.mainUrl.trim(),
            searchUrl = dto.request.searchUrl.trim(),
            headers = dto.request.header,
            groupExpr = dto.expressionModel.group.trim(),
            titleExpr = dto.expressionModel.title.trim(),
            dateExpr = dto.expressionModel.date.trim(),
            sizeExpr = dto.expressionModel.size.trim(),
            urlExpr = dto.expressionModel.url.trim(),
            magnetExpr = dto.expressionModel.magnet.trim(),
        )
    }
}
