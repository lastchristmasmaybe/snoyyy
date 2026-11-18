package com.echo.recorder

import com.echo.recorder.TranscriptTools.Seg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptToolsTest {
    @Test fun shortAudioIsOneChunk() {
        assertEquals(listOf(0.0 to 300.0), TranscriptTools.chunkPlan(300.0, 600.0, 3.0))
    }

    @Test fun longAudioIsCoveredWithOverlapAndNoGap() {
        val plan = TranscriptTools.chunkPlan(1500.0, 600.0, 3.0)
        assertEquals(3, plan.size)
        assertEquals(0.0, plan.first().first, 0.0)
        assertEquals(1500.0, plan.last().second, 0.0)
        for (i in 1 until plan.size) assertTrue("gap before chunk $i", plan[i].first < plan[i - 1].second)   // overlap, never a hole
    }

    @Test fun overlapIsNotDuplicatedAfterStitching() {
        val a = listOf(Seg(0.0, 5.0, "We agreed to ship the beta on Friday"), Seg(5.0, 10.0, "and Rohan will send the invoice"))
        // the next piece starts 3 s early, so it repeats the end of the previous one
        val b = listOf(Seg(7.0, 10.2, "Rohan will send the invoice"), Seg(10.2, 14.0, "once the client approves"))
        val joined = TranscriptTools.stitch(a, b).joinToString(" ") { it.text }
        assertEquals("We agreed to ship the beta on Friday and Rohan will send the invoice once the client approves", joined)
    }

    @Test fun nothingIsLostWhenThereIsNoOverlap() {
        val a = listOf(Seg(0.0, 5.0, "First thing"))
        val b = listOf(Seg(5.0, 9.0, "Second thing"), Seg(9.0, 12.0, "Third thing"))
        assertEquals(3, TranscriptTools.stitch(a, b).size)
    }

    @Test fun trimOverlapKeepsGenuinelyNewWords() {
        assertEquals("and then we left", TranscriptTools.trimOverlap("so we met at noon", "at noon and then we left"))
        assertEquals("brand new sentence", TranscriptTools.trimOverlap("totally different words", "brand new sentence"))
    }

    @Test fun speakersAreLabelledFromTheLiveTimeline() {
        val segs = listOf(Seg(0.0, 3.0, "Hello there"), Seg(3.0, 6.0, "Hi, how are you"), Seg(6.0, 9.0, "Good thanks"))
        // voice 0 until 3 s, voice 1 from 3 s to 6 s, voice 0 again from 6 s
        val runs = listOf(0L, 0L, 3000L, 1L, 6000L, 0L)
        val out = TranscriptTools.format(segs, runs, 2)
        assertTrue(out, out.contains("[00:00] Voice 1: Hello there"))
        assertTrue(out, out.contains("[00:03] Voice 2: Hi, how are you"))
        assertTrue(out, out.contains("[00:06] Voice 1: Good thanks"))
    }

    @Test fun singleVoiceHasNoSpeakerLabels() {
        val out = TranscriptTools.format(listOf(Seg(0.0, 2.0, "just me talking")), emptyList(), 1)
        assertEquals("[00:00] just me talking", out)
    }

    @Test fun bookmarkExcerptCapturesWhatWasSaid() {
        val segs = listOf(Seg(0.0, 5.0, "intro"), Seg(40.0, 45.0, "the budget is fifty thousand"), Seg(120.0, 125.0, "unrelated"))
        val ex = TranscriptTools.excerptAround(segs, 42_000)
        assertTrue(ex.contains("fifty thousand")); assertTrue(!ex.contains("unrelated"))
    }

    @Test fun longTextIsSplitOnLineBoundariesWithoutLosingAnything() {
        val text = (1..400).joinToString("\n") { "[00:%02d] line number $it with some words".format(it % 60) }
        val parts = TranscriptTools.chunkText(text, 2000)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 2000 })
        assertEquals(text, parts.joinToString("\n"))
    }

    @Test fun dedupeKeepsOrderAndRemovesRepeats() {
        assertEquals(listOf("Send invoice", "Book venue"), TranscriptTools.dedupe(listOf("Send invoice", "send invoice!", "Book venue")))
    }
}
