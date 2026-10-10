package com.localplayer.music.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageEstimateTest {
    private val mb = 1024L * 1024

    @Test fun coversBackupWorkingVerificationAndRewrite() {
        // 100 MB file: 3 coexisting cache copies + the rewrite + headroom.
        assertEquals(4 * 100 * mb + StorageEstimate.HEADROOM_BYTES, StorageEstimate.requiredBeforeEdit(100 * mb))
    }

    @Test fun isMoreConservativeThanTheOldTwiceEstimate() {
        val size = 30 * mb
        assertTrue(StorageEstimate.requiredBeforeEdit(size) > size * 2 + 16 * mb)
    }

    @Test fun afterBackupNeedsOneCopyLess() {
        val size = 10 * mb
        assertEquals(StorageEstimate.requiredBeforeEdit(size) - size, StorageEstimate.requiredAfterBackup(size))
    }

    @Test fun unknownSizeStillRequiresHeadroom() {
        assertEquals(StorageEstimate.HEADROOM_BYTES, StorageEstimate.requiredBeforeEdit(0))
        assertEquals(StorageEstimate.HEADROOM_BYTES, StorageEstimate.requiredBeforeEdit(-5))
    }
}
