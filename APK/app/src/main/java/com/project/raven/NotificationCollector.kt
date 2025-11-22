package com.project.raven

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationCollector : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Filter out our own notifications to avoid loops
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
        val app = sbn.packageName
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(sbn.postTime))

        val logEntry = NotificationLog(app, title, text, time)
        saveLog(logEntry)
    }

    private fun saveLog(log: NotificationLog) {
        try {
            // We append to a local JSON file. 
            // In a real app, use Room DB. For a lab demo, a file is easier to "dump".
            val file = File(filesDir, "notifications.log")
            val writer = FileWriter(file, true)
            writer.append(Gson().toJson(log) + "\n")
            writer.close()
        } catch (e: Exception) {
            Log.e("Raven", "Failed to save notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: Log when notifications are dismissed
    }
}

data class NotificationLog(
    val app: String,
    val title: String,
    val text: String,
    val time: String
)
