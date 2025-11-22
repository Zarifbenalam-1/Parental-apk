# PROJECT RAVEN: DEPLOYMENT GUIDE

## 1. Server Setup (The Brain)

You have successfully refactored the server. It is now secure and database-backed.

### **Prerequisites**
- Node.js 18+
- A Firebase Project (for Push Notifications)

### **Steps**
1.  **Install Dependencies:**
    ```bash
    cd SERVER
    npm install
    ```
2.  **Configure Firebase:**
    - Go to [Firebase Console](https://console.firebase.google.com/).
    - Create a project.
    - Go to **Project Settings > Service Accounts**.
    - Click **Generate New Private Key**.
    - Save the file as `firebase-key.json` inside the `SERVER/` folder.
    - **CRITICAL:** If you skip this, the "PING" and "WIPE" buttons will NOT work.

3.  **Start the Server:**
    ```bash
    node server.js
    ```
    - Default Admin: `admin` / `password123`
    - Dashboard: `http://localhost:3000`

---

## 2. Android Setup (The Implant)

The Android app is the "Implant". It must be built and installed on the target phone.

### **Configuration**
1.  **Open `APK/app/src/main/java/com/project/raven/AppConfig.java`**.
2.  **Change `SERVER_URL`**:
    - **Emulator:** Use `http://10.0.2.2:3000/api/v1`
    - **Real Device (Same WiFi):** Use `http://YOUR_PC_IP:3000/api/v1` (e.g., `192.168.1.5`)
    - **Real Device (Remote):** You must deploy the server to a cloud VPS (Heroku, DigitalOcean, Azure) and use that URL.

### **Building the APK**
1.  Open the `APK` folder in Android Studio.
2.  Sync Gradle.
3.  **Build > Build Bundle(s) / APK(s) > Build APK**.
4.  Transfer the `app-debug.apk` to the target phone and install it.

### **First Run**
1.  Open the app on the phone.
2.  Grant **Location** and **Notification** permissions.
3.  The app will immediately:
    - Generate a unique `deviceId`.
    - Register with FCM.
    - Upload its token to your Server.
4.  **Check the Dashboard:** Go to the "Devices" tab. You should see the new device appear as "ONLINE".

---

## 3. Troubleshooting

### **"Device not showing up in Dashboard"**
- **Check Server Logs:** Do you see `[DATA RECEIVED]` in the terminal?
- **Check Phone Logs (Logcat):** Filter for tag `Raven`.
    - If you see `java.net.ConnectException`, your `SERVER_URL` is wrong.
    - If you see `Upload Failed`, check if the server is running.

### **"Buttons don't work"**
- Did you add `firebase-key.json`?
- Check Server Logs: `[FCM] WARNING: firebase-key.json not found` means you failed step 1.2.

### **"Video Stream is Black"**
- WebRTC requires a STUN server (Google's is used by default).
- Both devices must be on networks that allow UDP traffic.
- If on different networks (e.g., WiFi vs 4G), you might need a TURN server (Advanced).
