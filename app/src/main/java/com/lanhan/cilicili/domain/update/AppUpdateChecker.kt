package com.lanhan.cilicili.domain.update

import com.lanhan.cilicili.data.network.FetchResult
import com.lanhan.cilicili.data.network.SiteHttpFetcher
import com.lanhan.cilicili.domain.model.SiteRule
import com.lanhan.cilicili.domain.model.SiteSourceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ReleaseInfo(
    val versionName: String,
    val tagName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val hasUpdate: Boolean,
    val publishedAt: String = "",
)

@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("assets")
    val assets: List<GithubReleaseAssetDto> = emptyList(),
)

@Serializable
internal data class GithubReleaseAssetDto(
    @SerialName("name")
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    @SerialName("content_type")
    val contentType: String? = null,
)

/**
 * Checks GitHub Releases Latest API for application updates and compares semantic versions.
 */
class AppUpdateChecker(
    private val fetcher: SiteHttpFetcher,
    private val latestReleaseApiUrl: String = GITHUB_LATEST_RELEASE_URL,
) {

    suspend fun checkForUpdate(currentVersion: String): Result<ReleaseInfo> {
        val rule = SiteRule(
            id = -2L,
            name = "GitHubReleaseChecker",
            info = "",
            enabled = true,
            sortOrder = 0,
            time = "",
            updateTime = "",
            method = "GET",
            mainUrl = "https://api.github.com",
            searchUrl = latestReleaseApiUrl,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28",
            ),
            sourceType = SiteSourceType.JSON_API,
            groupExpr = "",
            titleExpr = "",
            dateExpr = "",
            sizeExpr = "",
            urlExpr = "",
            magnetExpr = "",
        )

        return when (val res = fetcher.fetch(rule, latestReleaseApiUrl)) {
            is FetchResult.Success -> runCatching {
                parseReleaseJson(rawJson = res.html, currentVersion = currentVersion)
            }
            is FetchResult.HttpFailure -> Result.failure(
                IllegalStateException("GitHub API 响应失败 (${res.message})"),
            )
            is FetchResult.NetworkFailure -> Result.failure(
                IllegalStateException(res.message),
            )
            is FetchResult.ChallengeRequired -> Result.failure(
                IllegalStateException("访问受限 (HTTP ${res.statusCode})"),
            )
            is FetchResult.RedirectToMagnet -> Result.failure(
                IllegalStateException("非预期重定向"),
            )
        }
    }

    companion object {
        const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/SlackHan/CiliCili/releases/latest"
        const val GITHUB_RELEASES_PAGE_URL =
            "https://github.com/SlackHan/CiliCili/releases"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /**
         * Parses a GitHub Release JSON payload and evaluates whether it is newer than [currentVersion].
         */
        fun parseReleaseJson(rawJson: String, currentVersion: String): ReleaseInfo {
            val dto = json.decodeFromString<GithubReleaseDto>(rawJson)
            val rawTag = dto.tagName.trim()
            require(rawTag.isNotEmpty()) { "Release 响应缺少 tag_name" }

            val normalizedVersion = normalizeVersionName(rawTag)
            val fallbackHtmlUrl = dto.htmlUrl.trim().ifEmpty { GITHUB_RELEASES_PAGE_URL }
            val apkAssetUrl = dto.assets
                .firstOrNull { asset ->
                    val url = asset.browserDownloadUrl.trim()
                    url.isNotEmpty() && (
                        url.endsWith(".apk", ignoreCase = true) ||
                            asset.name.trim().endsWith(".apk", ignoreCase = true)
                        )
                }
                ?.browserDownloadUrl
                ?.trim()

            val downloadUrl = apkAssetUrl?.takeIf { it.isNotEmpty() } ?: fallbackHtmlUrl
            val title = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: rawTag
            val changelog = dto.body?.trim()?.takeIf { it.isNotEmpty() } ?: "暂无版本变更说明"
            val hasUpdate = isNewerVersion(currentVersion = currentVersion, latestTag = rawTag)

            return ReleaseInfo(
                versionName = normalizedVersion,
                tagName = rawTag,
                title = title,
                changelog = changelog,
                downloadUrl = downloadUrl,
                htmlUrl = fallbackHtmlUrl,
                hasUpdate = hasUpdate,
                publishedAt = dto.publishedAt?.trim().orEmpty(),
            )
        }

        /**
         * Compares [currentVersion] and [latestTag] semantically (`MAJOR.MINOR.PATCH`).
         * Automatically strips leading `v`/`V` prefixes and any pre-release (`-alpha`, `-rc.1`)
         * or build metadata (`+build`) suffixes.
         */
        fun isNewerVersion(currentVersion: String, latestTag: String): Boolean {
            val currentSegments = parseVersionSegments(currentVersion)
            val latestSegments = parseVersionSegments(latestTag)
            if (latestSegments.isEmpty()) return false
            if (currentSegments.isEmpty()) return true

            val maxSegments = maxOf(3, currentSegments.size, latestSegments.size)
            for (i in 0 until maxSegments) {
                val cur = currentSegments.getOrElse(i) { 0 }
                val lat = latestSegments.getOrElse(i) { 0 }
                if (lat > cur) return true
                if (lat < cur) return false
            }
            return false
        }

        internal fun normalizeVersionName(rawTag: String): String {
            val trimmed = rawTag.trim().removePrefix("v").removePrefix("V").trim()
            return trimmed.ifEmpty { rawTag.trim() }
        }

        internal fun parseVersionSegments(raw: String): List<Int> {
            val cleaned = raw
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .trim()
                .substringBefore('-')
                .substringBefore('+')
                .trim()
            if (cleaned.isEmpty()) return emptyList()
            val parts = cleaned.split('.')
            if (parts.any { it.isEmpty() || it.toIntOrNull() == null }) {
                return emptyList()
            }
            return parts.map { it.toInt() }
        }
    }
}
