package com.echo.recorder

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Source(val recId: String, val title: String, val stamp: String)
data class Turn(
    val fromUser: Boolean, val text: String, val sources: List<Source> = emptyList(),
    val followUps: List<String> = emptyList(), val failed: Boolean = false,
)

/** The Ask conversation survives app restarts, so context is never lost. */
object ChatStore {
    private lateinit var file: File
    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns

    @Synchronized fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "ask_chat.json")
        _turns.value = runCatching {
            val a = JSONArray(file.readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Turn(
                    o.getBoolean("u"), o.getString("t"),
                    o.optJSONArray("s")?.let { s -> List(s.length()) { k -> s.getJSONObject(k).let { Source(it.getString("r"), it.getString("n"), it.optString("p")) } } } ?: emptyList(),
                    o.optJSONArray("f")?.let { f -> List(f.length()) { f.getString(it) } } ?: emptyList(),
                    o.optBoolean("x"),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized fun add(t: Turn) = set((_turns.value + t).takeLast(60))
    @Synchronized fun clear() = set(emptyList())

    private fun set(list: List<Turn>) {
        _turns.value = list
        val a = JSONArray()
        list.forEach { t ->
            a.put(JSONObject().put("u", t.fromUser).put("t", t.text).put("x", t.failed)
                .put("s", JSONArray().also { s -> t.sources.forEach { s.put(JSONObject().put("r", it.recId).put("n", it.title).put("p", it.stamp)) } })
                .put("f", JSONArray(t.followUps)))
        }
        runCatching { file.writeText(a.toString()) }
    }
}
