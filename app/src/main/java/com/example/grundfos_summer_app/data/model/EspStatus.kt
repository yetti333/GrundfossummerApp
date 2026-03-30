package com.example.grundfos_summer_app.data.model

import com.google.gson.annotations.SerializedName

data class EspErrors(
    val wifi: Boolean,
    val time: Boolean,
    val pump: Boolean
)

data class EspPumpStatus(
    val running: Boolean,
    @SerializedName("test_phase") val testPhase: Boolean,
    @SerializedName("pulse_ok") val pulseOk: Boolean,
    @SerializedName("pulse_frequency_hz") val pulseFrequencyHz: Int,
    @SerializedName("pulse_count_last_minute") val pulseCountLastMinute: Long,
    @SerializedName("pulse_stability") val pulseStability: Int
)

data class EspSchedule(
    @SerializedName("start_hour") val startHour: Int,
    @SerializedName("start_minute") val startMinute: Int,
    @SerializedName("duration_minutes") val durationMinutes: Int
)

data class EspNetworkInfo(
    val ip: String? = null,
    @SerializedName("mac") val macAddress: String? = null,
    val ssid: String? = null,
    val hostname: String? = null,
    @SerializedName("mdns") val mdnsHost: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("ap_mode") val apMode: Boolean? = null,
    @SerializedName("station_mode") val stationMode: Boolean? = null,
    @SerializedName("connected") val connected: Boolean? = null,
    @SerializedName("last_seen") val lastSeen: String? = null
)

data class EspDeviceInfo(
    val hostname: String? = null,
    @SerializedName("mdns") val mdnsHost: String? = null,
    @SerializedName("firmware_version") val firmwareVersion: String? = null,
    val uptime: String? = null,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long? = null,
    @SerializedName("provisioning_required") val provisioningRequired: Boolean? = null
)

data class EspStatus(
    val state: String,
    val mode: String,
    val bypass: Boolean,
    val errors: EspErrors,
    val pump: EspPumpStatus,
    val schedule: EspSchedule? = null,
    val ip: String? = null,
    @SerializedName("mac") val macAddress: String? = null,
    val ssid: String? = null,
    val hostname: String? = null,
    @SerializedName("mdns") val mdnsHost: String? = null,
    val rssi: Int? = null,
    val uptime: String? = null,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long? = null,
    @SerializedName("firmware_version") val firmwareVersion: String? = null,
    @SerializedName("ap_mode") val apMode: Boolean? = null,
    @SerializedName("station_mode") val stationMode: Boolean? = null,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("provisioning_required") val provisioningRequired: Boolean? = null,
    @SerializedName("network_info") val networkInfo: EspNetworkInfo? = null,
    @SerializedName("device_info") val deviceInfo: EspDeviceInfo? = null
)
