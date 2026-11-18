package com.echo.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrieverTest {
    private fun rec(id: String, title: String, at: Long, transcript: String, summary: String? = null, actions: List<String> = emptyList()) =
        Recording(
            id = id, title = title, path = "", createdAt = at, durationMs = 60_000, avgLevel = 0f, peakLevel = 0f, silenceRatio = 0f,
            transcript = transcript, summary = summary, actionItems = actions,
        )

    private val budget = rec("a", "Budget call", 1_000, "[00:05] The budget for the launch is fifty thousand\n[00:20] Rohan will send the invoice", "Budget talk")
    private val hiring = rec("b", "Hiring sync", 2_000, "[00:03] We need two engineers\n[00:30] The interview loop starts Monday", "Hiring plan")

    @Test fun findsTheRightRecordingForAQuestion() {
        val hits = Retriever.search(Retriever.build(listOf(budget, hiring)), "how much is the launch budget", 3)
        assertEquals("a", hits.first().recId)
    }

    @Test fun matchesPluralsAndTenses() {
        val chunks = Retriever.build(listOf(budget, hiring))
        assertEquals("a", Retriever.search(chunks, "budgets", 1).first().recId)
        assertEquals("b", Retriever.search(chunks, "interviewing engineer", 1).first().recId)
    }

    @Test fun respectsTheDateWindow() {
        val chunks = Retriever.build(listOf(budget, hiring))
        val hits = Retriever.search(chunks, "the", 5, from = 1_500, to = 3_000)   // 'the' is a stop word -> falls back below
        assertTrue(hits.isEmpty())
        val onlyHiring = Retriever.search(chunks, "engineers interview budget", 10, from = 1_500, to = 3_000)
        assertTrue(onlyHiring.all { it.recId == "b" })
    }

    @Test fun transcriptWindowsKeepTimestamps() {
        val long = (0 until 60).joinToString("\n") { "[%02d:%02d] line $it about topic${it % 5}".format(it / 60, it % 60) }
        val chunks = Retriever.build(listOf(rec("c", "Long", 5, long))).filter { it.kind == "transcript" }
        assertTrue(chunks.size > 1)
        assertEquals("00:00", chunks.first().stamp)
        assertTrue(chunks.drop(1).all { it.stamp.isNotEmpty() })
    }

    @Test fun notesAreIndexedToo() {
        val r = rec("d", "Plans", 9, "[00:01] hello", actions = listOf("Book the venue by Friday — Priya"))
        val hits = Retriever.search(Retriever.build(listOf(r)), "venue", 2)
        assertTrue(hits.any { it.kind == "actions" })
    }

    @Test fun nothingRelevantReturnsNothing() {
        assertTrue(Retriever.search(Retriever.build(listOf(budget, hiring)), "zebra giraffe", 3).isEmpty())
    }
}
