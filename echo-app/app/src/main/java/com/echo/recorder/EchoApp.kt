package com.echo.recorder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class EchoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while Echo is recording"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val CHANNEL = "echo_recording"
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
