const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const path = require('path');
require('dotenv').config();

const { initDB } = require('./models');

// --- CONFIGURATION ---
const PORT = process.env.PORT || 3000;

// --- INITIALIZATION ---
const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: {
        origin: "*", // TODO: Lock this down in production!
        methods: ["GET", "POST"]
    }
});

// --- MIDDLEWARE ---
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// --- ROOT REDIRECT ---
app.get('/', (req, res) => {
    res.redirect('/login.html');
});

// --- DATABASE ---
initDB();

// --- ROUTES ---
const apiRoutes = require('./routes/api')(io);
app.use('/api/v1', apiRoutes);

const adminRoutes = require('./routes/admin');
app.use('/admin-api', adminRoutes);

// --- WEBSOCKETS ---
require('./sockets/signaling')(io);

// --- START ---
server.listen(PORT, () => {
    console.log(`
    ===========================================
    PROJECT RAVEN C2 SERVER (REFACTORED)
    Status: ONLINE
    Port: ${PORT}
    Database: SQLite (Sequelize)
    Auth: JWT Enabled
    ===========================================
    `);
});
