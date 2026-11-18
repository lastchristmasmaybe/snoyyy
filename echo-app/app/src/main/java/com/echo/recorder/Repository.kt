package com.echo.recorder

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** Tiny JSON-file store; plenty for a personal recorder library. */
object Repository {
    private lateinit var dir: File
    private lateinit var index: File
    private val _items = MutableStateFlow<List<Recording>>(emptyList())
    val items: StateFlow<List<Recording>> = _items

    val recordingsDir: File get() = File(dir, "recordings").also { it.mkdirs() }

    @Synchronized
    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = context.filesDir
        index = File(dir, "library.json")
        _items.value = runCatching {
            val arr = JSONArray(index.readText())
            List(arr.length()) { Recording.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun upsert(r: Recording) {
        val list = _items.value.filter { it.id != r.id } + r
        persist(list.sortedByDescending { it.createdAt })
    }

    @Synchronized
    fun get(id: String) = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun delete(id: String) {
        get(id)?.let { File(it.path).delete() }
        persist(_items.value.filter { it.id != id })
    }

    private fun persist(list: List<Recording>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        index.writeText(arr.toString())
    }
}
