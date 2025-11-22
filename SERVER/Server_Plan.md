# PROJECT RAVEN: C2 SERVER PLAN

## 1. Architecture Overview
The C2 (Command & Control) Server is the brain of the operation. It bridges the gap between the Operator (You) and the Implant (Android Device).

**Tech Stack:**
- **Runtime:** Node.js 20 (LTS)
- **Framework:** Express.js
- **Real-Time:** Socket.io (WebRTC Signaling)
- **Database:** MongoDB (Atlas Free Tier)
- **Push Notifications:** Firebase Admin SDK

## 2. Folder Structure
```
SERVER/
├── package.json          # Dependencies
├── server.js             # Main Entry Point
├── config.js             # Configuration (Ports, Secrets)
├── firebase-key.json     # (MANUAL) Service Account Key
├── routes/
│   ├── api.js            # HTTP Endpoints (Uploads, Logs)
│   └── admin.js          # Dashboard Endpoints
├── sockets/
│   └── signaling.js      # WebRTC Logic (Offer/Answer/ICE)
├── public/               # The Admin Dashboard (Frontend)
│   ├── index.html
│   ├── css/
│   │   └── style.css
│   └── js/
│       ├── admin.js      # Dashboard Logic
│       └── webrtc.js     # Viewer Logic
└── uploads/              # Stolen Data Storage
```

## 3. Implementation Phases

### Phase 1: The Foundation
- Initialize Node.js project.
- Install dependencies (`express`, `socket.io`, `firebase-admin`, `multer`, `mongoose`).
- Create the basic HTTP server.

### Phase 2: The API (Data Ingestion)
- Implement `/api/v1/upload` endpoint.
- Handle JSON payloads (Location, Battery).
- Handle File payloads (Notification Logs).
- Save data to MongoDB/Disk.

### Phase 3: The Commander (FCM)
- Integrate `firebase-admin`.
- Create the logic to send "Wake Up" commands (`GET_LOCATION`, `START_STREAM`) to the device via FCM.

### Phase 4: The Eyes (WebRTC Signaling)
- Implement Socket.io logic.
- Handle `webrtc_offer` (from Phone).
- Handle `webrtc_answer` (from Admin).
- Handle `ice_candidate` exchange.

### Phase 5: The Dashboard (UI)
- Build a secure HTML interface.
- **Features:**
    - Map View (Location).
    - Live Video Player.
    - Command Buttons (Ping, Kill Switch).
    - Log Viewer.

## 4. Security Checklist (Ruthless)
- [ ] **Auth:** Admin Dashboard MUST be password protected (Basic Auth or Token).
- [ ] **Validation:** Reject malformed JSON to prevent crashes.
- [ ] **Resilience:** Use `pm2` for auto-restart on crash.
- [ ] **Logs:** Log every IP that accesses the Admin panel.

## 5. Deployment (Azure)
- Setup Ubuntu VM.
- Install Node.js & PM2.
- Configure Firewall (Allow Ports 80, 443, 3000).
- Map Domain (Optional) or use Static IP.
