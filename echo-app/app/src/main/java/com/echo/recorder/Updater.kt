package com.echo.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
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

    /** Downloads the new APK into app cache; returns null on failure. */
    fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): java.io.File? = runCatching {
        val out = java.io.File(ctx.cacheDir, "updates").apply { mkdirs() }.let { java.io.File(it, "Echo.apk") }
        http.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val total = r.body!!.contentLength()
            r.body!!.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        out
    }.getOrNull()

    /** Opens the system installer; Android replaces Echo in place, keeping recordings. */
    fun install(ctx: Context, apk: java.io.File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
