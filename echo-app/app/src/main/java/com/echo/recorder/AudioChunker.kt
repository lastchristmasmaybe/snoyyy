package com.echo.recorder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Cuts a time range out of an .m4a WITHOUT re-encoding (exact, fast, lossless). */
object AudioChunker {
    fun extract(src: File, startUs: Long, endUs: Long, out: File): Boolean {
        val ex = MediaExtractor()
        var mux: MediaMuxer? = null
        return try {
            ex.setDataSource(src.absolutePath)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) return false
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val t = mux.addTrack(fmt)
            mux.start()
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buf = ByteBuffer.allocate(1 shl 16)
            val info = MediaCodec.BufferInfo()
            var first = -1L
            var wrote = 0
            while (true) {
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                val ts = ex.sampleTime
                if (ts > endUs) break
                if (ts >= startUs) {
                    if (first < 0) first = ts
                    info.set(0, n, ts - first, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    mux.writeSampleData(t, buf, info)
                    wrote++
                }
                ex.advance()
            }
            mux.stop()
            wrote > 0
        } catch (e: Exception) {
            false
        } finally {
            runCatching { mux?.release() }
            runCatching { ex.release() }
        }
    }
}
