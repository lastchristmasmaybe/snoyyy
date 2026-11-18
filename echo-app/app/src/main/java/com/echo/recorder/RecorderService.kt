package com.echo.recorder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

/**
 * Foreground service: recording survives screen lock, home-screen and
 * the app being swiped away. Owns the notification (timer + live waveform).
 */
class RecorderService : Service(), SensorEventListener {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private var accumulatedMs = 0L
    private var segmentStart = 0L
    private var paused = false
    private var running = false
    private var createdAt = 0L

    private val levels = ArrayList<Float>()
    private val bookmarks = ArrayList<Long>()
    private var levelSum = 0.0
    private var levelCount = 0
    private var peak = 0f
    private var silentCount = 0
    private var tickCount = 0
    private var lastShake = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (!paused) sample()
            publishState()
            if (tickCount++ % 5 == 0) notifyUpdate()
            handler.postDelayed(this, 100)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_BOOKMARK -> addBookmark()
            ACTION_STOP -> stopRecording()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---- lifecycle -------------------------------------------------------

    private fun startRecording() {
        if (running) return
        createdAt = System.currentTimeMillis()
        val out = File(Repository.recordingsDir, "echo_${createdAt}.m4a")
        file = out

        running = true
        segmentStart = System.currentTimeMillis()
        accumulatedMs = 0
        levels.clear(); bookmarks.clear()
        levelSum = 0.0; levelCount = 0; peak = 0f; silentCount = 0

        // Must promote to foreground immediately (microphone type needs RECORD_AUDIO).
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)

        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        } catch (e: Exception) {
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "echo:recording").apply { acquire(6 * 60 * 60 * 1000L) }

        if (Settings(this).shakeBookmark) {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        Haptics.heavy(this)
        handler.post(tick)
    }

    private fun pauseRecording() {
        if (!running || paused) return
        runCatching { recorder?.pause() }
        accumulatedMs += System.currentTimeMillis() - segmentStart
        paused = true
        Haptics.click(this)
        publishState(); notifyUpdate()
    }

    private fun resumeRecording() {
        if (!running || !paused) return
        runCatching { recorder?.resume() }
        segmentStart = System.currentTimeMillis()
        paused = false
        Haptics.click(this)
        publishState(); notifyUpdate()
    }

    private fun addBookmark() {
        if (!running) return
        bookmarks.add(elapsed())
        Haptics.doubleClick(this)
        publishState()
    }

    private fun stopRecording() {
        if (!running) { stopSelf(); return }
        running = false
        handler.removeCallbacks(tick)
        val total = elapsed()
        val ok = runCatching { recorder?.stop() }.isSuccess
        runCatching { recorder?.release() }
        recorder = null
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }

        var savedId: String? = null
        val f = file
        if (ok && f != null && f.exists() && total > 800) {
            val id = UUID.randomUUID().toString()
            val rec = Recording(
                id = id,
                title = "Recording · " + SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(createdAt)),
                path = f.absolutePath,
                createdAt = createdAt,
                durationMs = total,
                avgLevel = if (levelCount > 0) (levelSum / levelCount).toFloat() else 0f,
                peakLevel = peak,
                silenceRatio = if (levelCount > 0) silentCount.toFloat() / levelCount else 0f,
                bookmarks = bookmarks.toList(),
            )
            Repository.upsert(rec)
            savedId = id
            // AI is always on: kick off the pipeline the moment a recording lands.
            EchoApp.appScope.launchAi(applicationContext, id)
        } else {
            f?.delete()
        }
        Haptics.heavy(this)
        RecorderState.state.value = RecorderState.UiState(lastRecordingId = savedId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { recorder?.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        super.onDestroy()
    }

    // ---- sampling & state ------------------------------------------------

    private fun elapsed(): Long =
        accumulatedMs + if (!paused && running) System.currentTimeMillis() - segmentStart else 0

    private fun sample() {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        // sqrt curve makes quiet speech visible in the graph
        val lvl = sqrt((amp / 32767f).coerceIn(0f, 1f))
        levels.add(lvl)
        if (levels.size > 120) levels.removeAt(0)
        levelSum += lvl; levelCount++
        if (lvl > peak) peak = lvl
        if (lvl < 0.08f) silentCount++
    }

    private fun publishState() {
        RecorderState.state.value = RecorderState.UiState(
            status = if (paused) RecorderState.Status.Paused else RecorderState.Status.Recording,
            elapsedMs = elapsed(),
            levels = levels.toList(),
            bookmarks = bookmarks.toList(),
        )
    }

    // ---- haptic sensing: shake = bookmark -------------------------------

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
        val now = System.currentTimeMillis()
        if (g > 2.7f && now - lastShake > 2000 && running && !paused) {
            lastShake = now
            addBookmark()
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    // ---- notification ----------------------------------------------------

    private fun notifyUpdate() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun svc(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, RecorderService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val el = elapsed()
        val b = NotificationCompat.Builder(this, EchoApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setColor(0xFFFF2D46.toInt())
            .setContentTitle(if (paused) "Echo · Paused" else "Echo · Recording")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(NotificationWave.render(levels, paused))
                    .setSummaryText(if (bookmarks.isEmpty()) "Shake phone to bookmark a moment" else "${bookmarks.size} bookmark(s)")
            )
        if (paused) {
            b.setUsesChronometer(false).setContentText("Paused at " + fmt(el))
        } else {
            b.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - el)
                .setContentText("Capturing audio…")
        }
        b.addAction(0, if (paused) "Resume" else "Pause", svc(if (paused) ACTION_RESUME else ACTION_PAUSE, 1))
        b.addAction(0, "Bookmark", svc(ACTION_BOOKMARK, 2))
        b.addAction(0, "Stop", svc(ACTION_STOP, 3))
        return b.build()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_START = "com.echo.recorder.START"
        const val ACTION_PAUSE = "com.echo.recorder.PAUSE"
        const val ACTION_RESUME = "com.echo.recorder.RESUME"
        const val ACTION_STOP = "com.echo.recorder.STOP"
        const val ACTION_BOOKMARK = "com.echo.recorder.BOOKMARK"

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, RecorderService::class.java).setAction(action)
            if (action == ACTION_START) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun fmt(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
            else "%02d:%02d".format(s / 60, s % 60)
        }
    }
}
