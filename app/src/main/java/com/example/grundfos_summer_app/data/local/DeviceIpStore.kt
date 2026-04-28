package com.example.grundfos_summer_app.data.local

import android.content.Context
import android.util.Log

/**
 * Persists the last known IP address of the ESP device across app restarts.
 * Using this IP directly skips mDNS DNS resolution on every request (~0.5–3 s savings).
 */
class DeviceIpStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Last successfully discovered IPv4 address of the ESP device (e.g. "192.168.1.42"). */
    var cachedIp: String?
        get() = prefs.getString(KEY_DEVICE_IP, null)
        set(value) {
            if (value != null) {
                Log.d(TAG, "Storing device IP: $value")
                prefs.edit().putString(KEY_DEVICE_IP, value).apply()
            } else {
                Log.d(TAG, "Clearing stored device IP")
                prefs.edit().remove(KEY_DEVICE_IP).apply()
            }
        }

    /** Returns a base URL ready for Retrofit (e.g. "http://192.168.1.42/"), or null if unknown. */
    fun buildBaseUrl(): String? = cachedIp?.let { "http://$it/" }

    companion object {
        private const val TAG = "ESP_API"
        private const val PREFS_NAME = "esp_device_prefs"
        private const val KEY_DEVICE_IP = "device_ip"
    }
}

