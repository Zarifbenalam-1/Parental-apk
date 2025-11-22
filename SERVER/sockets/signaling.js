module.exports = (io) => {
    io.on('connection', (socket) => {
        console.log(`[SOCKET] New Connection: ${socket.id}`);

        // Identify who is connecting (Admin or Phone?)
        socket.on('identify', (role) => {
            socket.join(role); // Join room 'admin' or 'phone'
            console.log(`[SOCKET] ${socket.id} identified as ${role}`);
        });

        // --- WEBRTC SIGNALING ---
        
        // 1. Phone sends Offer -> Server sends to Admin
        socket.on('webrtc_offer', (data) => {
            console.log('[RTC] Received Offer from Phone');
            io.to('admin').emit('webrtc_offer', data);
        });

        // 2. Admin sends Answer -> Server sends to Phone
        socket.on('webrtc_answer', (data) => {
            console.log('[RTC] Received Answer from Admin');
            // We can't send via Socket to phone (it might be asleep/background).
            // We must use FCM to deliver the answer!
            // TODO: Trigger FCM here in Phase 4
        });

        // 3. ICE Candidates (Bi-directional)
        socket.on('ice_candidate', (data) => {
            console.log('[RTC] ICE Candidate');
            socket.broadcast.emit('ice_candidate', data);
        });

        socket.on('disconnect', () => {
            console.log(`[SOCKET] Disconnected: ${socket.id}`);
        });
    });
};
