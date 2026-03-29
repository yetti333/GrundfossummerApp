package com.example.grundfos_summer_app.data.repository

import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.model.EspSchedule
import com.example.grundfos_summer_app.data.remote.EspApiService
import com.google.gson.Gson
import java.io.IOException
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.HttpException

class EspRepository(private val api: EspApiService) {
    private val gson = Gson()

    suspend fun getStatus(): Result<EspStatus> {
        return try {
            Result.success(api.getStatus())
        } catch (e: HttpException) {
            Result.failure(Exception("HTTP ${e.code()}"))
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setMode(mode: String): Result<Unit> {
        return try {
            tryUnitResponses(
                listOf(
                    { api.setMode(createJsonBody(gson.toJson(mapOf("mode" to mode)))) },
                    { api.setMode(createJsonBody(gson.toJson(mapOf("mode" to normalizeModeState(mode))))) },
                    { api.setMode(createJsonBody(gson.toJson(mapOf("state" to normalizeModeState(mode))))) },
                    { api.setMode(createJsonBody(gson.toJson(mapOf("mode" to mode.lowercase())))) }
                )
            )
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setBypass(enabled: Boolean): Result<Unit> {
        return try {
            tryUnitResponses(
                listOf(
                    { api.setBypass(createJsonBody(gson.toJson(mapOf("bypass" to enabled)))) },
                    { api.setBypass(createJsonBody(gson.toJson(mapOf("enabled" to enabled)))) },
                    { api.setBypass(createJsonBody(gson.toJson(mapOf("bypass" to if (enabled) 1 else 0)))) },
                    { api.setBypass(createJsonBody(gson.toJson(mapOf("enabled" to if (enabled) 1 else 0)))) }
                )
            )
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pumpStart(): Result<Unit> {
        return try {
            handleUnitResponse(api.pumpStart())
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pumpStop(): Result<Unit> {
        return try {
            handleUnitResponse(api.pumpStop())
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetErrors(): Result<Unit> {
        return try {
            handleUnitResponse(api.resetErrors())
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPumpError(): Result<Unit> {
        return try {
            handleUnitResponse(api.resetPumpError(createJsonBody("{}")))
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSettings(
        startTime: String,
        runMinutes: Int,
        feedbackTimeoutSec: Int
    ): Result<Unit> {
        return try {
            val parts = startTime.split(":")
            val startHour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val startMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val schedule = EspSchedule(
                startHour = startHour,
                startMinute = startMinute,
                durationMinutes = runMinutes
            )
            tryUnitResponses(
                listOf(
                    { api.saveSchedule(createJsonBody(gson.toJson(schedule))) },
                    {
                        api.saveSchedule(
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
                        api.saveSchedule(
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
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun handleUnitResponse(response: Response<Unit>): Result<Unit> {
        return if (response.isSuccessful) {
            Result.success(Unit)
        } else {
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
