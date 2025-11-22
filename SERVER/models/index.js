const sequelize = require('../config/database');
const User = require('./User');
const Device = require('./Device');
const Log = require('./Log');

// Define Relationships
// A Device has many Logs
Device.hasMany(Log, { foreignKey: 'deviceId', sourceKey: 'deviceId' });
Log.belongsTo(Device, { foreignKey: 'deviceId', targetKey: 'deviceId' });

const initDB = async () => {
    try {
        await sequelize.sync({ alter: true }); // Updates tables if models change
        console.log('[DATABASE] Synced successfully.');
        
        // Create default admin if not exists
        const admin = await User.findOne({ where: { username: 'admin' } });
        if (!admin) {
            await User.create({ username: 'admin', password: 'password123' });
            console.log('[DATABASE] Default Admin created: admin / password123');
            console.log('[SECURITY WARNING] CHANGE THIS PASSWORD IMMEDIATELY.');
        }
    } catch (error) {
        console.error('[DATABASE] Sync failed:', error);
    }
};

module.exports = {
    sequelize,
    initDB,
    User,
    Device,
    Log
};
