const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const firebase = require('firebase-admin');

// --- FIREBASE INITIALIZATION (PHASE 3) ---
// Check if key exists to avoid crashing
const serviceAccountPath = path.join(__dirname, '../firebase-key.json');
let fcmReady = false;

if (fs.existsSync(serviceAccountPath)) {
    try {
        const serviceAccount = require(serviceAccountPath);
        firebase.initializeApp({
            credential: firebase.credential.cert(serviceAccount)
        });
        fcmReady = true;
        console.log('[FCM] Firebase Admin Initialized.');
    } catch (e) {
        console.error('[FCM] Initialization Failed:', e.message);
    }
} else {
    console.warn('[FCM] WARNING: firebase-key.json not found. Push notifications will be SIMULATED.');
}

// --- 1. SEND COMMAND (Wake Up Phone) ---
router.post('/command', async (req, res) => {
    const { command, payload } = req.body;
    
    console.log(`[ADMIN COMMAND] ${command}`);

    if (fcmReady) {
        // Real FCM Send
        const message = {
            data: {
                command: command,
                payload: JSON.stringify(payload || {})
            },
            topic: 'all_devices' // Target all phones subscribed to this topic
        };

        try {
            const response = await firebase.messaging().send(message);
            console.log('[FCM] Successfully sent message:', response);
            res.json({ status: 'sent', fcmId: response });
        } catch (error) {
            console.error('[FCM] Error sending message:', error);
            res.status(500).json({ status: 'error', error: error.message });
        }
    } else {
        // Simulated FCM Send
        console.log(`[FCM SIMULATION] Would send command: ${command} to topic 'all_devices'`);
        res.json({ status: 'simulated', message: 'Key missing, command logged.' });
    }
});

// --- 2. GET LOGS (Dashboard Intel) ---
router.get('/logs/:type', (req, res) => {
    const { type } = req.params;
    let filename = '';

    switch (type) {
        case 'location':
            filename = 'location_log.json';
            break;
        case 'notifications':
            filename = 'notification_log.json';
            break;
        default:
            return res.status(400).json({ error: 'Invalid log type' });
    }

    const filePath = path.join(__dirname, '../uploads', filename);

    if (fs.existsSync(filePath)) {
        // Read the file. It might be a single JSON object or multiple lines of JSON.
        // For now, let's assume it's a valid JSON file or we send it as text.
        // Since our API appends to it, it might be malformed as a single JSON file if we just appended objects.
        // Let's read it as text and let the frontend parse it.
        const content = fs.readFileSync(filePath, 'utf8');
        res.send(content);
    } else {
        res.json([]); // Return empty array if no log exists
    }
});

module.exports = router;
