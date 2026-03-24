package com.example.grundfos_summer_app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.data.remote.RetrofitProvider
import com.example.grundfos_summer_app.data.repository.EspRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
	val status: EspStatus? = null,
	val isLoading: Boolean = false,
	val errorMessage: String? = null,
	val baseUrl: String = "http://192.168.1.100"
)

class MainViewModel : ViewModel() {
	private val _uiState = MutableStateFlow(UiState())
	val uiState: StateFlow<UiState> = _uiState.asStateFlow()

	private var repository: EspRepository = EspRepository(
		RetrofitProvider.buildRetrofit(formatBaseUrl(_uiState.value.baseUrl))
	)

	init {
		viewModelScope.launch {
			while (true) {
				refreshStatus()
				delay(3000)
			}
		}
	}

	fun updateBaseUrl(url: String) {
		val updatedBaseUrl = url.trim()
		if (updatedBaseUrl == _uiState.value.baseUrl) {
			return
		}

		repository = EspRepository(
			RetrofitProvider.buildRetrofit(formatBaseUrl(updatedBaseUrl))
		)
		_uiState.value = _uiState.value.copy(
			baseUrl = updatedBaseUrl,
			status = null
		)

		viewModelScope.launch {
			refreshStatus()
		}
	}

	fun setBaseUrl(baseUrl: String) {
		updateBaseUrl(baseUrl)
	}

	fun setMode(mode: String) {
		viewModelScope.launch {
			repository.setMode(mode)
				.onSuccess { refreshStatus() }
				.onFailure { setError(it) }
		}
	}

	fun setBypass(enabled: Boolean) {
		viewModelScope.launch {
			repository.setBypass(enabled)
				.onSuccess { refreshStatus() }
				.onFailure { setError(it) }
		}
	}

	fun pumpStart() {
		viewModelScope.launch {
			repository.pumpStart()
				.onSuccess { refreshStatus() }
				.onFailure { setError(it) }
		}
	}

	fun pumpStop() {
		viewModelScope.launch {
			repository.pumpStop()
				.onSuccess { refreshStatus() }
				.onFailure { setError(it) }
		}
	}

	fun saveSettings(startTime: String, runMinutes: Int, feedbackTimeoutSec: Int) {
		viewModelScope.launch {
			repository.saveSettings(
					startTime = startTime,
					runMinutes = runMinutes,
					feedbackTimeoutSec = feedbackTimeoutSec
				)
				.onSuccess { refreshStatus() }
				.onFailure { setError(it) }
		}
	}

	fun clearErrorMessage() {
		_uiState.value = _uiState.value.copy(errorMessage = null)
	}

	private suspend fun refreshStatus() {
		_uiState.value = _uiState.value.copy(isLoading = true)

		repository.getStatus()
			.onSuccess { status ->
				_uiState.value = _uiState.value.copy(
					status = status,
					isLoading = false,
					errorMessage = null
				)
			}

			.onFailure { throwable ->
				_uiState.value = _uiState.value.copy(
					isLoading = false,
					errorMessage = throwable.message
				)
			}
	}

	private fun setError(throwable: Throwable?) {
		_uiState.value = _uiState.value.copy(
			errorMessage = throwable?.message,
			isLoading = false
		)
	}

	private fun formatBaseUrl(baseUrl: String): String {
		return if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
	}
}

