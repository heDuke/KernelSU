package me.weishu.kernelsu.data.model

import org.junit.Assert.*
import org.junit.Test

class LkmUpdateAssessmentTest {
    private val asset = LkmAsset("android14-6.1_kernelsu.ko", "https://example.invalid/lkm",
        "android14-6.1", 4096, 42, "a".repeat(64), "6.1.166-dirty")
    private val release = HuskyRelease("husky-v32643", "release", "https://example.invalid/release",
        "2026-10-16", "", 32643, listOf(asset), "https://example.invalid/manager.apk")

    private fun assess(value: HuskyRelease = release, installed: Long = 32640,
                       page: Int = 4096, kernel: String = "6.1.166-dirty") =
        assessLkmUpdate(value, installed, "android14-6.1", page, kernel)

    @Test fun canaryWithoutExactTargetIsInformationalAndHasNoInstallCandidate() {
        val result = assess(kernel = "6.1.172-android14-11-g3fc92ed75e44-ab16169542")
        assertEquals(LkmUpdateAvailability.NoMatchingTarget, result.availability)
        assertNull(result.candidate)
        assertTrue(result.diagnostic!!.contains("6.1.166-dirty"))
    }

    @Test fun manifestRejectionIsDifferentFromNoTargetAndPreservesManagerUpdate() {
        val rejected = release.copy(lkmAssets = emptyList(), lkmMetadataError = "unverified manifest",
            lkmMetadataDiagnostic = "SHA-256 mismatch")
        val result = assess(rejected)
        assertEquals(LkmUpdateAvailability.MetadataRejected, result.availability)
        assertEquals("SHA-256 mismatch", result.diagnostic)
        assertNull(result.candidate)
        assertEquals(release.apkDownloadUrl, rejected.apkDownloadUrl)
        assertEquals(release.versionCode, rejected.versionCode)
        assertEquals(LkmUpdateAvailability.NoMatchingTarget, assess(release.copy(lkmAssets = emptyList())).availability)
    }

    @Test fun driverVersionAndExactPageSizeControlAvailability() {
        assertEquals(LkmUpdateAvailability.Available, assess(installed = 32640).availability)
        assertEquals(LkmUpdateAvailability.UpToDate, assess(installed = 32643).availability)
        assertEquals(LkmUpdateAvailability.UpToDate, assess(installed = 32644).availability)
        assertEquals(asset, assess().candidate)
        assertEquals(LkmUpdateAvailability.NoMatchingTarget, assess(page = 16384).availability)
    }

    @Test fun malformedOrAmbiguousMatchingAssetsArePublicationErrors() {
        for (assets in listOf(listOf(asset.copy(sha256 = null)), listOf(asset, asset))) {
            val result = assess(release.copy(lkmAssets = assets))
            assertEquals(LkmUpdateAvailability.MetadataRejected, result.availability)
            assertNull(result.candidate)
        }
    }

    @Test fun unreadableDeviceTargetIsNotClaimedToBeUnsupported() {
        assertEquals(LkmUpdateAvailability.DeviceUnknown, assess(page = 0).availability)
        assertEquals(LkmUpdateAvailability.DeviceUnknown, assess(kernel = "").availability)
    }
}
