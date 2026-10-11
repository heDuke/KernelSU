package me.weishu.kernelsu.data.model

import org.junit.Assert.*
import org.junit.Test

class LkmAssetTest {
    private fun asset(kmi: String, page: Int?, digest: String? = "a".repeat(64)) =
        LkmAsset("$kmi-$page", "https://example.invalid/module.ko", kmi, page, 42, digest)

    @Test fun selectsKmiAndExplicitPageWithoutFixed61Fallback() {
        val assets = listOf(asset("android14-6.1", 4096), asset("android14-6.1", 16384), asset("android16-6.12", 4096))
        assertEquals(assets[2], selectLkmAsset(assets, "android16-6.12", 4096))
        assertEquals(assets[1], selectLkmAsset(assets, "android14-6.1", 16384))
    }

    @Test fun unknownAmbiguousOrUnverifiableAssetsAreRejected() {
        for (assets in listOf(emptyList(), listOf(asset("android16-6.12", 4096)),
            listOf(asset("android14-6.1", 4096, null)),
            listOf(asset("android14-6.1", 4096), asset("android14-6.1", 4096)))) {
            try { selectLkmAsset(assets, "android14-6.1", 4096); fail("Invalid asset accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
