package com.project.raven

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_QUICKBOOT_POWERON) {
            
            // On boot, we just want to register the listener or do a quick check.
            // We don't necessarily need to start the foreground service immediately unless we have work.
            // But to be safe and ensure we are alive, we can start it briefly.
            
            val serviceIntent = Intent(context, RavenService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
