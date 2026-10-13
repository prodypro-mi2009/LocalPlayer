package com.localplayer.music.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    @Test fun newerVersionsAreDetected() {
        assertTrue(VersionCompare.isNewer("v1.0.1", "1.0.0"))
        assertTrue(VersionCompare.isNewer("1.1", "1.0.9"))
        assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("1.0.0", "1.0.0-phase4"))      // final beats pre-release
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0.0"))
        assertFalse(VersionCompare.isNewer("1.0.0-rc1", "1.0.0"))
        assertFalse(VersionCompare.isNewer("0.3.0", "1.0.0"))
        assertFalse(VersionCompare.isNewer("1.0", "1.0.0"))
        assertFalse(VersionCompare.isNewer("nightly", "1.0.0"))
        assertFalse(VersionCompare.isNewer("1.0.1", "garbage"))
        assertTrue(VersionCompare.isNewer("1.0.10", "1.0.9"))
    }

    @Test fun onlyGithubReleaseDownloadsAreAllowed() {
        assertTrue(UpdateRules.isAllowedDownloadUrl("https://github.com/me/LocalPlayer/releases/download/v1.0.1/app-release.apk"))
        assertFalse(UpdateRules.isAllowedDownloadUrl("http://github.com/me/LocalPlayer/releases/download/v1/a.apk"))
        assertFalse(UpdateRules.isAllowedDownloadUrl("https://evil.example/releases/download/v1/a.apk"))
        assertFalse(UpdateRules.isAllowedDownloadUrl("https://github.com.evil.example/me/x/releases/download/v1/a.apk"))
        assertFalse(UpdateRules.isAllowedDownloadUrl("https://github.com/me/x/archive/main.zip"))
    }

    @Test fun repoNamesAreValidated() {
        assertTrue(UpdateRules.isValidRepo("me/LocalPlayer"))
        assertFalse(UpdateRules.isValidRepo(""))
        assertFalse(UpdateRules.isValidRepo("me"))
        assertFalse(UpdateRules.isValidRepo("me/x/../y"))
        assertFalse(UpdateRules.isValidRepo("me/x?y=1"))
    }

    @Test fun autoCheck_isLimitedToOncePerDayAndNeedsOptInAndRepo() {
        val day = UpdateRules.AUTO_CHECK_INTERVAL_MS
        assertTrue(UpdateRules.shouldAutoCheck(true, true, 1000L, 0L))
        assertFalse(UpdateRules.shouldAutoCheck(false, true, 1000L, 0L))
        assertFalse(UpdateRules.shouldAutoCheck(true, false, 1000L, 0L))
        assertFalse(UpdateRules.shouldAutoCheck(true, true, day, 1L + 1000L))
        assertTrue(UpdateRules.shouldAutoCheck(true, true, day + 5000L, 1000L))
        // a clock that went backwards must not block checks forever
        assertTrue(UpdateRules.shouldAutoCheck(true, true, 500L, 10_000L))
    }
}
