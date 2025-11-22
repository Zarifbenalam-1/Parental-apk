// --- AUTH CHECK ---
const token = localStorage.getItem('token');
if (!token) {
    window.location.href = '/login.html';
}

const socket = io();

// --- DOM ELEMENTS ---
const views = document.querySelectorAll('.view-section');
const navItems = document.querySelectorAll('.nav-item');
const logoutBtn = document.getElementById('logout-btn');
const connectionStatus = document.getElementById('connection-status');
const logOutput = document.getElementById('log-output');
const deviceList = document.getElementById('device-list');

// --- NAVIGATION ---
navItems.forEach(item => {
    item.addEventListener('click', (e) => {
        e.preventDefault();
        const targetView = item.dataset.view;
        
        // Update UI
        navItems.forEach(nav => nav.classList.remove('active'));
        item.classList.add('active');
        
        views.forEach(view => view.classList.remove('active'));
        document.getElementById(`view-${targetView}`).classList.add('active');
        
        // Load Data if needed
        if (targetView === 'devices') loadDevices();
    });
});

// --- LOGOUT ---
logoutBtn.addEventListener('click', () => {
    localStorage.removeItem('token');
    window.location.href = '/login.html';
});

// --- SOCKET CONNECTION ---
socket.on('connect', () => {
    connectionStatus.textContent = 'Connected';
    connectionStatus.className = 'badge badge-success';
    socket.emit('identify', 'admin');
});

socket.on('disconnect', () => {
    connectionStatus.textContent = 'Disconnected';
    connectionStatus.className = 'badge badge-danger';
});

// --- API CALLS (With Auth) ---
async function apiCall(endpoint, method = 'GET', body = null) {
    const headers = {
        'Content-Type': 'application/json',
        'x-auth-token': token
    };
    
    const options = { method, headers };
    if (body) options.body = JSON.stringify(body);
    
    const res = await fetch(`/admin-api${endpoint}`, options);
    if (res.status === 401) {
        // Token expired
        localStorage.removeItem('token');
        window.location.href = '/login.html';
    }
    return res.json();
}

// --- DEVICES ---
async function loadDevices() {
    const devices = await apiCall('/devices');
    deviceList.innerHTML = '';
    
    // Update Stats
    document.getElementById('stat-total-devices').textContent = devices.length;
    const onlineCount = devices.filter(d => d.status === 'online').length;
    document.getElementById('stat-online-devices').textContent = onlineCount;

    devices.forEach(device => {
        const row = document.createElement('tr');
        const statusClass = device.status === 'online' ? 'badge-success' : 'badge-danger';
        
        row.innerHTML = `
            <td><span class="badge ${statusClass}">${device.status.toUpperCase()}</span></td>
            <td>${device.deviceId}</td>
            <td>${new Date(device.lastSeen).toLocaleString()}</td>
            <td>${device.batteryLevel}%</td>
            <td>
                <button class="btn btn-sm btn-secondary" onclick="sendCommand('${device.deviceId}', 'PING')">PING</button>
                <button class="btn btn-sm btn-danger" onclick="sendCommand('${device.deviceId}', 'WIPE')">WIPE</button>
            </td>
        `;
        deviceList.appendChild(row);
    });
}

// --- COMMANDS ---
window.sendCommand = async (deviceId, command) => {
    if(!confirm(`Are you sure you want to send ${command} to ${deviceId}?`)) return;
    
    const res = await apiCall('/command', 'POST', { deviceId, command });
    alert(res.status === 'sent' ? 'Command Sent!' : 'Failed: ' + res.error);
};

// --- SETTINGS (User Management) ---
document.getElementById('change-password-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const newPassword = document.getElementById('new-password').value;
    const res = await apiCall('/change-password', 'POST', { newPassword });
    alert(res.msg || 'Error');
    e.target.reset();
});

document.getElementById('create-user-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = document.getElementById('new-username').value;
    const password = document.getElementById('new-user-password').value;
    const res = await apiCall('/create-user', 'POST', { username, password });
    alert(res.msg || 'Error');
    e.target.reset();
});

// --- WEBRTC (The Eyes) ---
const rtcConfig = { iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] };
let peerConnection;
const remoteVideo = document.getElementById('remoteVideo');
const overlay = document.querySelector('.video-overlay');

document.getElementById('btn-start-stream').addEventListener('click', () => {
    // In a real scenario, you'd select a specific device first.
    // For now, we broadcast to ALL (or the first one).
    // Ideally, add a "Stream" button to the device list.
    alert('Select a device from the Devices tab to stream (Feature coming in Phase 3)');
});

socket.on('webrtc_offer', async (data) => {
    console.log('Received Offer');
    overlay.style.display = 'none';
    
    if (peerConnection) peerConnection.close();
    peerConnection = new RTCPeerConnection(rtcConfig);
    
    peerConnection.ontrack = (event) => {
        remoteVideo.srcObject = event.streams[0];
    };
    
    await peerConnection.setRemoteDescription(new RTCSessionDescription(data.sdp));
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);
    
    // We need to send this answer back to the SPECIFIC device via FCM or Socket
    // For now, we just log it. In Phase 3, we implement the return path.
    console.log('Generated Answer');
});
