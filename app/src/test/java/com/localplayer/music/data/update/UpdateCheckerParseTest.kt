package com.localplayer.music.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerParseTest {
    private val repo = "prodypro-mi2009/LocalPlayer"
    private val checker = UpdateChecker("LocalPlayer/test")

    private fun release(
        tag: String = "v1.0.5",
        draft: Boolean = false,
        prerelease: Boolean = false,
        page: String = "https://github.com/$repo/releases/tag/v1.0.5",
        assets: String = asset("LocalPlayer-1.0.5.apk", "https://github.com/$repo/releases/download/v1.0.5/LocalPlayer-1.0.5.apk"),
    ) = """{"tag_name":"$tag","name":"Release","body":"notes","draft":$draft,"prerelease":$prerelease,
        |"html_url":"$page","assets":[$assets]}""".trimMargin()

    private fun asset(name: String, url: String, size: Long = 100, digest: String = "") =
        """{"name":"$name","browser_download_url":"$url","size":$size,"digest":"$digest"}"""

    @Test fun a_installed104_latest105_isDetected() {
        val r = checker.parseRelease(release(), repo)!!
        assertTrue(VersionCompare.isNewer(r.tag, "1.0.4"))
    }

    @Test fun b_installed105_latest104_isNotAnUpdate() {
        val r = checker.parseRelease(release(tag = "1.0.4"), repo)!!
        assertFalse(VersionCompare.isNewer(r.tag, "1.0.5"))
    }

    @Test fun c_tagsWithAndWithoutV_areBothParsed() {
        assertEquals("v1.0.5", checker.parseRelease(release(tag = "v1.0.5"), repo)!!.tag)
        assertEquals("1.0.5", checker.parseRelease(release(tag = "1.0.5"), repo)!!.tag)
        assertTrue(VersionCompare.isNewer("v1.0.5", "1.0.4"))
        assertTrue(VersionCompare.isNewer("1.0.5", "1.0.4"))
    }

    @Test fun d_draftsPrereleasesAndMalformedTagsAreIgnored() {
        assertNull(checker.parseRelease(release(draft = true), repo))
        assertNull(checker.parseRelease(release(prerelease = true), repo))
        assertNull(checker.parseRelease(release(tag = "nightly"), repo))
        assertNull(checker.parseRelease(release(tag = ""), repo))
    }

    @Test fun e_noAssetsOrNoApk_isHandled() {
        val none = checker.parseRelease(release(assets = ""), repo)!!
        assertNull(none.apkUrl)
        val onlyZip = checker.parseRelease(
            release(assets = asset("src.zip", "https://github.com/$repo/releases/download/v1.0.5/src.zip")), repo,
        )!!
        assertNull(onlyZip.apkUrl)
        assertEquals("https://github.com/$repo/releases/tag/v1.0.5", onlyZip.pageUrl)
    }

    @Test fun f_invalidJsonDoesNotCrash() {
        assertNull(checker.parseRelease("", repo))
        assertNull(checker.parseRelease("<html>rate limited</html>", repo))
        assertNull(checker.parseRelease("[]", repo))
        assertNull(checker.parseRelease("{\"message\":\"Not Found\"}", repo))
    }

    @Test fun apkFromOtherHostsOrRepositoriesIsNeverUsed() {
        val evil = checker.parseRelease(
            release(
                assets = asset("LocalPlayer.apk", "https://evil.example/releases/download/v1/LocalPlayer.apk") + "," +
                    asset("LocalPlayer2.apk", "https://github.com/someone/else/releases/download/v1/LocalPlayer2.apk"),
            ),
            repo,
        )!!
        assertNull(evil.apkUrl)
    }

    @Test fun unexpectedPageUrlFallsBackToTheRepositoryReleasesPage() {
        val r = checker.parseRelease(release(page = "https://evil.example/phish"), repo)!!
        assertEquals("https://github.com/$repo/releases", r.pageUrl)
    }

    @Test fun apkChoice_prefersTheAppApkAndSkipsDebugAndUnrelatedFiles() {
        val base = "https://github.com/$repo/releases/download/v1.0.5/"
        val json = release(
            assets = listOf(
                asset("notes.txt", base + "notes.txt"),
                asset("app-debug.apk", base + "app-debug.apk"),
                asset("other-tool.apk", base + "other-tool.apk"),
                asset("LocalPlayer-1.0.5.apk", base + "LocalPlayer-1.0.5.apk", size = 4242, digest = "sha256:ABCDEF"),
            ).joinToString(","),
        )
        val r = checker.parseRelease(json, repo)!!
        assertEquals("LocalPlayer-1.0.5.apk", r.apkName)
        assertEquals(base + "LocalPlayer-1.0.5.apk", r.apkUrl)
        assertEquals(4242L, r.apkSize)
        assertEquals("abcdef", r.apkSha256)
        assertNotNull(r.pageUrl)
    }

    @Test fun urlRulesAreExactForTheConfiguredRepository() {
        assertTrue(UpdateRules.isAllowedDownloadUrl("https://github.com/$repo/releases/download/v1/a.apk", repo))
        assertFalse(UpdateRules.isAllowedDownloadUrl("https://github.com/other/LocalPlayer/releases/download/v1/a.apk", repo))
        assertTrue(UpdateRules.isAllowedReleasePage("https://github.com/$repo/releases/tag/v1", repo))
        assertFalse(UpdateRules.isAllowedReleasePage("https://github.com/other/x/releases/tag/v1", repo))
        assertFalse(UpdateRules.isAllowedReleasePage("http://github.com/$repo/releases/tag/v1", repo))
    }
}
