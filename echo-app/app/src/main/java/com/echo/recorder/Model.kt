package com.echo.recorder

import org.json.JSONArray
import org.json.JSONObject

data class Recording(
    val id: String,
    val title: String,
    val path: String,
    val createdAt: Long,
    val durationMs: Long,
    val avgLevel: Float,
    val peakLevel: Float,
    val silenceRatio: Float,
    val bookmarks: List<Long> = emptyList(),
    val transcript: String? = null,
    val summary: String? = null,
    val actionItems: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    /** pending | working | done | local | error:<msg> */
    val aiStatus: String = "pending",
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("path", path)
        put("createdAt", createdAt); put("durationMs", durationMs)
        put("avgLevel", avgLevel.toDouble()); put("peakLevel", peakLevel.toDouble())
        put("silenceRatio", silenceRatio.toDouble())
        put("bookmarks", JSONArray(bookmarks))
        put("transcript", transcript ?: JSONObject.NULL)
        put("summary", summary ?: JSONObject.NULL)
        put("actionItems", JSONArray(actionItems))
        put("tags", JSONArray(tags))
        put("people", JSONArray(people))
        put("aiStatus", aiStatus)
    }

    companion object {
        fun fromJson(o: JSONObject) = Recording(
            id = o.getString("id"),
            title = o.getString("title"),
            path = o.getString("path"),
            createdAt = o.getLong("createdAt"),
            durationMs = o.getLong("durationMs"),
            avgLevel = o.optDouble("avgLevel", 0.0).toFloat(),
            peakLevel = o.optDouble("peakLevel", 0.0).toFloat(),
            silenceRatio = o.optDouble("silenceRatio", 0.0).toFloat(),
            bookmarks = o.optJSONArray("bookmarks").toLongList(),
            transcript = o.optString("transcript").takeIf { it.isNotEmpty() && !o.isNull("transcript") },
            summary = o.optString("summary").takeIf { it.isNotEmpty() && !o.isNull("summary") },
            actionItems = o.optJSONArray("actionItems").toStringList(),
            tags = o.optJSONArray("tags").toStringList(),
            people = o.optJSONArray("people").toStringList(),
            aiStatus = o.optString("aiStatus", "pending"),
        )
    }
}

private fun JSONArray?.toLongList(): List<Long> =
    if (this == null) emptyList() else List(length()) { getLong(it) }

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { getString(it) }
