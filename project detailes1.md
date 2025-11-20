# Parental-Control Agent — Lab Demo: Design, Security & Implementation Plan



---

## 1. Executive summary

You need a single, auditable APK to demo in a lab environment (your examiner(s) will be present or you will have signed consent for the test devices). This document gives a full specification, architecture, API contract, security model, required permissions and justification, deployment options for lab-owned devices, a sprint-ready MVP plan, an auditable demo script, and a detailed testing plan.

Key constraints 
Must stealthy/hidden surveillance. All sensitive operations require explicit consent and visible notifications.


---

## 2. Scope & goals

**Primary goal:** Produce an APK and backend that demonstrate remote monitoring and control features on lab-owned Android devices in an auditable, secure, and demonstrable way so you can pass your semester assignment.

**Must-haves (MVP):**

1. Secure device registration + backend (mTLS + attestation + JWT issuance).
2. FCM-based wake/push command system (data-only messages to wake device).
3. First-run consent UI and signed consent audit record.
4. Live screen sharing using MediaProjection (session consent + foreground service + WebRTC ingestion).
5. Camera & microphone streaming (runtime permission + foreground service).
6. App usage reporting (`PACKAGE_USAGE_STATS`) and per-app timers.
7. Boot persistence (RECEIVE_BOOT_COMPLETED) + WorkManager for scheduled work.
8. Encrypted local storage and secure uploads for captured artifacts.


---

## 3. Non-negotiable 

**I will provide** instructions to: bypass MediaProjection consent, suppress system notifications, hide the app from launcher or from Settings, phone home silently on install without user action, abuse Accessibility APIs for surveillance, or otherwise bypass Play Protect / OS-level safeguards.

**For your demo:** obtain documented, signed consent for any device you will monitor. Keep consent records in your submission.

---

## 4. Feature list (detailed)

1. **Device registration & attestation**

   * Device auto-registers on first-run after consent; registration includes Play Integrity attestation snapshot.
   * Mutual TLS bootstrap + server issues short-lived JWT.

2. **Secure command & control**

   * Backend command queue, push wake via FCM, pull command model, secure acknowledgement.

3. **Live screen streaming**

   * MediaProjection-based capture with explicit session consent. Streams via WebRTC to server (Janus/mediasoup). Foreground service & persistent notification.

4. **Live camera & mic**

   * Start on explicit grants; stream via WebRTC.

5. **App usage & blocking**

   * Usage stats (require user to grant "Usage Access"). Block/limit apps by showing interstitial / locking launcher (within Android constraints).

6. **File and log collection (selective)**

   * Allow user to pick files for upload via SAF or allow agent to collect specified directories (requires `MANAGE_EXTERNAL_STORAGE` and Play justification if publishing).

7. **Boot persistence & resiliency**

   * Receiver for BOOT_COMPLETED; most heavy tasks launched by WorkManager.

8. **Audit & consent logs**

   * Immutable server-side logs; local signed consent records per session.

9. **Operator/admin UI**

   * Web UI for issuing commands, viewing streams, browsing audit logs.

10. **Privacy controls**

    * Data retention & deletion UI; export logs; role-based access control for operators.

---

## 5. Permissions table & justification

| Permission                      |                                        Purpose | Constraints / Alternatives                                        |
| ------------------------------- | ---------------------------------------------: | ----------------------------------------------------------------- |
| INTERNET                        |         Connect to backend & streaming servers | Required                                                          |
| RECEIVE_BOOT_COMPLETED          | Auto-register and reschedule jobs after reboot | Use sparingly; show first-run consent                             |
| FOREGROUND_SERVICE              | Show persistent notification during monitoring | Required for recording/streaming                                  |
| CAMERA                          |                          Live camera streaming | Runtime grant; visible notification required                      |
| RECORD_AUDIO                    |                           Microphone streaming | Runtime grant; visible notification required                      |
| MEDIA_PROJECTION (implicit API) |                                 Screen capture | System consent dialog required for each session                   |
| PACKAGE_USAGE_STATS             |                           App usage monitoring | User must enable Usage Access in Settings; describe clearly in UI |
| ACCESS_FINE_LOCATION            |                         Geolocation (optional) | Runtime consent; explain usage                                    |
| MANAGE_EXTERNAL_STORAGE         |      Bulk file access (avoid unless necessary) | Play-restricted; prefer SAF picker                                |

**Notes on SMS/CALL_LOG**: DO NOT request SMS or CALL_LOG permissions for Play distribution. For lab demos, observe SMS content via notification access or have the tester forward messages to a test inbox; do not claim you can silently read SMS without being the default SMS app.

---

## 6. Architecture & components

### 6.1 High-level diagram (textual)

* **Device agent (APK)**

  * Modules: Registration, Auth (mTLS/JWT), FCM listener, Command executor, Media capture (MediaProjection, Camera, Mic), WorkManager jobs, Encrypted storage, Consent manager, Boot receiver.

* **Backend**

  * Auth service (mTLS termination & JWT issuer), Device registry, Command queue (e.g., Redis + persistent queue), FCM server integration, WebRTC signaling + SFU (Janus/mediasoup), Object store (S3), Audit DB (immutable append-only logs), Operator Web UI.

* **Operator UI**

  * Login (2FA), device list, live viewer, commands, audit viewer.

### 6.2 Component interactions

1. **On first run**: agent shows consent UI → user accepts → agent performs Play Integrity attestation → mTLS bootstrap → server issues JWT → device registers.
2. **Command flow**: operator issues command → backend enqueues → sends FCM data message (wake hint) → device pulls command via TLS → device executes → uploads result/logs → server updates operator.
3. **Streaming**: operator requests live view → backend enqueues real-time stream request → device shows MediaProjection consent UI (if required) → user accepts → device starts foreground streaming to SFU → operator connects to SFU to view.

---

## 7. Secure control channel (detailed)

This is the central security design. Weakness here means the system is trash.

### 7.1 Bootstrap & device identity

1. **Build-time bootstrap** (lab variant): preconfigure `DEMO_MODE=true` and `SERVER_URL` in the APK. Do not store plaintext private keys in resources. Use build-time injected client cert or use a secure provisioning flow using mTLS.

2. **Attestation**: Use Play Integrity API (preferred) at registration to bind the device state and app fingerprint to the server.

3. **mTLS handshake**: Device possesses a client certificate generated during build or created on first secure registration (device private key remains on device keystore). The server validates the cert during the registration call.

4. **JWT issuance**: After successful mTLS and attestation, server issues a short-lived JWT for API calls. Device uses mTLS for the control plane and includes JWT for authorization.

### 7.2 Command channel

* **Push**: Backend sends minimal data-only FCM: `{ "t":"cmd_wake", "id":"<cmd_id>" }` (do not include commands in FCM). FCM is only a wake-up hint.
* **Pull**: Device hits `/device/commands/pull` with mTLS + JWT. Server returns command(s) encrypted and signed.
* **Ack**: Device acknowledges execution via `/device/commands/ack` with result metadata and upload references.

### 7.3 Streaming & media encryption

* Use **WebRTC** with DTLS-SRTP for end-to-end media encryption where possible. The SFU handles relaying to operator; DTLS ensures encrypted channels.
* For recorded artifacts uploaded by device, encrypt locally with a symmetric key stored in Android Keystore and upload to S3-compatible storage using TLS. Server decrypts using its KMS after verifying operator authorization.

### 7.4 Audit & tamper evidence

* Every operator action and device event is logged with a server-side append-only log. Use hash chaining (Merkle-like) to make tampering evident.
* Store consent records (signed JSON with device signature) alongside actions.

---

## 8. API contract (example)

**Base URL**: `https://api.redteam-lab.example`

### 8.1 Device registration

`POST /device/register`

* Auth: mTLS client cert (bootstrap) + Play Integrity token
* Body: `{ "device_id": "<id>", "app_version": "1.0.0", "attestation": "<play_integrity_blob>" }`
* Response: `{ "jwt": "<short_lived_token>", "device_profile": {...} }`

### 8.2 Pull commands

`POST /device/commands/pull`

* Auth: mTLS + JWT
* Body: `{ "device_id":"<id>", "last_ack":"<cmd_id_or_null>" }`
* Response: `{ "commands":[ {"id":"c1","type":"stream_start","params":{...}}, ... ] }`

### 8.3 Ack command

`POST /device/commands/ack`

* Auth: mTLS + JWT
* Body: `{ "command_id":"c1","status":"ok|err","result_meta":{...} }`

### 8.4 Upload artifact

`POST /device/artifacts/request-upload`

* Auth: mTLS + JWT
* Body: `{ "device_id":"<id>", "filename":"capture.mp4","size":12345 }`
* Response: `{ "upload_url":"https://s3.example/...","artifact_id":"a1" }`

---

## 9. Data model (high level)

* **Device**: id, owner_id, registered_at, last_seen, attestation_snapshot, device_profile
* **Operator**: id, username, role, 2fa_enabled
* **Command**: id, device_id, operator_id, type, params, created_at, status, acked_at
* **Event**: id, device_id, type, payload, timestamp, log_hash
* **ConsentRecord**: id, device_id, operator_id, consent_type, consent_blob, timestamp

---

## 10. Consent & audit schema (JSON example)

```json
{
  "consent_id": "consent-2025-11-20T09:00:00Z-01",
  "device_id": "device-abc123",
  "operator_id": "op-redteam",
  "consent_type": "media_projection",
  "consent_text": "Device owner (Raven) grants permission for screen capture for lab testing.",
  "timestamp": "2025-11-20T09:00:00Z",
  "device_signature": "<base64sig>",
  "server_signature": "<base64sig>"
}
```

Store this in server-side immutable store and keep the device copy.

---

## 11. Lab deployment options (ethical)

### 11.1 Recommended: Pre-install on test image (system app)

* Build a test firmware/image that includes your signed APK as a preinstalled app. This ensures the app is present immediately and can be auto-started by your image. Use this only on devices you own.
* Pros: Instant availability; complete control. Cons: Requires device flashing and OEM tooling.

### 11.2 Quick test variant: ADB install + grant perms

* Use `adb install app-lab.apk` then run:

  * `adb shell pm grant com.example.app android.permission.RECORD_AUDIO`
  * `adb shell pm grant com.example.app android.permission.CAMERA`
  * `adb shell pm grant com.example.app android.permission.ACCESS_FINE_LOCATION`
  * `adb shell am start -n com.example.app/.MainActivity`
* Use `adb shell settings put secure enabled_accessibility_services ...` only for lab-specific accessibility testing (documented consent required).

### 11.3 Device-owner provisioning (OOBE / QR / Zero-touch)

* For fleet demonstrations, provision the device as Device Owner during setup. This is the most platform-correct method to get management capabilities.
* Requires factory reset or fresh device.

**IMPORTANT:** Never use these on devices you don't own or without signed consent.

---

## 12. UX & first-run consent flow (mock)

1. Splash screen
2. **First-run consent screen**

   * Title: "Lab Demo — Remote Monitoring Consent"
   * Body: concise list of capabilities (screen capture, camera, mic, location, file upload), retention policy, contact info.
   * Checkboxes for each capability and a required checkbox: "I am the owner of this device and consent to these operations for lab testing."
   * "ALLOW & START" button (disabled until checked).
3. On tap: Show system runtime permission prompts in sequence (location/camera/mic). After all perms granted, show MediaProjection system dialog right before a stream starts.
4. Persist consent record in EncryptedSharedPreferences and upload to server.

---

## 13. Demo script (exact steps to present to examiner)

**Before demo**: Prepare a lab device flashed with `app-lab` or install via ADB. Have an operator account ready. Ensure server is running and operator UI accessible.

1. Power on device (if preinstalled) or `adb install` + `adb shell am start` to begin first-run.
2. Walk examiner through consent screen; highlight stored consent record on device and server.
3. From Operator UI, request live screen. When MediaProjection consent appears, confirm acceptance (this is the required visible consent). Start stream and show live WebRTC viewer. Show server audit log entry for the action.
4. Request camera/mic stream; show persistent notification while streaming. Demonstrate stopping stream and show audit record.
5. Demonstrate app usage report and a per-app block/timeout enforcement (explain limitations if you cannot fully block without device-owner privileges).
6. Show encrypted artifact upload flow: create a short recording and show artifact uploaded and accessible in operator UI (with access control).
7. Present test logs: attestation snapshot, mTLS session info, JWT usage, and hashed audit log chain.

---

## 14. Sprint plan (9 sprints — 2 weeks each or condense for semester)

**Sprint 1:** Backend basics (auth, device registry, FCM integration); device bootstrap skeleton.
**Sprint 2:** First-run consent UI + EncryptedSharedPreferences + registration flow + Play Integrity integration.
**Sprint 3:** Command queue + FCM wake + device pull/ack flow.
**Sprint 4:** MediaProjection + WebRTC proof-of-concept; foreground service + notification.
**Sprint 5:** Camera & mic streaming; artifact upload flow.
**Sprint 6:** Usage stats, per-app timers, UI for enforcement.
**Sprint 7:** Boot persistence & WorkManager; reliability tests.
**Sprint 8:** Audit, consent immutability, operator UI polish.
**Sprint 9:** Testing, Play Console prep (if you aim to publish), documentation and final demo prep.

Each sprint includes acceptance criteria and test cases (see Testing section).

---

## 15. Testing & red-team checklist (must-have)

* **Functional**: registration, FCM wake, pull/ack, stream start/stop, artifact upload, usage stats working.
* **Security**: mTLS validation, reject invalid certs, JWT replay prevention, server-side rate limits, test MITM detection.
* **Privacy**: consent records present for each session, deletion flows work, retention policy enforces deletions.
* **Performance**: battery drain baseline and under stream load; memory leak checks.
* **Play-compatibility (optional)**: remove restricted permissions; prepare Data Safety form.

---

## 16. Risk & mitigation (summary)

* **Risk:** App flagged by Play Protect or reviewer suspicion.

  * **Mitigation:** Be transparent in UI & metadata, avoid requesting restricted permissions, run private lab builds only for test.

* **Risk:** Legal exposure when monitoring without consent.

  * **Mitigation:** Signed consent records, operator authentication, delete data on request.

* **Risk:** Data leakage (artifacts intercepted)

  * **Mitigation:** mTLS, WebRTC DTLS, server-side KMS for stored artifacts.

---

## 17. Appendix A — Example AndroidManifest entries (safe)

```xml
<manifest package="com.redteam.lab.agent">
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.CAMERA" />
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <!-- PACKAGE_USAGE_STATS is declared but user must enable it in settings -->
</manifest>
```

---

## 18. Appendix B — Sample FCM wake payload (data-only)

```json
{
  "to":"<device_fcm_token>",
  "priority":"high",
  "data":{
    "type":"command_wake",
    "cmd_id":"cmd-12345"
  }
}
```

Device must call `/device/commands/pull` to retrieve the real command.

---

## 19. Appendix C — Sample server audit log line (immutable)

```
2025-11-20T09:12:34Z | operator:op-redteam | device:device-abc123 | action:stream_start | consent:consent-2025-11-20T09:00:00Z-01 | hash:7f3ab9...
```

---

## 20. Deliverables & what I will produce next (on request)

If you want, I will generate any one of the following now (pick one):

* **A. Android agent skeleton** (complete Gradle project scaffold: manifest, MainActivity, Consent UI, FCM listener pseudocode, WorkManager wiring).
* **B. Backend minimal implementation spec** (detailed REST endpoints, DB schema, sequence diagrams, and an example Postman collection).
* **C. Operator web UI mockups & security policy** (wireframes, RBAC model, 2FA flow).
* **D. Sprint backlog expanded into Jira-style tickets** (detailed tasks and acceptance criteria per ticket).

Pick A, B, C, or D and I'll produce it immediately.

---


## Academic Red‑Team Analysis: Detection Surface & Covert-Behavior Risk Profile 

### 2. Defensive Detection Techniques (Conceptual Overview)

* **Static code analysis** (manifest, permissions, API signatures)
* **Dynamic behavioral analysis** (runtime hooks, accessibility events, service lifetime, network telemetry)
* **Heuristic scoring models** comparing app behavior to known-malware clusters
* **Reputation services** using certificate history, developer identity, and similar metadata
* **Policy-based device management rules** in enterprise or enhanced-protection Android builds

### 3. Legitimate vs. Covert Agent Distinction

* Legitimate parental-control, security, monitoring, or enterprise agents operate with explicit user-facing disclosures, onboarding flows, and persistent notifications.
* Covert agents attempt to hide their purpose or presence; these behaviors are what detection systems are designed to classify and block.
* Academic analysis focuses on **understanding** these distinctions, not bypassing them.

### 4. Research Perspective for Coursework

* Highlight how mobile ecosystems attempt to balance privacy, user rights, and security.
* Demonstrate awareness of why platform rules prohibit non-transparent behaviors.
* Analyze theoretical risks and defensive rationale without engaging in evasion tactics.

### 5. Role in Your Project Documentation

* Shows mastery of security architecture.
* Demonstrates red-team awareness while respecting legal and ethical constraints.
* Strengthens academic credibility by articulating detection surfaces and threat models without violating policy.

## Architectural Diagram Set

Below are structured, text-based architectural diagrams suitable for system design review. They illustrate the overall data flow, component boundaries, and operational lifecycle of the lab parental‑control/monitoring agent.

---

### 1. High-Level System Architecture (Top-Level)

```
┌──────────────────────────────────────────────┐
│                  Operator                    │
│        (Web Dashboard / CLI Tools)           │
└──────────────────────────────┬───────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────┐
│               Control Server                 │
│  • Auth / Session Layer                      │
│  • FCM Message Dispatcher                    │
│  • REST API & WebSocket Gateway              │
│  • Data Processing Workers                   │
└──────────────────────────────┬───────────────┘
                               │
                 (FCM Downlink Push)           │
                               │
                               ▼
┌──────────────────────────────────────────────┐
│                Android Agent                 │
│  • Foreground/Background Service Manager     │
│  • Data Collection Modules                   │
│  • Event Listeners & Schedulers              │
│  • Screen Streaming / Media Capture          │
│  • Secure Uplink Channel (HTTPS)             │
└──────────────────────────────────────────────┘
```

---

### 2. Android Agent Internal Module Diagram

```
┌──────────────────────────────────────────────┐
│               Android Agent                  │
└──────────────────────────────────────────────┬────────────┬───────────────┐
                                               │            │               │
                                               ▼            ▼               ▼
                              ┌────────────────────┐ ┌────────────────────┐
                              │ Service Controller │ │ Permission Handler │
                              └─────────┬──────────┘ └─────────┬──────────┘
                                        │                      │
                         ┌──────────────┴──────────────┐       │
                         ▼                             ▼       ▼
              ┌────────────────────┐        ┌────────────────────┐
              │ Data Collection    │        │ Event Listeners     │
              │ Modules            │        │ (Boot, FCM, Timer)  │
              └──────────┬─────────┘        └──────────┬─────────┘
                         │                             │
                         ▼                             ▼
              ┌────────────────────┐        ┌────────────────────┐
              │ Media Capture      │        │ Scheduler/Worker    │
              │ (Screen, Audio)    │        │ Manager             │
              └──────────┬─────────┘        └──────────┬─────────┘
                         │                             │
                         ▼                             ▼
              ┌──────────────────────────────────────────────┐
              │         Secure Uplink Manager (HTTPS)         │
              └──────────────────────────────────────────────┘
```

---

### 3. Control Channel Architecture (FCM Activation + HTTPS Data Flow)

```
Operator → Dashboard
        → Server (Command API)
        → FCM Dispatcher
        → FCM Push Token
        → Android Device
        → Agent receives push
             • Activates module
             • Retrieves command payload
             • Runs task
        → Agent sends results via HTTPS POST
        → Server DB
        → Dashboard Live View
```

---

### 4. Screen Streaming Pipeline Architecture

```
┌──────────────────────────────────────┐
│         Screen Capture Module        │
│ (MediaProjection + Virtual Display)  │
└──────────────────────┬──────────────┘
                       │Raw Frames
                       ▼
            ┌──────────────────────────┐
            │   Compression Layer      │
            │ (H.264 encoder, scaling) │
            └──────────────┬──────────┘
                           │Encoded Chunks
                           ▼
            ┌──────────────────────────┐
            │   Network Uplink         │
            │  (Chunked HTTPS Stream)  │
            └──────────────┬──────────┘
                           │
                           ▼
            ┌──────────────────────────┐
            │   Server Stream Handler  │
            │ (WebSocket/HTTP Relay)   │
            └──────────────┬──────────┘
                           │
                           ▼
            ┌──────────────────────────┐
            │    Dashboard Viewer      │
            │ (Canvas/WebRTC/Player)   │
            └──────────────────────────┘
```

---

### 5. Boot & Runtime Lifecycle Diagram

```
Device Boots
    ▼
BroadcastReceiver (BOOT_COMPLETED)
    ▼
Start Service Controller
    ▼
Restore Schedules & Listeners
    ▼
Register for FCM
    ▼
Idle (Waiting for commands or scheduled tasks)
    ▼
Receive FCM → Activate module → Execute → Upload → Return to idle
```

---

### 6. Data Flow Security Diagram

```
┌───────────────────────┐   TLS 1.2+/mTLS   ┌─────────────────────────┐
│ Android Agent Uplink  │──────────────────→│  Control Server (API)   │
└───────────────────────┘                   └─────────┬───────────────┘
                                                      │DB Layer
                                                      ▼
                                         ┌─────────────────────────┐
                                         │Dashboard / Operator UI │
                                         └─────────────────────────┘
```

---

### 7. Operator Interaction Sequence Diagram

```
Operator → Dashboard → API Request → Server → FCM Push → Android Agent
Android Agent → Execute Task → HTTPS Upload → Server → Dashboard Live Output
```

---

These diagrams provide a complete architectural overview appropriate for academic submission, design reviews, and implementation planning.
