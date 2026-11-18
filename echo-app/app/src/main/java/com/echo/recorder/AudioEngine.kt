package com.echo.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Raw-audio recorder: AudioRecord -> AAC (MediaCodec) -> .m4a (MediaMuxer).
 * Because we hold the PCM, every frame is also analysed for loudness and voice pitch,
 * which drives the live, per-speaker coloured waveform. Fully on-device, zero cost.
 */
class AudioEngine(private val file: File) {
    @Volatile private var running = false
    @Volatile var paused = false
    @Volatile private var lvl = 0f
    @Volatile private var spk = -1
    private var thread: Thread? = null
    private val tracker = SpeakerTracker()
    val voices: Int get() = tracker.count

    private class Mux { var track = -1; var started = false }

    @SuppressLint("MissingPermission")
    fun start(): Boolean = try {
        val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) false else {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, FRAME * 8)
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); false } else {
                val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 48000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                running = true
                thread = Thread { loop(rec, codec, muxer) }.also { it.start() }
                true
            }
        }
    } catch (e: Exception) { false }

    /** Blocks briefly while the encoder flushes and the file is finalised. */
    fun stop() {
        running = false
        thread?.join(3000)
        thread = null
    }

    /** Loudest level and last voiced speaker since the previous poll; resets both. */
    fun poll(): Pair<Float, Int> {
        val r = lvl to spk
        lvl = 0f; spk = -1
        return r
    }

    private fun loop(rec: AudioRecord, codec: MediaCodec, muxer: MediaMuxer) {
        val buf = ShortArray(FRAME)
        val info = MediaCodec.BufferInfo()
        val mux = Mux()
        var samples = 0L
        try {
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, FRAME)
                if (n <= 0 || paused) continue
                analyse(buf, n)
                feed(codec, buf, n, samples * 1_000_000L / SR, false, info, muxer, mux)
                samples += n
            }
            feed(codec, buf, 0, samples * 1_000_000L / SR, true, info, muxer, mux)
            var guard = 0
            while (guard++ < 300 && !drain(codec, info, muxer, mux, 10_000)) { /* wait for EOS */ }
        } catch (_: Exception) {
        } finally {
            runCatching { rec.stop() }; rec.release()
            runCatching { codec.stop() }; codec.release()
            runCatching { if (mux.started) muxer.stop() }; runCatching { muxer.release() }
        }
    }

    private fun feed(
        codec: MediaCodec, buf: ShortArray, n: Int, ptsUs: Long, eos: Boolean,
        info: MediaCodec.BufferInfo, muxer: MediaMuxer, mux: Mux,
    ) {
        var tries = 0
        var idx = codec.dequeueInputBuffer(10_000)
        while (idx < 0 && tries++ < 50) { drain(codec, info, muxer, mux, 0); idx = codec.dequeueInputBuffer(10_000) }
        if (idx < 0) return
        val ib = codec.getInputBuffer(idx)!!
        ib.clear()
        if (n > 0) {
            ib.order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until n) ib.putShort(buf[i])
        }
        codec.queueInputBuffer(idx, 0, n * 2, ptsUs, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
        drain(codec, info, muxer, mux, 0)
    }

    /** Returns true once end-of-stream has been written. */
    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, muxer: MediaMuxer, mux: Mux, waitUs: Long): Boolean {
        while (true) {
            val o = codec.dequeueOutputBuffer(info, waitUs)
            when {
                o == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    mux.track = muxer.addTrack(codec.outputFormat); muxer.start(); mux.started = true
                }
                o >= 0 -> {
                    val ob: ByteBuffer? = codec.getOutputBuffer(o)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (ob != null && info.size > 0 && mux.started) {
                        ob.position(info.offset); ob.limit(info.offset + info.size)
                        muxer.writeSampleData(mux.track, ob, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    // ---- live analysis ---------------------------------------------------

    private fun analyse(b: ShortArray, n: Int) {
        var sum = 0.0
        for (i in 0 until n) { val v = b[i] / 32768.0; sum += v * v }
        val rms = sqrt(sum / n).toFloat()
        val level = (rms * 7f).coerceIn(0f, 1f).pow(0.6f)
        if (level > lvl) lvl = level
        if (rms > 0.012f) {
            val pitch = pitchOf(b, n, sum)
            if (pitch > 0f) spk = tracker.update(pitch)
        }
    }

    /** Autocorrelation pitch estimate for human voices (70–400 Hz); 0 if unvoiced. */
    private fun pitchOf(b: ShortArray, n: Int, energy: Double): Float {
        val minLag = SR / 400; val maxLag = SR / 70
        var bestLag = 0; var best = 0.0
        val e0 = energy * 32768.0 * 32768.0
        if (e0 <= 0) return 0f
        for (lag in minLag..maxLag) {
            var r = 0.0
            for (i in 0 until n - lag) r += b[i].toDouble() * b[i + lag]
            if (r > best) { best = r; bestLag = lag }
        }
        return if (bestLag > 0 && best / e0 > 0.35) SR.toFloat() / bestLag else 0f
    }

    companion object {
        const val SR = 16000
        const val FRAME = 1024
    }
}

/** Online clustering of voice pitch (in semitones) into up to three speakers. */
class SpeakerTracker {
    private val centroids = ArrayList<Float>()
    private var current = -1
    private var candidate = -1
    private var streak = 0
    val count: Int get() = centroids.size

    fun update(pitchHz: Float): Int {
        val p = 12f * (ln(pitchHz / 100f) / ln(2f))
        var nearest = -1; var dist = Float.MAX_VALUE
        for (i in centroids.indices) { val d = abs(centroids[i] - p); if (d < dist) { dist = d; nearest = i } }
        val label = when {
            nearest < 0 -> { centroids.add(p); 0 }
            dist <= 3.0f -> { centroids[nearest] = centroids[nearest] * 0.95f + p * 0.05f; nearest }
            centroids.size < 3 -> { centroids.add(p); centroids.size - 1 }
            else -> nearest
        }
        // hysteresis: a new speaker must hold for 3 voiced frames before the colour flips
        if (current == -1) { current = label; candidate = label; streak = 0 }
        else if (label == current) { candidate = current; streak = 0 }
        else {
            if (label == candidate) streak++ else { candidate = label; streak = 1 }
            if (streak >= 3) { current = label; streak = 0 }
        }
        return current
    }
}
