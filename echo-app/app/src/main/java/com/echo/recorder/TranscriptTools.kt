package com.echo.recorder

import kotlin.math.max
import kotlin.math.min

/** Pure, unit-tested helpers: chunk planning, de-duplicated stitching, speaker labelling, excerpts. */
object TranscriptTools {
    data class Seg(val start: Double, val end: Double, val text: String)

    fun stamp(sec: Double): String {
        val s = sec.toLong().coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /** Time ranges (seconds) covering [0, durationSec] with overlap, so no word falls between pieces. */
    fun chunkPlan(durationSec: Double, chunkSec: Double, overlapSec: Double): List<Pair<Double, Double>> {
        if (durationSec <= chunkSec) return listOf(0.0 to durationSec)
        val out = ArrayList<Pair<Double, Double>>()
        var start = 0.0
        while (start < durationSec) {
            val end = min(start + chunkSec, durationSec)
            out.add(max(0.0, start - (if (start > 0) overlapSec else 0.0)) to end)
            if (end >= durationSec) break
            start = end
        }
        return out
    }

    private fun words(s: String) = s.lowercase().split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}']"), "") }.filter { it.isNotEmpty() }

    /** Drops the words at the start of [next] that merely repeat the end of [prevTail] (the overlap). */
    fun trimOverlap(prevTail: String, next: String): String {
        val a = words(prevTail).takeLast(30)
        val nextTokens = next.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val b = nextTokens.map { it.lowercase().replace(Regex("[^\\p{L}\\p{N}']"), "") }
        var best = 0
        for (k in min(a.size, b.size) downTo 1) {
            if (a.takeLast(k) == b.take(k)) { best = k; break }
        }
        return nextTokens.drop(best).joinToString(" ")
    }

    /** Appends a new chunk's segments to [acc], removing anything already covered by the overlap. */
    fun stitch(acc: List<Seg>, next: List<Seg>): List<Seg> {
        if (acc.isEmpty()) return next
        val lastEnd = acc.last().end
        val tail = acc.takeLast(6).joinToString(" ") { it.text }
        val fresh = next.filter { it.end > lastEnd + 0.2 }
        if (fresh.isEmpty()) return acc
        val first = fresh.first()
        val trimmed = if (first.start < lastEnd) trimOverlap(tail, first.text) else first.text
        val rest = fresh.drop(1)
        return acc + (if (trimmed.isBlank()) emptyList() else listOf(first.copy(text = trimmed))) + rest
    }

    /** runs = flattened [startMs, speaker, startMs, speaker, ...] */
    fun speakerAt(runs: List<Long>, tMs: Long): Int {
        var sp = -1
        var i = 0
        while (i + 1 < runs.size && runs[i] <= tMs) { sp = runs[i + 1].toInt(); i += 2 }
        return sp
    }

    private fun dominantSpeaker(runs: List<Long>, seg: Seg): Int {
        val votes = IntArray(4)
        for (k in 0..4) {
            val t = ((seg.start + (seg.end - seg.start) * k / 4.0) * 1000).toLong()
            val s = speakerAt(runs, t)
            if (s in 0..3) votes[s]++
        }
        val best = votes.indices.maxByOrNull { votes[it] } ?: 0
        return if (votes[best] == 0) -1 else best
    }

    /** "[mm:ss] text" lines; with 2+ voices, turns are labelled "Voice N:" using the live speaker timeline. */
    fun format(segs: List<Seg>, runs: List<Long>, voices: Int): String {
        val sb = StringBuilder()
        var last = -1
        for (s in segs) {
            val t = s.text.trim()
            if (t.isEmpty()) continue
            if (voices >= 2 && runs.isNotEmpty()) {
                val sp = dominantSpeaker(runs, s).let { if (it < 0) last else it }
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append('[').append(stamp(s.start)).append("] ")
                if (sp >= 0 && sp != last) sb.append("Voice ").append(sp + 1).append(": ")
                if (sp >= 0) last = sp
                sb.append(t)
            } else {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append('[').append(stamp(s.start)).append("] ").append(t)
            }
        }
        return sb.toString()
    }

    /** What was being said around a bookmark, so a bookmarked moment is never lost. */
    fun excerptAround(segs: List<Seg>, atMs: Long, windowSec: Double = 20.0): String {
        val t = atMs / 1000.0
        return segs.filter { it.end >= t - windowSec && it.start <= t + windowSec }.joinToString(" ") { it.text.trim() }.trim()
    }

    /** Splits on line boundaries so no thought is cut mid-sentence between model calls. */
    fun chunkText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = ArrayList<String>(); val cur = StringBuilder()
        for (line in text.split('\n')) {
            if (cur.isNotEmpty() && cur.length + line.length + 1 > maxChars) { out.add(cur.toString()); cur.clear() }
            if (line.length > maxChars) { line.chunked(maxChars).forEach { out.add(it) } } else {
                if (cur.isNotEmpty()) cur.append('\n'); cur.append(line)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /** Case-insensitive de-duplication that keeps first-seen order. */
    fun dedupe(items: List<String>): List<String> {
        val seen = HashSet<String>()
        return items.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "")) }
    }
}
