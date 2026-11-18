package com.echo.recorder

import android.content.Context
import android.speech.tts.TextToSpeech

/** Free, on-device text-to-speech for spoken answers. */
object Voice {
    private var tts: TextToSpeech? = null

    fun speak(ctx: Context, text: String, rate: Float) {
        stop()
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                t?.setSpeechRate(rate)
                t?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "echo")
            }
        }
        tts = t
    }

    fun stop() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }
}
