package com.echo.recorder

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    private fun vib(c: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31)
            (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private fun play(c: Context, predefined: Int, fallbackMs: Long, fallbackAmp: Int) {
        if (!Settings(c).haptics) return
        val v = vib(c)
        if (!v.hasVibrator()) return
        val fx = if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(predefined)
        else VibrationEffect.createOneShot(fallbackMs, fallbackAmp)
        v.vibrate(fx)
    }

    fun tick(c: Context) = play(c, VibrationEffect.EFFECT_TICK, 15, 80)
    fun click(c: Context) = play(c, VibrationEffect.EFFECT_CLICK, 30, 160)
    fun heavy(c: Context) = play(c, VibrationEffect.EFFECT_HEAVY_CLICK, 60, 255)
    fun doubleClick(c: Context) = play(c, VibrationEffect.EFFECT_DOUBLE_CLICK, 80, 200)
}
