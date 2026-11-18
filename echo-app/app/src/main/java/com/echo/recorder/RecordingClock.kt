package com.echo.recorder

/** Pure timing logic (unit-tested): total recorded time excluding pauses. */
class RecordingClock {
    private var accumulated = 0L
    private var segmentStart = 0L
    private var ticking = false

    fun start(now: Long) { accumulated = 0; segmentStart = now; ticking = true }
    fun pause(now: Long) { if (ticking) { accumulated += now - segmentStart; ticking = false } }
    fun resume(now: Long) { if (!ticking) { segmentStart = now; ticking = true } }
    fun elapsed(now: Long): Long = accumulated + if (ticking) now - segmentStart else 0
    /** Final length; always counts the segment that was still running when Stop was tapped. */
    fun stop(now: Long): Long = elapsed(now).also { pause(now) }
}
