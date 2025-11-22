const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { User, Device, Log } = require('../models');
const auth = require('../middleware/auth');
const firebase = require('firebase-admin');
const path = require('path');
const fs = require('fs');

const SECRET_KEY = process.env.JWT_SECRET || 'super_secret_key_change_this_in_prod';

// --- FIREBASE INIT ---
const serviceAccountPath = path.join(__dirname, '../firebase-key.json');
let fcmReady = false;
if (fs.existsSync(serviceAccountPath)) {
    try {
        const serviceAccount = require(serviceAccountPath);
        firebase.initializeApp({ credential: firebase.credential.cert(serviceAccount) });
        fcmReady = true;
    } catch (e) { console.error('[FCM] Init Failed:', e.message); }
}

// --- 1. LOGIN (Get Token) ---
router.post('/login', async (req, res) => {
    const { username, password } = req.body;
    try {
        const user = await User.findOne({ where: { username } });
        if (!user) return res.status(400).json({ msg: 'Invalid Credentials' });

        const isMatch = await bcrypt.compare(password, user.password);
        if (!isMatch) return res.status(400).json({ msg: 'Invalid Credentials' });

        const payload = { user: { id: user.id } };
        jwt.sign(payload, SECRET_KEY, { expiresIn: '24h' }, (err, token) => {
            if (err) throw err;
            res.json({ token });
        });
    } catch (err) {
        console.error(err.message);
        res.status(500).send('Server Error');
    }
});

// --- 1.5 CHANGE PASSWORD (Protected) ---
router.post('/change-password', auth, async (req, res) => {
    const { newPassword } = req.body;
    try {
        const user = await User.findByPk(req.user.id);
        if (!user) return res.status(404).json({ msg: 'User not found' });

        const salt = await bcrypt.genSalt(10);
        user.password = await bcrypt.hash(newPassword, salt);
        await user.save();

        res.json({ msg: 'Password updated successfully' });
    } catch (err) {
        console.error(err.message);
        res.status(500).send('Server Error');
    }
});

// --- 1.6 CREATE NEW USER (Protected) ---
router.post('/create-user', auth, async (req, res) => {
    const { username, password } = req.body;
    try {
        let user = await User.findOne({ where: { username } });
        if (user) return res.status(400).json({ msg: 'User already exists' });

        user = await User.create({ username, password });
        res.json({ msg: 'User created successfully' });
    } catch (err) {
        console.error(err.message);
        res.status(500).send('Server Error');
    }
});

// --- 2. GET DEVICES (Protected) ---
router.get('/devices', auth, async (req, res) => {
    try {
        const devices = await Device.findAll();
        res.json(devices);
    } catch (err) {
        res.status(500).send('Server Error');
    }
});

// --- 3. GET LOGS (Protected) ---
router.get('/logs/:deviceId/:type', auth, async (req, res) => {
    try {
        const { deviceId, type } = req.params;
        const logs = await Log.findAll({
            where: { deviceId, type },
            order: [['createdAt', 'DESC']],
            limit: 100
        });
        res.json(logs);
    } catch (err) {
        res.status(500).send('Server Error');
    }
});

// --- 4. SEND COMMAND (Protected) ---
router.post('/command', auth, async (req, res) => {
    const { deviceId, command, payload } = req.body;
    
    console.log(`[ADMIN COMMAND] ${command} -> ${deviceId || 'ALL'}`);

    if (!fcmReady) return res.json({ status: 'simulated', msg: 'FCM Key Missing' });

    const message = {
        data: { command, payload: JSON.stringify(payload || {}) }
    };

    if (deviceId) {
        // Get Token for specific device
        const device = await Device.findOne({ where: { deviceId } });
        if (!device || !device.fcmToken) return res.status(404).json({ msg: 'Device not found or no FCM token' });
        message.token = device.fcmToken;
    } else {
        message.topic = 'all_devices';
    }

    try {
        const response = await firebase.messaging().send(message);
        res.json({ status: 'sent', fcmId: response });
    } catch (error) {
        res.status(500).json({ status: 'error', error: error.message });
    }
});

module.exports = router;
