package com.example.grundfos_summer_app.data.repository

import com.example.grundfos_summer_app.data.model.ProvisioningRequest
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.model.EspSchedule
import com.example.grundfos_summer_app.data.remote.EspApiService
import com.google.gson.Gson
import java.io.IOException
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response

class EspRepository(
    private val api: EspApiService,
    private val provisioningApi: EspApiService? = null
) {
    private val gson = Gson()
    private var preferMdnsEndpoint: Boolean = false

    fun setPreferMdnsEndpoint(enabled: Boolean) {
        preferMdnsEndpoint = enabled && provisioningApi != null
    }

    suspend fun getStatus(): Result<EspStatus> {
        return tryServices(::safeStatusCall)
    }

    suspend fun submitProvisioning(ssid: String, password: String): Result<Unit> {
        val request = ProvisioningRequest(
            ssid = ssid.trim(),
            password = password
        )

        val result = tryServices { service ->
            try {
                handleUnitResponse(
                    response = service.provision(request),
                    badRequestMessage = "SSID nesmí být prázdné. Zkontrolujte zadané údaje a zkuste to znovu."
                )
            } catch (e: IOException) {
                Result.failure(
                    Exception(
                        "Nepodařilo se spojit se zařízením pro provisioning. Připojte telefon k síti Grundfos-Provision a zkuste to znovu.",
                        e
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        if (result.isSuccess) {
            // Po úspěšném provisioningu má appka preferovat mDNS endpoint.
            setPreferMdnsEndpoint(true)
        }

        return result
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
        return tryServices { service ->
            try {
                action(service)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun <T> tryServices(
        action: suspend (EspApiService) -> Result<T>
    ): Result<T> {
        var lastFailure: Throwable? = null

        for (service in orderedServices()) {
            val result = action(service)
            if (result.isSuccess) {
                return result
            }
            lastFailure = result.exceptionOrNull()
        }

        return Result.failure(lastFailure ?: Exception("Požadavek se nezdařil."))
    }

    private fun orderedServices(): List<EspApiService> {
        val fallback = provisioningApi ?: return listOf(api)
        return if (preferMdnsEndpoint) {
            listOf(fallback, api)
        } else {
            listOf(api, fallback)
        }
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
