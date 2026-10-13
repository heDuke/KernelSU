package me.weishu.kernelsu.data.model

import org.junit.Assert.*
import org.junit.Test

class LkmAssetTest {
    private fun asset(kmi: String, page: Int?, digest: String? = "a".repeat(64), release: String? = "target-kernel") =
        LkmAsset("$kmi-$page", "https://example.invalid/module.ko", kmi, page, 42, digest, release)

    @Test fun selectsKmiAndExplicitPageWithoutFixed61Fallback() {
        val assets = listOf(asset("android14-6.1", 4096), asset("android14-6.1", 16384), asset("android16-6.12", 4096))
        assertEquals(assets[2], selectLkmAsset(assets, "android16-6.12", 4096, "target-kernel"))
        assertEquals(assets[1], selectLkmAsset(assets, "android14-6.1", 16384, "target-kernel"))
    }

    @Test fun unknownAmbiguousOrUnverifiableAssetsAreRejected() {
        for (assets in listOf(emptyList(), listOf(asset("android16-6.12", 4096)),
            listOf(asset("android14-6.1", 4096, null)),
            listOf(asset("android14-6.1", 4096), asset("android14-6.1", 4096)))) {
            try { selectLkmAsset(assets, "android14-6.1", 4096, "target-kernel"); fail("Invalid asset accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun sameKmiDoesNotPermitDifferentOrUnknownKernelRelease() {
        for (candidate in listOf(asset("android14-6.1", 4096, release = "6.1.166-dirty"),
            asset("android14-6.1", 4096, release = null), asset("android14-6.1", null))) {
            try { selectLkmAsset(listOf(candidate), "android14-6.1", 4096, "6.1.172-canary"); fail("Cross-target module accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun targetMetadataMustMatchPayloadAndBeComplete() {
        val asset = asset("android14-6.1", null, release = null)
        val target = LkmTarget(asset.name, 42, "a".repeat(64), asset.kmi, "6.1.166-dirty", 4096)
        assertEquals("6.1.166-dirty", applyLkmTargets(listOf(asset), listOf(target)).single().kernelRelease)
        for (targets in listOf(emptyList(), listOf(target, target), listOf(target.copy(sha256 = "b".repeat(64))),
            listOf(target.copy(sizeBytes = 43)), listOf(target.copy(kmi = "android16-6.12")),
            listOf(target.copy(pageSizeBytes = 0)), listOf(target.copy(kernelRelease = "")))) {
            try { applyLkmTargets(listOf(asset), targets); fail("Invalid target manifest accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
