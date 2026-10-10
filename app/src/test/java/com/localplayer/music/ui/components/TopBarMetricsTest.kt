package com.localplayer.music.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class TopBarMetricsTest {
    @Test fun buttonFitsInsideBarAndMeetsTouchTarget() {
        assertTrue(TopBarMetrics.ButtonSize.value >= 48f)
        assertTrue(TopBarMetrics.ButtonSize <= TopBarMetrics.Height)
    }
}
