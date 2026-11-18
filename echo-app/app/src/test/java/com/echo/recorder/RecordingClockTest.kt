package com.echo.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingClockTest {
    @Test fun stopCountsTheSegmentThatWasStillRunning() {
        // Regression: tapping Stop used to report 0 ms, so the recording was thrown away as "too short".
        val c = RecordingClock()
        c.start(1_000)
        assertEquals(7_000L, c.stop(8_000))
    }

    @Test fun pausedTimeIsExcluded() {
        val c = RecordingClock()
        c.start(0); c.pause(2_000)          // 2 s recorded
        c.resume(10_000)                    // 8 s paused
        assertEquals(5_000L, c.stop(13_000)) // + 3 s = 5 s total
    }

    @Test fun elapsedKeepsTickingWhileRecordingAndFreezesWhenPaused() {
        val c = RecordingClock()
        c.start(0)
        assertEquals(1_500L, c.elapsed(1_500))
        c.pause(2_000)
        assertEquals(2_000L, c.elapsed(9_999))
    }

    @Test fun stoppingWhilePausedKeepsRecordedTime() {
        val c = RecordingClock()
        c.start(0); c.pause(4_000)
        assertEquals(4_000L, c.stop(60_000))
    }

    @Test fun aTwoSecondRecordingPassesTheSaveThreshold() {
        val c = RecordingClock()
        c.start(0)
        assertTrue(c.stop(2_000) > 800)   // service discards anything <= 800 ms
    }
}
