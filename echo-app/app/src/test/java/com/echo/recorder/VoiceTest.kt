package com.echo.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceTest {
    private fun tone(hz: Double, n: Int = 1024, sr: Int = 16000, amp: Double = 8000.0) =
        ShortArray(n) { (amp * sin(2 * PI * hz * it / sr)).toInt().toShort() }

    @Test fun detectsPitchOfASineWithinFivePercent() {
        for (hz in listOf(110.0, 140.0, 200.0, 260.0)) {
            val est = Pitch.estimate(tone(hz), 1024, 16000)
            assertTrue("expected ~$hz got $est", est > hz * 0.95 && est < hz * 1.05)
        }
    }

    @Test fun silenceAndNoiseAreNotVoiced() {
        assertEquals(0f, Pitch.estimate(ShortArray(1024), 1024, 16000), 0f)
        val rnd = java.util.Random(1)
        val noise = ShortArray(1024) { (rnd.nextGaussian() * 3000).toInt().toShort() }
        assertEquals(0f, Pitch.estimate(noise, 1024, 16000), 0f)
    }

    @Test fun oneSpeakerWithPitchJitterIsOneVoice() {
        // Regression: a 2-second clip by a single person was reported as "3 voices".
        val t = SpeakerTracker(); val rnd = java.util.Random(7)
        repeat(60) { t.update((150 + rnd.nextGaussian() * 9).toFloat()) }
        assertEquals(1, t.count)
    }

    @Test fun twoDifferentVoicesAreTwoVoices() {
        val t = SpeakerTracker()
        repeat(30) { t.update(120f) }
        var last = -1
        repeat(30) { last = t.update(230f) }
        assertEquals(2, t.count)
        assertNotEquals(0, last)
    }

    @Test fun aShortBlipIsNotAVoice() {
        val t = SpeakerTracker()
        repeat(40) { t.update(150f) }
        repeat(3) { t.update(320f) }
        assertEquals(1, t.count)
    }
}
