package com.echo.recorder

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

fun CoroutineScope.launchAi(ctx: Context, id: String, manual: Boolean = false) {
    launch { AiAgent.process(ctx, id, manual) }
}

data class Insight(val emoji: String, val title: String, val body: String)

/**
 * Always-on AI layer.
 *  - Local (no key needed): smart tagging + recommendations from audio stats and habits.
 *  - Cloud (keys in Settings): Groq Whisper -> Groq Llama summary, title, action items, tags.
 */
object AiAgent {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun process(ctx: Context, id: String, manual: Boolean = false) {
        var rec = Repository.get(id) ?: return
        val s = Settings(ctx)

        // 1) Instant local intelligence
        rec = rec.copy(tags = localTags(rec), aiStatus = "working")
        Repository.upsert(rec)

        if (s.groqKey.isBlank() || (!s.autoTranscribe && !manual)) {
            Repository.upsert(rec.copy(aiStatus = "local"))
            return
        }
        try {
            val key = s.groqKey
            val memory = Repository.items.value.filter { it.id != rec.id && it.summary != null }.take(10)
            val memoryText = memory.joinToString("\n") { "- ${it.title}: ${it.summary} People: ${it.people.joinToString()}" }
            val glossary = memory.flatMap { it.people.map { p -> p.substringAfter("= ", p) } }.distinct().joinToString(", ")

            // 2) Full transcript: every part of the audio, with timestamps, nothing skipped.
            val tr = transcribeAll(key, File(rec.path), rec.durationMs, glossary)
            val labelled = TranscriptTools.format(tr.segs, rec.speakerRuns, rec.voices)
            rec = rec.copy(transcript = labelled)
            Repository.upsert(rec)
            if (labelled.isBlank()) {
                Repository.upsert(rec.copy(aiStatus = if (tr.gaps > 0) "error:Transcription failed, tap Run AI to retry" else "done"))
                return
            }

            // 3) Complete notes: every part of the transcript is read, plus your bookmarked moments.
            val notes = buildNotes(key, labelled, rec, memoryText)
            var transcript = labelled
            notes.speakers.forEach { (label, name) ->
                if (name.isNotBlank() && !name.startsWith("Voice")) transcript = transcript.replace("$label:", "$name:")
            }
            val bookmarked = rec.bookmarks.map { ms ->
                "🔖 [${TranscriptTools.stamp(ms / 1000.0)}] " + TranscriptTools.excerptAround(tr.segs, ms).ifBlank { "(bookmark)" }
            }
            rec = rec.copy(
                transcript = transcript,
                title = notes.title.ifBlank { rec.title },
                summary = notes.summary.ifBlank { null },
                keyPoints = notes.keyPoints,
                decisions = notes.decisions,
                actionItems = notes.actionItems,
                questions = notes.questions,
                highlights = bookmarked + notes.highlights,
                people = notes.speakers.map { (k, v) -> "$k = $v" },
                tags = (rec.tags + notes.tags).distinct(),
            )
            val status = when {
                tr.gaps > 0 -> "error:${tr.gaps} part(s) of the audio could not be transcribed. Tap Run AI to retry."
                tr.coverage < 0.8 && rec.silenceRatio < 0.6f -> "error:Transcript covers only ${(tr.coverage * 100).toInt()}% of the audio. Tap Run AI to retry."
                else -> "done"
            }
            Repository.upsert(rec.copy(aiStatus = status))
        } catch (e: Exception) {
            Repository.upsert(rec.copy(aiStatus = "error:" + (e.message ?: "failed").take(120)))
        }
    }

    /** Free tiers rate-limit; wait and retry politely instead of failing (never falls back to paid). */
    private fun execRetry(req: Request, maxTries: Int = 2, maxWaitSec: Long = 30): okhttp3.Response {
        var r = http.newCall(req).execute()
        var tries = 0
        while (r.code == 429 && tries < maxTries) {
            val wait = (r.header("retry-after")?.toLongOrNull() ?: 8L).coerceIn(2L, maxWaitSec)
            r.close(); Thread.sleep(wait * 1000); tries++
            r = http.newCall(req).execute()
        }
        return r
    }

    private const val GROQ = "https://api.groq.com/openai/v1"
    private const val MAX_UPLOAD = 20L * 1024 * 1024   // stay under Groq's 25 MB free-tier file limit

    class Transcription(val segs: List<TranscriptTools.Seg>, val gaps: Int, val coverage: Double)

    /**
     * Transcribes the whole recording. Long audio is cut (losslessly, with overlap) into pieces that fit
     * the free upload limit; each piece is primed with the previous text for continuity, then stitched
     * back together without duplicated or missing words. A failed piece is retried, then marked as a gap.
     */
    private fun transcribeAll(key: String, f: File, durationMs: Long, glossary: String): Transcription {
        val durSec = durationMs / 1000.0
        val plan: List<Pair<Double, Double>> =
            if (f.length() <= MAX_UPLOAD) listOf(0.0 to durSec) else {
                val bytesPerSec = f.length() / maxOf(durSec, 1.0)
                val chunkSec = (MAX_UPLOAD * 0.85 / bytesPerSec).coerceIn(60.0, 900.0)
                TranscriptTools.chunkPlan(durSec, chunkSec, 3.0)
            }
        var acc: List<TranscriptTools.Seg> = emptyList()
        var gaps = 0
        val tmp = File(f.parentFile, "chunk_tmp.m4a")
        for ((i, range) in plan.withIndex()) {
            val (from, to) = range
            val piece: File = if (plan.size == 1) f else {
                if (!AudioChunker.extract(f, (from * 1e6).toLong(), (to * 1e6).toLong(), tmp)) {
                    gaps++
                    acc = acc + TranscriptTools.Seg(from, to, "[⚠ this part could not be read]")
                    continue
                }
                tmp
            }
            val prompt = (glossary.take(80) + " " + acc.takeLast(3).joinToString(" ") { it.text }).trim().takeLast(220)
            val segs = runCatching { whisper(key, piece, from, prompt) }
                .recoverCatching { Thread.sleep(4000); whisper(key, piece, from, prompt) }
                .getOrNull()
            if (segs == null) {
                gaps++
                acc = acc + TranscriptTools.Seg(from, to, "[⚠ part ${i + 1} of ${plan.size} could not be transcribed]")
            } else acc = TranscriptTools.stitch(acc, segs)
        }
        runCatching { tmp.delete() }
        val spoken = acc.filter { !it.text.startsWith("[⚠") }
        val covered = if (spoken.isEmpty()) 0.0 else (spoken.last().end - spoken.first().start) / maxOf(durSec, 1.0)
        return Transcription(acc, gaps, covered)
    }

    private fun whisper(key: String, f: File, offsetSec: Double, prompt: String): List<TranscriptTools.Seg> =
        runCatching { whisperWith("whisper-large-v3", key, f, offsetSec, prompt) }
            .getOrElse { whisperWith("whisper-large-v3-turbo", key, f, offsetSec, prompt) }

    private fun whisperWith(model: String, key: String, f: File, offsetSec: Double, prompt: String): List<TranscriptTools.Seg> {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .addFormDataPart("temperature", "0")
        if (prompt.isNotBlank()) b.addFormDataPart("prompt", prompt)
        b.addFormDataPart("file", f.name, f.asRequestBody("audio/mp4".toMediaType()))
        val req = Request.Builder().url("$GROQ/audio/transcriptions")
            .header("Authorization", "Bearer $key").post(b.build()).build()
        execRetry(req, maxTries = 4, maxWaitSec = 60).use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Transcription ${r.code}: " + txt.take(80))
            val j = JSONObject(txt)
            val arr = j.optJSONArray("segments")
            if (arr == null || arr.length() == 0) {
                val t = j.optString("text").trim()
                return if (t.isEmpty()) emptyList() else listOf(TranscriptTools.Seg(offsetSec, offsetSec + j.optDouble("duration", 0.0), t))
            }
            val out = ArrayList<TranscriptTools.Seg>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // drop Whisper's well-known hallucinations on silence, keep everything that is real speech
                if (o.optDouble("no_speech_prob", 0.0) > 0.6 && o.optDouble("avg_logprob", 0.0) < -0.8) continue
                val t = o.optString("text").trim()
                if (t.isNotEmpty()) out.add(TranscriptTools.Seg(offsetSec + o.optDouble("start"), offsetSec + o.optDouble("end"), t))
            }
            return out
        }
    }

    @Volatile private var chatModel: String? = null

    /** Ask Groq which models this account can use, instead of hard-coding one that may be retired. */
    private fun resolveModel(key: String): String {
        chatModel?.let { return it }
        val req = Request.Builder().url("$GROQ/models").header("Authorization", "Bearer $key").build()
        val ids = runCatching {
            execRetry(req).use { r ->
                if (!r.isSuccessful) emptyList() else {
                    val a = JSONObject(r.body!!.string()).getJSONArray("data")
                    (0 until a.length()).map { a.getJSONObject(it) }
                        .filter { it.optBoolean("active", true) }.map { it.getString("id") }
                }
            }
        }.getOrDefault(emptyList())
        val prefer = listOf(
            "llama-3.3-70b-versatile", "openai/gpt-oss-120b", "openai/gpt-oss-20b",
            "llama-3.1-70b-versatile", "llama-3.1-8b-instant", "qwen/qwen3-32b",
            "meta-llama/llama-4-scout-17b-16e-instruct",
        )
        val skip = listOf("whisper", "guard", "tts", "orpheus", "embed", "playai", "safeguard")
        val m = prefer.firstOrNull { it in ids }
            ?: ids.firstOrNull { id -> skip.none { id.contains(it) } }
            ?: "llama-3.1-8b-instant"
        chatModel = m
        return m
    }

    private fun chat(key: String, system: String, user: String, json: Boolean): String {
        var useJson = json
        var attempt = 0
        while (true) {
            val payload = JSONObject()
                .put("model", resolveModel(key))
                .put("temperature", 0.3)
                .put("messages", org.json.JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)))
            if (useJson) payload.put("response_format", JSONObject().put("type", "json_object"))
            val req = Request.Builder().url("$GROQ/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
            execRetry(req).use { r ->
                val txt = r.body?.string().orEmpty()
                if (r.isSuccessful) return JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                attempt++
                when {
                    r.code == 429 -> error("Free limit reached, tap Run AI again later")
                    (r.code == 404 || txt.contains("model")) && attempt == 1 -> chatModel = null   // re-pick a model
                    r.code == 400 && useJson && attempt <= 2 -> useJson = false                   // model lacks JSON mode
                    else -> error("AI ${r.code}: " + txt.take(100))
                }
            }
        }
    }

    class Notes(
        val title: String, val summary: String, val keyPoints: List<String>, val decisions: List<String>,
        val actionItems: List<String>, val questions: List<String>, val highlights: List<String>,
        val speakers: List<Pair<String, String>>, val tags: List<String>,
    )

    private fun strings(a: org.json.JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.optString(it) }
    private fun parseObj(out: String): JSONObject = JSONObject(out.substring(out.indexOf('{'), out.lastIndexOf('}') + 1))

    /**
     * Map-reduce so nothing is cut off: every ~7k-character part of the transcript is read and mined for
     * points, decisions, tasks, questions and key moments; those lists are merged (never re-summarised away),
     * then one final pass writes the title, a thorough summary, tags and who the voices are.
     */
    private fun buildNotes(key: String, transcript: String, rec: Recording, memory: String): Notes {
        val parts = TranscriptTools.chunkText(transcript, 7000)
        val kp = ArrayList<String>(); val dec = ArrayList<String>(); val act = ArrayList<String>()
        val qs = ArrayList<String>(); val hl = ArrayList<String>(); val names = ArrayList<String>()
        for ((i, part) in parts.withIndex()) {
            val out = chat(
                key,
                "You extract notes from part ${i + 1} of ${parts.size} of a voice-recording transcript. Timestamps look like [mm:ss]. " +
                    "Miss NOTHING that matters: keep every name, number, date, amount, deadline, commitment and reason, quoted exactly. " +
                    "Resolve pronouns and references (he, she, that project) using the transcript and the user's earlier recordings. " +
                    "Never invent facts. Reply ONLY with JSON: {\"key_points\":[strings], \"decisions\":[strings], " +
                    "\"action_items\":[{\"task\":string,\"owner\":string,\"due\":string}], \"questions\":[open questions], " +
                    "\"highlights\":[{\"t\":\"mm:ss\",\"text\":string}] (the most important moments), \"names\":[people, places, orgs]}",
                "Earlier recordings (memory):\n" + memory.ifBlank { "(none)" } + "\n\nTranscript part:\n" + part, true
            )
            val j = runCatching { parseObj(out) }.getOrNull() ?: continue
            kp += strings(j.optJSONArray("key_points")); dec += strings(j.optJSONArray("decisions"))
            qs += strings(j.optJSONArray("questions")); names += strings(j.optJSONArray("names"))
            j.optJSONArray("action_items")?.let { a ->
                for (k in 0 until a.length()) {
                    val o = a.optJSONObject(k) ?: continue
                    val owner = o.optString("owner").takeIf { it.isNotBlank() && !it.equals("unknown", true) }
                    val due = o.optString("due").takeIf { it.isNotBlank() && !it.equals("none", true) && !it.equals("unknown", true) }
                    act += o.optString("task") + (owner?.let { " — $it" } ?: "") + (due?.let { " (by $it)" } ?: "")
                }
            }
            j.optJSONArray("highlights")?.let { a ->
                for (k in 0 until a.length()) { val o = a.optJSONObject(k) ?: continue; hl += "[${o.optString("t")}] ${o.optString("text")}" }
            }
        }
        val keyPoints = TranscriptTools.dedupe(kp); val decisions = TranscriptTools.dedupe(dec)
        val questions = TranscriptTools.dedupe(qs)
        val condensed = ("Key points:\n" + keyPoints.joinToString("\n") { "- $it" } +
            "\nDecisions:\n" + decisions.joinToString("\n") { "- $it" } +
            "\nNames: " + TranscriptTools.dedupe(names).joinToString(", ")).take(20000)
        val voices = if (rec.voices >= 2) "The transcript labels speakers Voice 1..Voice ${rec.voices}. Infer who each is from how they address each other; use a name only if the evidence is clear, else a role or keep 'Voice N'. " else ""
        val out = chat(
            key,
            "You write the final notes for a voice recording. $voices" +
                "Be thorough: the summary must cover EVERY topic discussed, in order, with the specifics (names, numbers, dates). " +
                "Resolve references using the user's earlier recordings. Never invent facts. Reply ONLY with JSON: " +
                "{\"title\":string (max 7 words), \"summary\":string (as many short paragraphs as needed, one per topic), " +
                "\"tags\":[up to 5 lowercase words], \"speakers\":{\"Voice 1\":name-or-role}}",
            "Earlier recordings (memory):\n" + memory.ifBlank { "(none)" } + "\n\nNotes gathered from the whole recording:\n" + condensed +
                "\n\nOpening of the transcript:\n" + transcript.take(2500), true
        )
        val f = runCatching { parseObj(out) }.getOrNull() ?: JSONObject()
        val sp = f.optJSONObject("speakers")
        return Notes(
            title = f.optString("title"), summary = f.optString("summary"),
            keyPoints = keyPoints, decisions = decisions, actionItems = TranscriptTools.dedupe(act),
            questions = questions, highlights = TranscriptTools.dedupe(hl),
            speakers = sp?.keys()?.asSequence()?.map { it to sp.optString(it) }?.toList() ?: emptyList(),
            tags = strings(f.optJSONArray("tags")),
        )
    }

    fun test(key: String): String { chat(key, "Reply with the single word OK.", "ping", false); return "Connected ✓ · " + chatModel }

    /** Ask your past self: answers grounded only in your recordings, with short follow-up memory. */
    fun ask(key: String, question: String, items: List<Recording>, history: String = ""): String {
        val fmt = java.text.SimpleDateFormat("EEE d MMM yyyy, h:mm a", java.util.Locale.getDefault())
        val ctx = items.filter { !it.transcript.isNullOrBlank() }.take(15).joinToString("\n\n") { r ->
            "RECORDING \"${r.title}\" (${fmt.format(java.util.Date(r.createdAt))})\n" +
                (r.summary?.let { "Summary: $it\n" } ?: "") +
                (if (r.keyPoints.isNotEmpty()) "Key points:\n" + r.keyPoints.joinToString("\n") { "- $it" } + "\n" else "") +
                (if (r.decisions.isNotEmpty()) "Decisions:\n" + r.decisions.joinToString("\n") { "- $it" } + "\n" else "") +
                (if (r.actionItems.isNotEmpty()) "Action items:\n" + r.actionItems.joinToString("\n") { "- $it" } + "\n" else "") +
                "Transcript:\n" + r.transcript!!.take(3500)
        }
        if (ctx.isBlank()) return "I don't have any transcripts yet. Record something with your Groq key set, and ask me again."
        return chat(
            key,
            "You are Echo, the user's second brain. Answer ONLY from the user's recordings below; if the answer isn't there, say so plainly. " +
                "Be accurate and complete but concise. Write plain conversational text: NO markdown symbols (no ** or #), " +
                "no bracketed citations. Use short paragraphs, or simple lines starting with '- ' for lists. " +
                "When useful, mention the recording title and time in normal words, e.g. (Status update, 1:18 pm). " +
                "Today is ${fmt.format(java.util.Date())}.",
            (if (history.isNotBlank()) "Conversation so far:\n$history\n\n" else "") +
                "Recordings:\n$ctx\n\nQuestion: $question", false
        )
    }

    private fun localTags(r: Recording): List<String> = buildList {
        val min = r.durationMs / 60000
        add(if (min >= 20) "long-form" else if (min >= 3) "session" else "quick-note")
        if (r.silenceRatio > 0.6f) add("mostly-quiet")
        if (r.avgLevel > 0.45f) add("loud")
        if (r.bookmarks.isNotEmpty()) add("bookmarked")
        if (r.voices >= 2) add("conversation")
        val h = Calendar.getInstance().apply { timeInMillis = r.createdAt }.get(Calendar.HOUR_OF_DAY)
        add(when (h) { in 5..11 -> "morning"; in 12..17 -> "afternoon"; in 18..22 -> "evening"; else -> "late-night" })
    }

    /** Suggestions for the home screen, derived from the library and current setup. */
    fun insights(items: List<Recording>, hasKeys: Boolean): List<Insight> = buildList {
        if (!hasKeys) add(Insight("✨", "Unlock transcripts", "Add API keys in Settings and every recording is transcribed and summarised automatically."))
        val last = items.firstOrNull()
        if (last != null) {
            if (last.silenceRatio > 0.6f) add(Insight("🎙️", "Last take was quiet", "Over 60% silence. Hold the phone closer or reduce distance to the speaker."))
            if (last.avgLevel > 0.6f) add(Insight("🔊", "Audio was hot", "Recording ran loud and may clip. Try a little more distance next time."))
            if (last.durationMs > 45 * 60000) add(Insight("✂️", "Long recording", "Consider splitting long sessions into chapters with shake-bookmarks."))
            last.actionItems.firstOrNull()?.let { add(Insight("✅", "Follow up", it)) }
        }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        add(when (h) {
            in 5..11 -> Insight("🌅", "Morning capture", "Dictate today's top 3 priorities in 30 seconds.")
            in 12..17 -> Insight("🧠", "Capture the idea", "Record meetings with one shake per key decision — Echo bookmarks it.")
            else -> Insight("🌙", "Evening reflection", "A 1-minute voice journal beats a page of notes.")
        })
        if (items.size >= 5) add(Insight("📈", "${items.size} recordings", "Total ${items.sumOf { it.durationMs } / 60000} min captured so far."))
    }
}
