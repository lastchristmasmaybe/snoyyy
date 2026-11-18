package com.echo.recorder

/**
 * Isolation guard: anything in a recording's notes must be supported by THAT recording's own transcript.
 * Stops details from earlier recordings (or model hallucinations) leaking into a newer one.
 */
object Grounding {
    fun tokenSet(transcript: String): Set<String> = Retriever.tokens(transcript).toSet()

    fun supported(item: String, tt: Set<String>, minShare: Double = 0.4): Boolean {
        val t = Retriever.tokens(item).distinct()
        if (t.size < 2) return t.all { it in tt }
        return t.count { it in tt }.toDouble() / t.size >= minShare
    }

    fun filter(items: List<String>, tt: Set<String>): List<String> = items.filter { supported(it, tt) }

    /** Keeps only the summary sentences that are actually backed by the transcript. */
    fun summary(text: String, tt: Set<String>): String =
        text.split(Regex("(?<=[.!?])\\s+|\\n+")).filter { it.isNotBlank() && supported(it, tt, 0.35) }.joinToString(" ").trim()

    fun nameSpoken(name: String, transcript: String): Boolean =
        name.split(' ', '-', '/').any { it.length > 2 && transcript.contains(it, ignoreCase = true) }
}
