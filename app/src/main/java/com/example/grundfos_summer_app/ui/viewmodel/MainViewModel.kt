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
	val isConnectionLost: Boolean = false
)

class MainViewModel : ViewModel() {
	private val _uiState = MutableStateFlow(UiState())
	val uiState: StateFlow<UiState> = _uiState.asStateFlow()

	private var retryCount = 0
	private val maxRetries = 10

	private val repository: EspRepository = EspRepository(
		RetrofitProvider.buildRetrofit("http://192.168.0.130/")
	)

	init {
		viewModelScope.launch {
			while (true) {
				refreshStatus()
				delay(3000)
			}
		}
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

	fun resetErrors() {
		viewModelScope.launch {
			repository.resetErrors()
				.onSuccess { refreshStatus() }
				.onFailure { setError(it) }
		}
	}

	fun resetConnectionTimeout() {
		retryCount = 0
		_uiState.value = _uiState.value.copy(
			isConnectionLost = false,
			errorMessage = null
		)
	}

	fun saveSettings(startTime: String, runMinutes: Int, feedbackTimeoutSec: Int) {
		viewModelScope.launch {
			val result: Result<Unit> = repository.saveSettings(
				startTime = startTime,
				runMinutes = runMinutes,
				feedbackTimeoutSec = feedbackTimeoutSec
			)
			result.onSuccess { refreshStatus() }
			result.onFailure { setError(it) }
		}
	}

	fun clearErrorMessage() {
		_uiState.value = _uiState.value.copy(errorMessage = null)
	}

	private suspend fun refreshStatus() {
		_uiState.value = _uiState.value.copy(isLoading = true)

		repository.getStatus()
			.onSuccess { status ->
				retryCount = 0
				_uiState.value = _uiState.value.copy(
					status = status,
					isLoading = false,
					errorMessage = null,
					isConnectionLost = false
				)
			}
			.onFailure { throwable ->
				retryCount++
				val isLost = retryCount >= maxRetries
				_uiState.value = _uiState.value.copy(
					isLoading = false,
					errorMessage = if (isLost) "Připojení k ESP ztraceno (10 neúspěšných pokusů)" else throwable.message,
					isConnectionLost = isLost
				)
			}
	}

	private fun setError(throwable: Throwable?) {
		_uiState.value = _uiState.value.copy(
			errorMessage = throwable?.message,
			isLoading = false
		)
	}
}
