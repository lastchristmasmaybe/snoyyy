package com.echo.recorder

import android.content.Context

/** API keys stay on-device in private app storage. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)
    var groqKey: String
        get() = p.getString("groq", "") ?: ""
        set(v) = p.edit().putString("groq", v.trim()).apply()
    var deepgramKey: String
        get() = p.getString("deepgram", "") ?: ""
        set(v) = p.edit().putString("deepgram", v.trim()).apply()
    var shakeBookmark: Boolean
        get() = p.getBoolean("shake", true)
        set(v) = p.edit().putBoolean("shake", v).apply()
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()
}
