package com.example.grundfos_summer_app.data.repository

import com.example.grundfos_summer_app.data.model.EspSettings
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.remote.EspApiService
import java.io.IOException
import retrofit2.Response
import retrofit2.HttpException

class EspRepository(private val api: EspApiService) {
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
			handleUnitResponse(api.setMode(mapOf("mode" to mode)))
		} catch (e: IOException) {
			Result.failure(e)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun setBypass(enabled: Boolean): Result<Unit> {
		return try {
			handleUnitResponse(api.setBypass(mapOf("enabled" to enabled)))
		} catch (e: IOException) {
			Result.failure(e)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun pumpStart(): Result<Unit> {
		return try {
			handleUnitResponse(api.pumpStart(emptyMap()))
		} catch (e: IOException) {
			Result.failure(e)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}

	suspend fun pumpStop(): Result<Unit> {
		return try {
			handleUnitResponse(api.pumpStop(emptyMap()))
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
			val settings = EspSettings(
				startTime = startTime,
				runMinutes = runMinutes,
				feedbackTimeout = feedbackTimeoutSec
			)
			handleUnitResponse(api.saveSettings(settings))
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
			Result.failure(Exception("HTTP ${response.code()}"))
		}
	}
}

