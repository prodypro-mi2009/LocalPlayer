package com.localplayer.music.playback

import com.localplayer.music.playback.PlaybackErrorPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorPolicyTest {
    private val repeatOff = 0
    private val repeatOne = 1
    private val repeatAll = 2

    @Test fun missingOrUnreadableFileSkipsAndMarksUnavailable() {
        for (code in listOf(2005, 3001, 3003, 4005)) {
            assertEquals("code $code", Decision.Skip(true), PlaybackErrorPolicy.decide(code, 0))
        }
    }

    @Test fun notProvenGoneSkipsWithoutMarking() {
        for (code in listOf(2000, 2006, 2008, 4001, 4002, 4003, 4004, 3002, 3004)) {
            assertEquals("code $code", Decision.Skip(false), PlaybackErrorPolicy.decide(code, 0))
        }
    }

    @Test fun outputOrSessionFailuresKeepTheQueueAndPause() {
        // audio track init/write, remote error, timeout, unspecified, resources reclaimed, network
        for (code in listOf(5001, 5002, 5003, 5004, 1000, 1001, 1003, 1004, 4006, 2001, 2002, 2004)) {
            assertEquals("code $code", Decision.PauseKeep, PlaybackErrorPolicy.decide(code, 0))
        }
    }

    @Test fun skippingStopsAfterTooManyInARow() {
        val limit = PlaybackErrorPolicy.MAX_CONSECUTIVE_SKIPS
        assertEquals(Decision.Skip(true), PlaybackErrorPolicy.decide(2005, limit - 1))
        assertEquals(Decision.PauseKeep, PlaybackErrorPolicy.decide(2005, limit))
        assertEquals(Decision.PauseKeep, PlaybackErrorPolicy.decide(4003, limit))
    }

    @Test fun lastEntryRemovedPausesForOffAndOneButWrapsForAll() {
        assertEquals(true, PlaybackErrorPolicy.pauseAfterRemoval(true, 3, repeatOff))
        assertEquals(true, PlaybackErrorPolicy.pauseAfterRemoval(true, 3, repeatOne))
        assertEquals(false, PlaybackErrorPolicy.pauseAfterRemoval(true, 3, repeatAll))
    }

    @Test fun middleEntryRemovedAlwaysCarriesOn() {
        for (mode in listOf(repeatOff, repeatOne, repeatAll)) {
            assertEquals(false, PlaybackErrorPolicy.pauseAfterRemoval(false, 4, mode))
        }
    }

    @Test fun emptyQueueAlwaysPauses() {
        for (mode in listOf(repeatOff, repeatOne, repeatAll)) {
            assertEquals(true, PlaybackErrorPolicy.pauseAfterRemoval(true, 0, mode))
            assertEquals(true, PlaybackErrorPolicy.pauseAfterRemoval(false, 0, mode))
        }
    }
}
