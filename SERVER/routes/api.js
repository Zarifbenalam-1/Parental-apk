const express = require('express');
const router = express.Router();
const { Device, Log } = require('../models');

module.exports = (io) => {

    // --- 1. UPLOAD DATA (Location, Battery, etc.) ---
    router.post('/upload', async (req, res) => {
        try {
            const { deviceId, type, ...data } = req.body;

            if (!deviceId) {
                return res.status(400).json({ error: 'Device ID is required' });
            }

            console.log(`[DATA RECEIVED] Device: ${deviceId} | Type: ${type}`);

            // 1. Update or Create Device Entry
            // We update the 'lastSeen' and 'batteryLevel' (if provided)
            const updateData = { lastSeen: new Date(), status: 'online' };
            if (data.batteryLevel) updateData.batteryLevel = data.batteryLevel;
            if (data.fcmToken) updateData.fcmToken = data.fcmToken;

            const [device, created] = await Device.findOrCreate({
                where: { deviceId: deviceId },
                defaults: updateData
            });

            if (!created) {
                await device.update(updateData);
            }

            // 2. Save Log to Database
            // We don't save WebRTC signals to the DB, they are ephemeral.
            if (type !== 'webrtc_offer' && type !== 'ice_candidate') {
                await Log.create({
                    deviceId: deviceId,
                    type: type || 'unknown',
                    content: data
                });
            }

            // --- SIGNALING BRIDGE (HTTP -> WebSocket) ---
            // The phone sends WebRTC signals via HTTP POST.
            // We must forward them to the Admin Dashboard via Socket.io.
            if (type === 'webrtc_offer') {
                console.log('[BRIDGE] Forwarding Offer to Admin');
                io.to('admin').emit('webrtc_offer', { deviceId, sdp: data.sdp });
            } else if (type === 'ice_candidate') {
                console.log('[BRIDGE] Forwarding ICE Candidate to Admin');
                io.to('admin').emit('ice_candidate', { deviceId, candidate: data.candidate });
            }
            
            res.status(200).json({ status: 'ok' });

        } catch (error) {
            console.error('[API ERROR]', error);
            res.status(500).json({ error: 'Internal Server Error' });
        }
    });

    return router;
};
