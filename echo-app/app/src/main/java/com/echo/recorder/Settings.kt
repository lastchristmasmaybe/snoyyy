package com.echo.recorder

import android.content.Context

/** API keys stay on-device in private app storage. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("echo_settings", Context.MODE_PRIVATE)
    var groqKey: String
        get() = p.getString("groq", "") ?: ""
        set(v) = p.edit().putString("groq", v.trim()).apply()
    var shakeBookmark: Boolean
        get() = p.getBoolean("shake", true)
        set(v) = p.edit().putBoolean("shake", v).apply()
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()

    /** 0 = Standard 48 kbps, 1 = High 96 kbps, 2 = Max 160 kbps */
    var quality: Int
        get() = p.getInt("quality", 1)
        set(v) = p.edit().putInt("quality", v).apply()
    val bitrate: Int get() = listOf(48_000, 96_000, 160_000)[quality.coerceIn(0, 2)]
    var autoTranscribe: Boolean
        get() = p.getBoolean("auto", true)
        set(v) = p.edit().putBoolean("auto", v).apply()
    var detectVoices: Boolean
        get() = p.getBoolean("voices", true)
        set(v) = p.edit().putBoolean("voices", v).apply()
    /** 0 = Gentle (easy), 1 = Normal, 2 = Firm (hard shake needed) */
    var shakeLevel: Int
        get() = p.getInt("shakeLevel", 1)
        set(v) = p.edit().putInt("shakeLevel", v).apply()
    val shakeThreshold: Float get() = listOf(2.5f, 3.2f, 4.0f)[shakeLevel.coerceIn(0, 2)]
    var speakAnswers: Boolean
        get() = p.getBoolean("tts", false)
        set(v) = p.edit().putBoolean("tts", v).apply()
    var speechRate: Float
        get() = p.getFloat("rate", 1.0f)
        set(v) = p.edit().putFloat("rate", v).apply()
}
