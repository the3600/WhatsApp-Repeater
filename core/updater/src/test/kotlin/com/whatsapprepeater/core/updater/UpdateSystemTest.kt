package com.whatsapprepeater.core.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UpdateSystemTest {

  private val installed = InstalledAppInfo(
    applicationId = "com.whatsapprepeater.android",
    versionCode = 1L,
    versionName = "1.0.0",
    signingCertSha256 = "aaaa1111bbbb2222cccc3333dddd4444eeee5555ffff66667777888899990000",
    deviceSdkInt = 34,
  )

  @Test
  fun `semantic version comparison orders 1_0_0 through 2_0_0 correctly`() {
    val v100 = SemanticVersion.parseOrNull("v1.0.0")!!
    val v110 = SemanticVersion.parseOrNull("1.1.0")!!
    val v120 = SemanticVersion.parseOrNull("v1.2.0")!!
    val v200 = SemanticVersion.parseOrNull("2.0.0")!!

    assertTrue(v100 < v110)
    assertTrue(v110 < v120)
    assertTrue(v120 < v200)
  }

  @Test
  fun `parses github release json with apk sha256 and metadata assets`() {
    val raw = """
      {
        "tag_name": "v1.1.0",
        "name": "WhatsApp Repeater 1.1.0",
        "body": "Mejoras de estabilidad y nuevo actualizador.",
        "draft": false,
        "prerelease": false,
        "published_at": "2026-11-15T20:00:00Z",
        "assets": [
          {
            "name": "WhatsAppRepeater-v1.1.0-release.apk",
            "size": 12345678,
            "browser_download_url": "https://github.com/Yojan/WhatsApp-Repeater/releases/download/v1.1.0/WhatsAppRepeater-v1.1.0-release.apk"
          },
          {
            "name": "WhatsAppRepeater-v1.1.0-release.apk.sha256",
            "size": 120,
            "browser_download_url": "https://github.com/Yojan/WhatsApp-Repeater/releases/download/v1.1.0/WhatsAppRepeater-v1.1.0-release.apk.sha256"
          },
          {
            "name": "version-metadata.json",
            "size": 240,
            "browser_download_url": "https://github.com/Yojan/WhatsApp-Repeater/releases/download/v1.1.0/version-metadata.json"
          }
        ]
      }
    """.trimIndent()

    val candidate = GitHubReleaseParser.parseSingleRelease(raw)
    assertNotNull(candidate)
    assertEquals("v1.1.0", candidate!!.tagName)
    assertTrue(candidate.apkDownloadUrl.endsWith(".apk"))
    assertTrue(candidate.sha256AssetUrl!!.endsWith(".sha256"))
    assertTrue(candidate.metadataAssetUrl!!.endsWith("version-metadata.json"))
  }

  @Test
  fun `sha256 verifier detects valid and tampered payloads`() {
    val payload = "whatsapp-repeater-apk-bytes".toByteArray()
    val actual = Sha256Verifier.computeSha256(ByteArrayInputStream(payload))
    assertTrue(Sha256Verifier.verifyHexMatch(actual, actual.uppercase()))
    assertFalse(Sha256Verifier.verifyHexMatch(actual, "0".repeat(64)))
  }

  @Test
  fun `compatibility checker approves valid signed update and rejects mismatched signature or package`() {
    val baseMeta = ReleaseMetadataDto(
      applicationId = "com.whatsapprepeater.android",
      versionCode = 2L,
      versionName = "1.1.0",
      minSdk = 28,
      sha256 = "f".repeat(64),
      signingCertSha256 = installed.signingCertSha256,
    )
    val baseCandidate = RemoteReleaseCandidate(
      tagName = "v1.1.0",
      releaseTitle = "1.1.0",
      releaseNotes = "Novedades",
      publishedAt = "2026-11-15T20:00:00Z",
      htmlUrl = null,
      apkFileName = "app.apk",
      apkDownloadUrl = "https://github.com/Yojan/WhatsApp-Repeater/releases/download/v1.1.0/app.apk",
      apkSizeBytes = 1000L,
      sha256AssetUrl = null,
      metadataAssetUrl = null,
      inlineSha256 = "f".repeat(64),
      metadata = baseMeta,
    )

    val okResult = UpdateCompatibilityChecker.evaluate(installed, baseCandidate, null)
    assertTrue(okResult is UpdateCheckResult.UpdateAvailable)

    val wrongPkg = baseCandidate.copy(
      metadata = baseMeta.copy(applicationId = "com.other.app"),
    )
    assertTrue(UpdateCompatibilityChecker.evaluate(installed, wrongPkg, null) is UpdateCheckResult.IncompatibleUpdate)

    val wrongSig = baseCandidate.copy(
      metadata = baseMeta.copy(signingCertSha256 = "0".repeat(64)),
    )
    assertTrue(UpdateCompatibilityChecker.evaluate(installed, wrongSig, null) is UpdateCheckResult.IncompatibleUpdate)

    val sameVersion = baseCandidate.copy(
      metadata = baseMeta.copy(versionCode = 1L, versionName = "1.0.0"),
    )
    assertTrue(UpdateCompatibilityChecker.evaluate(installed, sameVersion, null) is UpdateCheckResult.UpToDate)
  }

  @Test
  fun `validates downloaded apk metadata against installed package version and signature`() {
    val ok = UpdateCompatibilityChecker.validateDownloadedApkMetadata(
      installed = installed,
      apkPackageName = "com.whatsapprepeater.android",
      apkVersionCode = 2L,
      apkSigningCertSha256 = installed.signingCertSha256,
    )
    assertEquals(null, ok)

    val wrongPkg = UpdateCompatibilityChecker.validateDownloadedApkMetadata(
      installed = installed,
      apkPackageName = "com.fake.app",
      apkVersionCode = 2L,
      apkSigningCertSha256 = installed.signingCertSha256,
    )
    assertTrue(wrongPkg!!.contains("com.whatsapprepeater.android"))

    val downgrade = UpdateCompatibilityChecker.validateDownloadedApkMetadata(
      installed = installed,
      apkPackageName = "com.whatsapprepeater.android",
      apkVersionCode = 1L,
      apkSigningCertSha256 = installed.signingCertSha256,
    )
    assertTrue(downgrade!!.contains("versionCode"))

    val wrongSig = UpdateCompatibilityChecker.validateDownloadedApkMetadata(
      installed = installed,
      apkPackageName = "com.whatsapprepeater.android",
      apkVersionCode = 2L,
      apkSigningCertSha256 = "f".repeat(64),
    )
    assertTrue(wrongSig!!.contains("firma"))

    val unsignedApk = UpdateCompatibilityChecker.validateDownloadedApkMetadata(
      installed = installed,
      apkPackageName = "com.whatsapprepeater.android",
      apkVersionCode = 2L,
      apkSigningCertSha256 = null,
    )
    assertNotNull("APK sin certificado debe ser rechazado", unsignedApk)
    assertTrue(unsignedApk!!.contains("certificado"))
  }

  @Test
  fun `rejects non-https and untrusted third-party apk download hosts`() {
    assertEquals(
      null,
      UpdateCompatibilityChecker.validateDownloadUrl(
        "https://github.com/the3600/WhatsApp-Repeater/releases/download/v1.0.1/app.apk",
      ),
    )
    assertEquals(
      null,
      UpdateCompatibilityChecker.validateDownloadUrl(
        "https://objects.githubusercontent.com/github-production-release-asset/app.apk",
      ),
    )
    assertNotNull(
      UpdateCompatibilityChecker.validateDownloadUrl("http://github.com/the3600/WhatsApp-Repeater/app.apk"),
    )
    assertNotNull(
      UpdateCompatibilityChecker.validateDownloadUrl("https://evil-mirror.example.com/fake-update.apk"),
    )
  }

  @Test
  fun `ignores drafts prereleases and non https apk download urls`() {
    val draftJson = """{"tag_name":"v1.2.0","draft":true,"assets":[{"name":"app.apk","browser_download_url":"https://example.com/app.apk"}]}"""
    assertEquals(null, GitHubReleaseParser.parseSingleRelease(draftJson))

    val insecureHttpJson = """{"tag_name":"v1.2.0","draft":false,"assets":[{"name":"app.apk","browser_download_url":"http://example.com/app.apk"}]}"""
    assertEquals(null, GitHubReleaseParser.parseSingleRelease(insecureHttpJson))

    val listWithPrereleaseOnly = """[{"tag_name":"v1.2.0-beta1","draft":false,"prerelease":true,"assets":[{"name":"app.apk","browser_download_url":"https://example.com/app.apk"}]}]"""
    assertEquals(null, GitHubReleaseParser.parseReleaseList(listWithPrereleaseOnly))
  }
}

