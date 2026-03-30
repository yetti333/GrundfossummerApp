package com.example.grundfos_summer_app.data.repository

import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.model.EspSchedule
import com.example.grundfos_summer_app.data.remote.EspApiService
import com.google.gson.Gson
import java.io.IOException
import java.net.URI
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response

class EspRepository(
    api: EspApiService,
    private val provisioningApi: EspApiService? = null,
    primaryBaseUrl: String? = null,
    private val apiFactory: ((String) -> EspApiService)? = null
) {
    private val gson = Gson()
    private var primaryApi: EspApiService = api
    private var primaryIpHost: String? = parseHost(primaryBaseUrl)

    suspend fun getStatus(): Result<EspStatus> {
        return tryServices(::safeStatusCall) { _, status ->
            updatePrimaryFromStatus(status)
        }
    }

    suspend fun submitProvisioning(ssid: String, password: String): Result<Unit> {
        val request = ProvisioningRequest(
            ssid = ssid.trim(),
            password = password
        )

        val services = provisioningFirstServices()
        var lastFailure: Throwable? = null

        for (service in services) {
            try {
                val result = handleUnitResponse(
                    response = service.provision(request),
                    badRequestMessage = "SSID nesmí být prázdné. Zkontrolujte zadané údaje a zkuste to znovu."
                )

                if (result.isSuccess) {
                    return result
                }

                lastFailure = result.exceptionOrNull()
            } catch (e: IOException) {
                lastFailure = Exception(
                    "Nepodařilo se spojit se zařízením pro provisioning. Připojte telefon k síti Grundfos-Provision a zkuste to znovu.",
                    e
                )
            } catch (e: Exception) {
                lastFailure = e
            }
        }

        return Result.failure(lastFailure ?: Exception("Provisioning se nezdařil."))
    }

    suspend fun setMode(mode: String): Result<Unit> {
        return executeUnitOnServices { service ->
            tryUnitResponses(
                listOf(
                    { service.setMode(createJsonBody(gson.toJson(mapOf("mode" to mode)))) },
                    { service.setMode(createJsonBody(gson.toJson(mapOf("mode" to normalizeModeState(mode))))) },
                    { service.setMode(createJsonBody(gson.toJson(mapOf("state" to normalizeModeState(mode))))) },
                    { service.setMode(createJsonBody(gson.toJson(mapOf("mode" to mode.lowercase())))) }
                )
            )
        }
    }

    suspend fun setBypass(enabled: Boolean): Result<Unit> {
        return executeUnitOnServices { service ->
            tryUnitResponses(
                listOf(
                    { service.setBypass(createJsonBody(gson.toJson(mapOf("bypass" to enabled)))) },
                    { service.setBypass(createJsonBody(gson.toJson(mapOf("enabled" to enabled)))) },
                    { service.setBypass(createJsonBody(gson.toJson(mapOf("bypass" to if (enabled) 1 else 0)))) },
                    { service.setBypass(createJsonBody(gson.toJson(mapOf("enabled" to if (enabled) 1 else 0)))) }
                )
            )
        }
    }

    suspend fun pumpStart(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleUnitResponse(service.pumpStart())
        }
    }

    suspend fun pumpStop(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleUnitResponse(service.pumpStop())
        }
    }

    suspend fun resetErrors(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleUnitResponse(service.resetErrors())
        }
    }

    suspend fun resetPumpError(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleUnitResponse(service.resetPumpError(createJsonBody("{}")))
        }
    }

    suspend fun saveSettings(
        startTime: String,
        runMinutes: Int,
        _feedbackTimeoutSec: Int
    ): Result<Unit> {
        val parts = startTime.split(":")
        val startHour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val startMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val schedule = EspSchedule(
            startHour = startHour,
            startMinute = startMinute,
            durationMinutes = runMinutes
        )

        return executeUnitOnServices { service ->
            tryUnitResponses(
                listOf(
                    { service.saveSchedule(createJsonBody(gson.toJson(schedule))) },
                    {
                        service.saveSchedule(
                            createJsonBody(
                                gson.toJson(
                                    mapOf(
                                        "schedule" to mapOf(
                                            "start_hour" to startHour,
                                            "start_minute" to startMinute,
                                            "duration_minutes" to runMinutes
                                        )
                                    )
                                )
                            )
                        )
                    },
                    {
                        service.saveSchedule(
                            createJsonBody(
                                gson.toJson(
                                    mapOf(
                                        "startHour" to startHour,
                                        "startMinute" to startMinute,
                                        "durationMinutes" to runMinutes
                                    )
                                )
                            )
                        )
                    }
                )
            )
        }
    }

    private suspend fun executeUnitOnServices(
        action: suspend (EspApiService) -> Result<Unit>
    ): Result<Unit> {
        return tryServices(
            action = { service ->
                try {
                    action(service)
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onSuccess = { service, _ ->
                recoverPrimaryFromMdnsIfNeeded(service)
            }
        )
    }

    private suspend fun <T> tryServices(
        action: suspend (EspApiService) -> Result<T>,
        onSuccess: suspend (EspApiService, T) -> Unit = { _, _ -> }
    ): Result<T> {
        var lastFailure: Throwable? = null

        for (service in orderedServices()) {
            val result = action(service)
            if (result.isSuccess) {
                result.getOrNull()?.let { value -> onSuccess(service, value) }
                return result
            }
            lastFailure = result.exceptionOrNull()
        }

        return Result.failure(lastFailure ?: Exception("Požadavek se nezdařil."))
    }

    private fun orderedServices(): List<EspApiService> {
        val fallback = provisioningApi ?: return listOf(primaryApi)
        return if (fallback === primaryApi) listOf(primaryApi) else listOf(primaryApi, fallback)
    }

    private fun provisioningFirstServices(): List<EspApiService> {
        val fallback = provisioningApi ?: return listOf(primaryApi)
        return if (fallback === primaryApi) listOf(primaryApi) else listOf(fallback, primaryApi)
    }

    private suspend fun safeStatusCall(service: EspApiService): Result<EspStatus> {
        return try {
            Result.success(service.getStatus())
        } catch (e: retrofit2.HttpException) {
            Result.failure(Exception("HTTP ${e.code()}"))
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun recoverPrimaryFromMdnsIfNeeded(service: EspApiService) {
        val mdnsService = provisioningApi ?: return
        if (service !== mdnsService) {
            return
        }

        val status = runCatching { mdnsService.getStatus() }.getOrNull() ?: return
        updatePrimaryFromStatus(status)
    }

    private fun updatePrimaryFromStatus(status: EspStatus) {
        val discoveredHost = parseHost(status.networkInfo?.ip)
            ?: parseHost(status.ip)
            ?: return

        if (discoveredHost == primaryIpHost) {
            return
        }

        primaryIpHost = discoveredHost
        val factory = apiFactory ?: return
        val newBaseUrl = "http://$discoveredHost/"
        primaryApi = factory(newBaseUrl)
    }

    private fun parseHost(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) {
            return null
        }

        return runCatching {
            val uri = if (raw.contains("://")) URI(raw) else URI("http://$raw")
            uri.host?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: raw.substringBefore('/').ifBlank { null }
    }

    private fun handleUnitResponse(
        response: Response<Unit>,
        badRequestMessage: String? = null
    ): Result<Unit> {
        return if (response.isSuccessful) {
            Result.success(Unit)
        } else {
            if (response.code() == 400 && badRequestMessage != null) {
                return Result.failure(IllegalArgumentException(badRequestMessage))
            }

            val errorBody = try { response.errorBody()?.string() } catch (_: Exception) { null }
            val msg = if (!errorBody.isNullOrBlank()) {
                "HTTP ${response.code()}: $errorBody"
            } else {
                "HTTP ${response.code()}"
            }
            Result.failure(Exception(msg))
        }
    }

    private suspend fun tryUnitResponses(
        attempts: List<suspend () -> Response<Unit>>
    ): Result<Unit> {
        var lastResponse: Response<Unit>? = null

        for (attempt in attempts) {
            val response = attempt()
            lastResponse = response

            if (response.isSuccessful) {
                return Result.success(Unit)
            }

            if (response.code() != 400) {
                return handleUnitResponse(response)
            }
        }

        return handleUnitResponse(
            lastResponse ?: error("No request attempts were provided.")
        )
    }

    private fun normalizeModeState(mode: String): String {
        return when (mode.uppercase()) {
            "AUTO" -> "AUTO_MODE"
            "MANUAL" -> "MANUAL_MODE"
            else -> mode
        }
    }

    private fun createJsonBody(json: String): RequestBody {
        return json.toRequestBody("application/json".toMediaType())
    }
}
