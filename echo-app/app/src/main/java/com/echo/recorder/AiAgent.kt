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

fun CoroutineScope.launchAi(ctx: Context, id: String) {
    launch { AiAgent.process(ctx, id) }
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

    fun process(ctx: Context, id: String) {
        var rec = Repository.get(id) ?: return
        val s = Settings(ctx)

        // 1) Instant local intelligence
        rec = rec.copy(tags = localTags(rec), aiStatus = "working")
        Repository.upsert(rec)

        if (s.groqKey.isBlank()) {
            Repository.upsert(rec.copy(aiStatus = "local"))
            return
        }
        try {
            val transcript = transcribe(s.groqKey, File(rec.path))
            rec = rec.copy(transcript = transcript)
            Repository.upsert(rec)
            if (transcript.isNotBlank()) {
                val j = summarize(s.groqKey, transcript)
                rec = rec.copy(
                    title = j.optString("title").ifBlank { rec.title },
                    summary = j.optString("summary").ifBlank { null },
                    actionItems = j.optJSONArray("action_items")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
                    tags = (rec.tags + (j.optJSONArray("tags")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList())).distinct(),
                )
            }
            Repository.upsert(rec.copy(aiStatus = "done"))
        } catch (e: Exception) {
            Repository.upsert(rec.copy(aiStatus = "error:" + (e.message ?: "failed").take(120)))
        }
    }

    private const val GROQ = "https://api.groq.com/openai/v1"

    private fun transcribe(key: String, f: File): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("file", f.name, f.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val req = Request.Builder().url("$GROQ/audio/transcriptions")
            .header("Authorization", "Bearer $key").post(body).build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Transcription ${r.code}: " + txt.take(80))
            return JSONObject(txt).optString("text")
        }
    }

    private fun chat(key: String, system: String, user: String, json: Boolean): String {
        val payload = JSONObject()
            .put("model", "llama-3.3-70b-versatile")
            .put("temperature", 0.3)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        if (json) payload.put("response_format", JSONObject().put("type", "json_object"))
        val req = Request.Builder().url("$GROQ/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("AI ${r.code}: " + txt.take(80))
            return JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    private fun summarize(key: String, transcript: String): JSONObject {
        val out = chat(
            key,
            "You are the intelligence inside a voice recorder. Reply ONLY with a JSON object with keys: " +
                "title (max 6 words), summary (2-3 sentences), action_items (array of short strings), " +
                "tags (array of up to 4 lowercase words).",
            transcript.take(24000), true
        )
        return JSONObject(out)
    }

    /** Ask your past self: answers grounded only in your recordings. */
    fun ask(key: String, question: String, items: List<Recording>): String {
        val ctx = items.filter { !it.transcript.isNullOrBlank() }.take(15).joinToString("\n\n") {
            "[" + java.text.SimpleDateFormat("d MMM h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.createdAt)) +
                " · ${it.title}]\n" + (it.summary ?: "") + "\n" + it.transcript!!.take(2500)
        }
        if (ctx.isBlank()) return "No transcripts yet. Record something with AI enabled first."
        return chat(
            key,
            "Answer the question using ONLY the user's recordings below. Be brief. If the answer isn't there, say so.",
            "Recordings:\n$ctx\n\nQuestion: $question", false
        )
    }

    private fun localTags(r: Recording): List<String> = buildList {
        val min = r.durationMs / 60000
        add(if (min >= 20) "long-form" else if (min >= 3) "session" else "quick-note")
        if (r.silenceRatio > 0.6f) add("mostly-quiet")
        if (r.avgLevel > 0.45f) add("loud")
        if (r.bookmarks.isNotEmpty()) add("bookmarked")
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
