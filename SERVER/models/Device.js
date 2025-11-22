const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Device = sequelize.define('Device', {
    deviceId: {
        type: DataTypes.STRING,
        allowNull: false,
        unique: true
    },
    name: {
        type: DataTypes.STRING,
        defaultValue: 'Unknown Device'
    },
    status: {
        type: DataTypes.STRING,
        defaultValue: 'offline' // online, offline
    },
    lastSeen: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW
    },
    fcmToken: {
        type: DataTypes.TEXT,
        allowNull: true
    },
    batteryLevel: {
        type: DataTypes.INTEGER,
        defaultValue: 0
    }
});

module.exports = Device;
