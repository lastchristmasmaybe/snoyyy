package com.echo.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Renders the live voice-flow graph shown in the expanded notification. */
object NotificationWave {
    fun render(levels: List<Float>, paused: Boolean, w: Int = 720, h: Int = 200): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#F2EEDD"))
        val bars = 48
        val gap = 5f
        val bw = (w - gap * (bars + 1)) / bars
        val mid = h / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), 0f,
                if (paused) Color.parseColor("#889A8A") else Color.parseColor("#FF6B7D"),
                if (paused) Color.parseColor("#CBD3C2") else Color.parseColor("#E5263F"),
                Shader.TileMode.CLAMP
            )
        }
        val data = levels.takeLast(bars)
        val offset = bars - data.size
        for (i in 0 until bars) {
            val v = if (i < offset) 0.03f else data[i - offset].coerceIn(0.03f, 1f)
            val half = (h * 0.46f) * v
            val x = gap + i * (bw + gap)
            c.drawRoundRect(RectF(x, mid - half, x + bw, mid + half), bw / 2, bw / 2, paint)
        }
        return bmp
    }
}
