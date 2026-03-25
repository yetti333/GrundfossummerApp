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

data class EspStatus(
    val state: String,
    val mode: String,
    val bypass: Boolean,
    val errors: EspErrors,
    val pump: EspPumpStatus,
    val schedule: EspSchedule
)
