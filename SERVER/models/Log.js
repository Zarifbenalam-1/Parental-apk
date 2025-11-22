const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Log = sequelize.define('Log', {
    deviceId: {
        type: DataTypes.STRING,
        allowNull: false
    },
    type: {
        type: DataTypes.STRING, // location, notification, call, sms
        allowNull: false
    },
    content: {
        type: DataTypes.JSON, // Store the raw data as JSON
        allowNull: false
    }
});

module.exports = Log;
