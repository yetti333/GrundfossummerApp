package com.example.grundfos_summer_app.data.repository

import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.model.EspSchedule
import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import com.example.grundfos_summer_app.data.remote.ApiAckDto
import com.example.grundfos_summer_app.data.remote.EspApiService
import com.example.grundfos_summer_app.data.remote.EspStatusDto
import com.example.grundfos_summer_app.data.remote.toDomain
import com.example.grundfos_summer_app.data.remote.toDomainStatus
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.URI
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
    private val prefersMdnsAsPrimary = primaryIpHost?.endsWith(".local", ignoreCase = true) == true

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
                val result = handleAckResponse(
                    response = service.provision(request),
                    endpoint = "/provision",
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
        val normalizedMode = if (mode.equals("AUTO", ignoreCase = true)) "AUTO" else "MANUAL"
        return executeUnitOnServices { service ->
            handleAckResponse(
                endpoint = "/set/mode",
                response = service.setMode(mapOf("mode" to normalizedMode))
            )
        }
    }

    suspend fun setBypass(enabled: Boolean): Result<Unit> {
        return executeUnitOnServices { service ->
            handleAckResponse(
                endpoint = "/set/bypass",
                response = service.setBypass(mapOf("bypass" to enabled))
            )
        }
    }

    suspend fun pumpStart(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleAckResponse(endpoint = "/pump/start", response = service.pumpStart())
        }
    }

    suspend fun pumpStop(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleAckResponse(endpoint = "/pump/stop", response = service.pumpStop())
        }
    }

    suspend fun resetErrors(): Result<Unit> {
        // Firmware expose pouze /pump/error-reset, proto resetErrors mapujeme sem pro zpětnou kompatibilitu volání.
        return resetPumpError()
    }

    suspend fun resetPumpError(): Result<Unit> {
        return executeUnitOnServices { service ->
            handleAckResponse(endpoint = "/pump/error-reset", response = service.resetPumpError())
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
            handleAckResponse(
                endpoint = "/set/schedule",
                response = service.saveSchedule(
                    mapOf(
                        "start_hour" to schedule.startHour,
                        "start_minute" to schedule.startMinute,
                        "duration_minutes" to schedule.durationMinutes
                    )
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
        // Normal API calls go only to the primary service (cached IP).
        // mDNS fallback is intentionally removed – reconnection is handled by MainViewModel via NsdDiscovery.
        return listOf(primaryApi)
    }

    /**
     * Updates the primary API endpoint URL.
     * Called after NSD discovery returns a new IP, or after provisioning discovers the device on
     * the home network for the first time.
     */
    fun updatePrimaryUrl(baseUrl: String) {
        val newHost = parseHost(baseUrl) ?: return
        if (newHost == primaryIpHost) {
            Log.d("ESP_API", "Repository: primary URL unchanged ($baseUrl)")
            return
        }
        val factory = apiFactory ?: return
        Log.d("ESP_API", "Repository: updating primary URL to $baseUrl")
        primaryIpHost = newHost
        primaryApi = factory(baseUrl)
    }

    private fun provisioningFirstServices(): List<EspApiService> {
        val fallback = provisioningApi ?: return listOf(primaryApi)
        return if (fallback === primaryApi) listOf(primaryApi) else listOf(fallback, primaryApi)
    }

    private suspend fun safeStatusCall(service: EspApiService): Result<EspStatus> {
        return try {
            val statusResponse = service.getStatus()
            val statusResult = toStatusResult(statusResponse)
            if (statusResult.isSuccess) {
                return statusResult
            }

            val heartbeatResponse = service.getHeartbeat()
            if (heartbeatResponse.isSuccessful) {
                val heartbeat = heartbeatResponse.body()
                    ?: return Result.failure(Exception("Empty response body for /heartbeat"))
                return Result.success(heartbeat.toDomainStatus())
            }

            Result.failure(
                mapHttpOrApiError(
                    endpoint = "/status",
                    code = statusResponse.code(),
                    errorBody = statusResponse.errorBody()?.string()
                )
            )
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

        val statusDto = runCatching { mdnsService.getStatus().body() }.getOrNull() ?: return
        val status = statusDto.toDomain()
        updatePrimaryFromStatus(status)
    }

    private fun updatePrimaryFromStatus(status: EspStatus) {
        if (prefersMdnsAsPrimary) {
            return
        }

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

    private fun handleAckResponse(
        endpoint: String,
        response: Response<ApiAckDto>,
        badRequestMessage: String? = null
    ): Result<Unit> {
        if (response.isSuccessful) {
            val body = response.body()
            val apiError = body?.error
            if (!apiError.isNullOrBlank()) {
                return Result.failure(mapApiError(endpoint, apiError, response.code(), badRequestMessage))
            }
            if (body?.ok == false) {
                return Result.failure(Exception("$endpoint failed: ok=false"))
            }
            return Result.success(Unit)
        }

        val errorBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        return Result.failure(mapHttpOrApiError(endpoint, response.code(), errorBody, badRequestMessage))
    }

    private fun toStatusResult(response: Response<EspStatusDto>): Result<EspStatus> {
        if (!response.isSuccessful) {
            val errorBody = try {
                response.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            return Result.failure(mapHttpOrApiError("/status", response.code(), errorBody))
        }

        val dto = response.body() ?: return Result.failure(Exception("Empty response body for /status"))
        return Result.success(dto.toDomain())
    }

    private fun mapHttpOrApiError(
        endpoint: String,
        code: Int,
        errorBody: String?,
        badRequestMessage: String? = null
    ): Throwable {
        val apiError = extractApiError(errorBody)
        return when {
            code == 400 && badRequestMessage != null -> IllegalArgumentException(badRequestMessage)
            !apiError.isNullOrBlank() -> mapApiError(endpoint, apiError, code, badRequestMessage)
            !errorBody.isNullOrBlank() -> Exception("HTTP $code at $endpoint: $errorBody")
            else -> Exception("HTTP $code at $endpoint")
        }
    }

    private fun mapApiError(
        endpoint: String,
        apiError: String,
        code: Int,
        badRequestMessage: String? = null
    ): Throwable {
        if (apiError == "invalid_json" && badRequestMessage != null) {
            return IllegalArgumentException(badRequestMessage)
        }

        val message = when (apiError.lowercase()) {
            "invalid_json" -> "Neplatný JSON požadavek pro $endpoint."
            "ssid_empty" -> "SSID nesmí být prázdné. Zkontrolujte zadané údaje a zkuste to znovu."
            else -> "ESP API chyba na $endpoint: $apiError (HTTP $code)"
        }
        return Exception(message)
    }

    private fun extractApiError(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) {
            return null
        }

        return try {
            gson.fromJson(errorBody, ApiAckDto::class.java)?.error
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
