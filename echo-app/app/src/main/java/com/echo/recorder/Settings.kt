package com.echo.recorder

import android.content.Context

/** API keys stay on-device in private app storage. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)
    var openAiKey: String
        get() = p.getString("openai", "") ?: ""
        set(v) = p.edit().putString("openai", v.trim()).apply()
    var anthropicKey: String
        get() = p.getString("anthropic", "") ?: ""
        set(v) = p.edit().putString("anthropic", v.trim()).apply()
    var shakeBookmark: Boolean
        get() = p.getBoolean("shake", true)
        set(v) = p.edit().putBoolean("shake", v).apply()
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()
}
