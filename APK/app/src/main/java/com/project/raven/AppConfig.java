package com.project.raven;

public class AppConfig {
    // The "Owner" ID - links this device to your dashboard
    public static final String OWNER_ID = "admin_001";
    
    // Your C2 Server URL
    // RUTHLESS: YOU MUST CHANGE THIS BEFORE BUILDING THE APK.
    // If you are testing on Emulator, use "http://10.0.2.2:3000/api/v1"
    // If you are testing on Real Device, use your PC's Local IP "http://192.168.x.x:3000/api/v1"
    // If you are deploying, use your Cloud URL "https://your-vps.com/api/v1"
    public static final String SERVER_URL = "http://10.0.2.2:3000/api/v1";
    
    // Notification Channel ID (Boring name)
    public static final String CHANNEL_ID = "battery_optimization_channel";
    public static final String CHANNEL_NAME = "System Optimization";
}
