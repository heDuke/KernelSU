package me.weishu.kernelsu.data.repository
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
class RemoteModuleResolverTest {
    @Test fun discoversStableZipAssetsWithoutInventingModuleIdentity() {
        val candidates = RemoteModuleResolver.parse(Json.parseToJsonElement("""[
            {"tag_name":"v2","assets":[{"name":"mod.zip","browser_download_url":"https://github.com/a/b/releases/download/v2/mod.zip","size":100}, {"name":"mod.apk","browser_download_url":"https://github.com/a/b/mod.apk"}]},
            {"tag_name":"v3-beta","prerelease":true,"assets":[{"name":"beta.zip","browser_download_url":"https://github.com/a/b/beta.zip"}]}
        ]"""))
        assertEquals(1, candidates.size); assertEquals("", candidates.single().id)
        assertEquals("v2", candidates.single().version)
    }
    @Test fun rejectsUnsafeUrlsAndKeepsDeclaredIndexIdentity() {
        val candidates = RemoteModuleResolver.parse(Json.parseToJsonElement("""{"modules":[{"id":"actual.id","versions":[
          {"zipUrl":"javascript:evil","zipName":"bad.zip"},
          {"zipUrl":"https://example.org/mod.zip","version":"1"}]}]}"""))
        assertEquals("actual.id", candidates.single().id)
    }
}
