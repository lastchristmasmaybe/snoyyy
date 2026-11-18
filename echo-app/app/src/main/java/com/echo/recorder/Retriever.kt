package com.echo.recorder

import kotlin.math.ln

/**
 * On-device retrieval (BM25) over every recording: summaries, notes and timestamped transcript windows.
 * Lets Ask search an unlimited memory and send the model only the passages that matter.
 */
object Retriever {
    data class Chunk(val recId: String, val title: String, val createdAt: Long, val kind: String, val stamp: String, val text: String)

    private val stop = setOf(
        "the", "an", "and", "or", "of", "to", "in", "on", "at", "for", "with", "is", "are", "was", "were", "be", "been", "it",
        "this", "that", "these", "those", "you", "he", "she", "they", "we", "me", "my", "your", "our", "their", "what", "which",
        "who", "whom", "when", "where", "why", "how", "do", "does", "did", "about", "from", "as", "by", "so", "if", "then",
        "than", "but", "not", "no", "can", "could", "should", "would", "will", "have", "has", "had", "any", "all", "some",
        "there", "here", "just", "also", "into", "over", "out", "up", "down", "tell", "said", "say",
    )

    private fun stem(word: String): String {
        var x = word
        if (x.length > 3 && x.endsWith("s") && !x.endsWith("ss")) x = x.dropLast(1)
        x = when {
            x.length > 5 && x.endsWith("ing") -> x.dropLast(3)
            x.length > 4 && x.endsWith("ed") -> x.dropLast(2)
            else -> x
        }
        if (x.length > 4 && x.endsWith("e")) x = x.dropLast(1)
        return x
    }

    fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in stop }.map { stem(it) }

    fun build(recs: List<Recording>): List<Chunk> {
        val out = ArrayList<Chunk>()
        for (r in recs) {
            val head = buildString {
                append(r.title)
                r.summary?.let { append(". ").append(it) }
                if (r.tags.isNotEmpty()) append(" Tags: ").append(r.tags.joinToString())
                if (r.people.isNotEmpty()) append(" People: ").append(r.people.joinToString())
            }
            out += Chunk(r.id, r.title, r.createdAt, "summary", "", head)
            fun group(kind: String, label: String, items: List<String>) {
                if (items.isEmpty()) return
                var cur = StringBuilder()
                for (it in items) {
                    if (cur.isNotEmpty() && cur.length + it.length > 600) {
                        out += Chunk(r.id, r.title, r.createdAt, kind, "", "$label:\n" + cur.toString().trim()); cur = StringBuilder()
                    }
                    cur.append("- ").append(it).append('\n')
                }
                if (cur.isNotEmpty()) out += Chunk(r.id, r.title, r.createdAt, kind, "", "$label:\n" + cur.toString().trim())
            }
            group("points", "Key points", r.keyPoints)
            group("decisions", "Decisions", r.decisions)
            group("actions", "Action items", r.actionItems)
            group("questions", "Open questions", r.questions)
            group("highlights", "Highlights", r.highlights)
            r.transcript?.let { t ->
                val lines = t.split('\n').filter { it.isNotBlank() }
                var i = 0
                while (i < lines.size) {
                    val sb = StringBuilder(); var j = i
                    while (j < lines.size && sb.length < 700) { sb.append(lines[j]).append('\n'); j++ }
                    val stamp = Regex("^\\[([0-9:]+)\\]").find(lines[i])?.groupValues?.get(1) ?: ""
                    out += Chunk(r.id, r.title, r.createdAt, "transcript", stamp, sb.toString().trim())
                    i = if (j >= lines.size) j else maxOf(i + 1, j - 1)   // one line of overlap between windows
                }
            }
        }
        return out
    }

    /** BM25 ranking, optional date window; notes and decisions are slightly favoured over raw transcript. */
    fun search(chunks: List<Chunk>, query: String, k: Int, from: Long? = null, to: Long? = null): List<Chunk> {
        val qs = tokens(query).distinct()
        if (qs.isEmpty()) return emptyList()
        val pool = chunks.filter { (from == null || it.createdAt >= from) && (to == null || it.createdAt <= to) }
        if (pool.isEmpty()) return emptyList()
        val docs = pool.map { tokens(it.text + " " + it.title) }
        val sets = docs.map { it.toSet() }
        val avg = docs.map { it.size }.average().coerceAtLeast(1.0)
        val n = pool.size
        val idf = qs.associateWith { q -> val df = sets.count { q in it }; ln(1.0 + (n - df + 0.5) / (df + 0.5)) }
        val k1 = 1.4; val b = 0.75
        val scored = pool.indices.map { i ->
            var score = 0.0
            val d = docs[i]
            for (q in qs) {
                val tf = d.count { it == q }
                if (tf > 0) score += idf.getValue(q) * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * d.size / avg))
            }
            if (pool[i].kind != "transcript") score *= 1.25
            i to score
        }
        return scored.filter { it.second > 0 }.sortedByDescending { it.second }.take(k).map { pool[it.first] }
    }
}
