package com.echo.recorder

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val build: Int, val url: String)

/** Checks the rolling "latest" GitHub release that CI publishes on every merge to main. */
object Updater {
    private const val REPO = "lastchristmasmaybe/snoyyy"
    private val http = OkHttpClient()

    fun check(): UpdateInfo? = runCatching {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/tags/latest")
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body!!.string())
            val build = Regex("build-(\\d+)").find(j.optString("body"))?.groupValues?.get(1)?.toInt() ?: return null
            val assets = j.getJSONArray("assets")
            if (assets.length() == 0 || build <= BuildConfig.VERSION_CODE) return null
            UpdateInfo(build, assets.getJSONObject(0).getString("browser_download_url"))
        }
    }.getOrNull()
}
