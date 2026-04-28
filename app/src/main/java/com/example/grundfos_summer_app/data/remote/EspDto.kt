package com.example.grundfos_summer_app.data.remote

import com.example.grundfos_summer_app.data.model.EspDeviceInfo
import com.example.grundfos_summer_app.data.model.EspErrors
import com.example.grundfos_summer_app.data.model.EspNetworkInfo
import com.example.grundfos_summer_app.data.model.EspPumpStatus
import com.example.grundfos_summer_app.data.model.EspSchedule
import com.example.grundfos_summer_app.data.model.EspStatus
import com.google.gson.annotations.SerializedName

data class ApiAckDto(
	val ok: Boolean? = null,
	val error: String? = null
)

data class EspHeartbeatDto(
	val timestamp: Long? = null,
	val state: String? = null,
	@SerializedName("wifi_rssi") val wifiRssi: Int? = null,
	@SerializedName("uptime_sec") val uptimeSec: Long? = null,
	@SerializedName("last_pulse_timestamp") val lastPulseTimestamp: Long? = null
)

data class EspErrorsDto(
	val wifi: Boolean? = null,
	val time: Boolean? = null,
	val pump: Boolean? = null
)

data class EspPumpStatusDto(
	val running: Boolean? = null,
	@SerializedName("test_phase") val testPhase: Boolean? = null,
	@SerializedName("pulse_ok") val pulseOk: Boolean? = null,
	@SerializedName("pulse_frequency_hz") val pulseFrequencyHz: Double? = null,
	@SerializedName("pulse_count_last_minute") val pulseCountLastMinute: Long? = null,
	@SerializedName("pulse_stability") val pulseStability: Double? = null,
	@SerializedName("period_ms") val periodMs: Double? = null,
	@SerializedName("high_ms") val highMs: Double? = null,
	@SerializedName("duty_percent") val dutyPercent: Double? = null,
	@SerializedName("interpreted_state") val interpretedState: String? = null,
	@SerializedName("valid_frequency") val validFrequency: Boolean? = null,
	val timestamp: Long? = null
)

data class EspScheduleDto(
	@SerializedName("start_hour") val startHour: Int? = null,
	@SerializedName("start_minute") val startMinute: Int? = null,
	@SerializedName("duration_minutes") val durationMinutes: Int? = null
)

data class EspNetworkInfoDto(
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

data class EspDeviceInfoDto(
	val hostname: String? = null,
	@SerializedName("mdns") val mdnsHost: String? = null,
	@SerializedName("firmware_version") val firmwareVersion: String? = null,
	val uptime: String? = null,
	@SerializedName("uptime_seconds") val uptimeSeconds: Long? = null,
	@SerializedName("provisioning_required") val provisioningRequired: Boolean? = null
)

data class EspStatusDto(
	val state: String? = null,
	val mode: String? = null,
	val bypass: Boolean? = null,
	val errors: EspErrorsDto? = null,
	val pump: EspPumpStatusDto? = null,
	val schedule: EspScheduleDto? = null,
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
	@SerializedName("network_info") val networkInfo: EspNetworkInfoDto? = null,
	@SerializedName("device_info") val deviceInfo: EspDeviceInfoDto? = null
)

fun EspStatusDto.toDomain(): EspStatus {
	val modeValue = mode ?: if ((state ?: "").uppercase().contains("AUTO")) "AUTO" else "MANUAL"
	return EspStatus(
		state = state ?: "UNKNOWN",
		mode = modeValue,
		bypass = bypass ?: false,
		errors = EspErrors(
			wifi = errors?.wifi ?: false,
			time = errors?.time ?: false,
			pump = errors?.pump ?: false
		),
		pump = EspPumpStatus(
			running = pump?.running ?: false,
			testPhase = pump?.testPhase ?: false,
			pulseOk = pump?.pulseOk ?: false,
			pulseFrequencyHz = pump?.pulseFrequencyHz ?: 0.0,
			pulseCountLastMinute = pump?.pulseCountLastMinute ?: 0L,
			pulseStability = pump?.pulseStability ?: 0.0,
			periodMs = pump?.periodMs,
			highMs = pump?.highMs,
			dutyPercent = pump?.dutyPercent,
			interpretedStateRaw = pump?.interpretedState,
			validFrequency = pump?.validFrequency,
			timestamp = pump?.timestamp
		),
		schedule = schedule?.let {
			EspSchedule(
				startHour = it.startHour ?: 0,
				startMinute = it.startMinute ?: 0,
				durationMinutes = it.durationMinutes ?: 0
			)
		},
		ip = ip,
		macAddress = macAddress,
		ssid = ssid,
		hostname = hostname,
		mdnsHost = mdnsHost,
		rssi = rssi,
		uptime = uptime,
		uptimeSeconds = uptimeSeconds,
		firmwareVersion = firmwareVersion,
		apMode = apMode,
		stationMode = stationMode,
		lastSeen = lastSeen,
		provisioningRequired = provisioningRequired,
		networkInfo = networkInfo?.let {
			EspNetworkInfo(
				ip = it.ip,
				macAddress = it.macAddress,
				ssid = it.ssid,
				hostname = it.hostname,
				mdnsHost = it.mdnsHost,
				rssi = it.rssi,
				apMode = it.apMode,
				stationMode = it.stationMode,
				connected = it.connected,
				lastSeen = it.lastSeen
			)
		},
		deviceInfo = deviceInfo?.let {
			EspDeviceInfo(
				hostname = it.hostname,
				mdnsHost = it.mdnsHost,
				firmwareVersion = it.firmwareVersion,
				uptime = it.uptime,
				uptimeSeconds = it.uptimeSeconds,
				provisioningRequired = it.provisioningRequired
			)
		}
	)
}

fun EspHeartbeatDto.toDomainStatus(): EspStatus {
	val modeValue = if ((state ?: "").uppercase().contains("AUTO")) "AUTO" else "MANUAL"
	return EspStatus(
		state = state ?: "UNKNOWN",
		mode = modeValue,
		bypass = false,
		errors = EspErrors(wifi = false, time = false, pump = false),
		pump = EspPumpStatus(
			running = false,
			testPhase = false,
			pulseOk = true,
			pulseFrequencyHz = 0.0,
			pulseCountLastMinute = 0L,
			pulseStability = 0.0,
			timestamp = lastPulseTimestamp ?: timestamp
		),
		rssi = wifiRssi,
		uptimeSeconds = uptimeSec
	)
}

