package com.echo.recorder

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow

object Player {
    val playingId = MutableStateFlow<String?>(null)
    private var mp: MediaPlayer? = null

    fun toggle(r: Recording) {
        if (playingId.value == r.id) { stop(); return }
        stop()
        mp = MediaPlayer().apply {
            setDataSource(r.path)
            prepare()
            setOnCompletionListener { stop() }
            start()
        }
        playingId.value = r.id
    }

    fun stop() {
        runCatching { mp?.release() }
        mp = null
        playingId.value = null
    }
}
