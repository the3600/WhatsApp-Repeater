package com.whatsapprepeater.core.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GitHubReleaseDto(
  @SerialName("tag_name") val tagName: String = "",
  val name: String? = null,
  val body: String? = null,
  val draft: Boolean = false,
  val prerelease: Boolean = false,
  @SerialName("published_at") val publishedAt: String? = null,
  @SerialName("html_url") val htmlUrl: String? = null,
  val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
  val name: String = "",
  val size: Long = 0L,
  @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

@Serializable
data class ReleaseMetadataDto(
  val applicationId: String = "",
  val versionCode: Long = 0L,
  val versionName: String = "",
  val minSdk: Int = 28,
  val sha256: String? = null,
  val signingCertSha256: String? = null,
)

data class RemoteReleaseCandidate(
  val tagName: String,
  val releaseTitle: String,
  val releaseNotes: String,
  val publishedAt: String?,
  val htmlUrl: String?,
  val apkFileName: String,
  val apkDownloadUrl: String,
  val apkSizeBytes: Long,
  val sha256AssetUrl: String?,
  val metadataAssetUrl: String?,
  val inlineSha256: String?,
  val metadata: ReleaseMetadataDto? = null,
)

object GitHubReleaseParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun parseSingleRelease(rawJson: String): RemoteReleaseCandidate? {
    val dto = json.decodeFromString<GitHubReleaseDto>(rawJson)
    return toCandidate(dto)
  }

  fun parseReleaseList(rawJson: String): RemoteReleaseCandidate? {
    val list = json.decodeFromString<List<GitHubReleaseDto>>(rawJson)
    val stable = list.firstOrNull { !it.draft && !it.prerelease } ?: return null
    return toCandidate(stable)
  }

  fun parseMetadata(rawJson: String): ReleaseMetadataDto? =
    runCatching { json.decodeFromString<ReleaseMetadataDto>(rawJson) }.getOrNull()

  private fun toCandidate(dto: GitHubReleaseDto): RemoteReleaseCandidate? {
    if (dto.draft) return null
    val apkAsset = dto.assets.firstOrNull {
      it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.startsWith("https://")
    } ?: return null

    val shaAsset = dto.assets.firstOrNull {
      it.name.endsWith(".sha256", ignoreCase = true) ||
        it.name.equals("SHA256SUMS.txt", ignoreCase = true)
    }

    val metadataAsset = dto.assets.firstOrNull {
      it.name.equals("version-metadata.json", ignoreCase = true)
    }

    return RemoteReleaseCandidate(
      tagName = dto.tagName,
      releaseTitle = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
      releaseNotes = dto.body?.trim().orEmpty(),
      publishedAt = dto.publishedAt,
      htmlUrl = dto.htmlUrl,
      apkFileName = apkAsset.name,
      apkDownloadUrl = apkAsset.browserDownloadUrl,
      apkSizeBytes = apkAsset.size,
      sha256AssetUrl = shaAsset?.browserDownloadUrl,
      metadataAssetUrl = metadataAsset?.browserDownloadUrl,
      inlineSha256 = Sha256Verifier.extractSha256FromText(dto.body),
    )
  }
}

