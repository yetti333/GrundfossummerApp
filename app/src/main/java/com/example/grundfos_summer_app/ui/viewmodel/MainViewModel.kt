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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val isError: Boolean
)

data class UiState(
    val status: EspStatus? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnectionLost: Boolean = false,
    val logs: List<LogEntry> = emptyList(),
    val selectedLog: LogEntry? = null
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var retryCount = 0
    private val maxRetries = 1
    private var lastWifiError = false
    private var lastTimeError = false
    private var lastPumpError = false

    private val repository: EspRepository = EspRepository(
        RetrofitProvider.buildRetrofit("http://10.66.12.184/")
    )

    init {
        viewModelScope.launch {
            while (true) {
                refreshStatus()
                delay(3000)
            }
        }
    }

    private fun addLog(message: String, isError: Boolean) {
        val newLog = LogEntry(message = message, isError = isError)
        _uiState.update { currentState ->
            val currentLogs = currentState.logs.toMutableList()
            currentLogs.add(0, newLog)
            if (currentLogs.size > 15) {
                currentLogs.removeAt(currentLogs.size - 1)
            }
            currentState.copy(logs = currentLogs)
        }
    }

    fun selectLog(log: LogEntry?) {
        _uiState.update { it.copy(selectedLog = log) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList(), selectedLog = null) }
    }

    fun setMode(mode: String) {
        viewModelScope.launch {
            repository.setMode(mode)
                .onSuccess { 
                    addLog("Změna režimu na $mode", false)
                    refreshStatus() 
                }
                .onFailure { 
                    addLog("Chyba při změně režimu: ${it.message}", true)
                    setError(it) 
                }
        }
    }

    fun setBypass(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBypass(enabled)
                .onSuccess { 
                    addLog("Bypass ${if (enabled) "zapnut" else "vypnut"}", false)
                    refreshStatus() 
                }
                .onFailure { 
                    addLog("Chyba bypassu: ${it.message}", true)
                    setError(it) 
                }
        }
    }

    fun pumpStart() {
        viewModelScope.launch {
            repository.pumpStart()
                .onSuccess { 
                    addLog("Start čerpadla", false)
                    refreshStatus() 
                }
                .onFailure { 
                    addLog("Chyba při startu čerpadla: ${it.message}", true)
                    setError(it) 
                }
        }
    }

    fun pumpStop() {
        viewModelScope.launch {
            repository.pumpStop()
                .onSuccess { 
                    addLog("Zastavení čerpadla", false)
                    refreshStatus() 
                }
                .onFailure { 
                    addLog("Chyba při zastavení čerpadla: ${it.message}", true)
                    setError(it) 
                }
        }
    }

    fun resetErrors() {
        viewModelScope.launch {
            repository.resetErrors()
                .onSuccess { 
                    addLog("Reset chyb proveden", false)
                    refreshStatus() 
                    _uiState.update { it.copy(selectedLog = null) }
                }
                .onFailure { 
                    addLog("Chyba při resetu: ${it.message}", true)
                    setError(it) 
                }
        }
    }

    fun resetPumpError() {
        viewModelScope.launch {
            repository.resetPumpError()
                .onSuccess { 
                    addLog("Reset chyby čerpadla", false)
                    refreshStatus() 
                }
                .onFailure { 
                    addLog("Chyba při resetu čerpadla: ${it.message}", true)
                    setError(it) 
                }
        }
    }

    fun resetConnectionTimeout() {
        retryCount = 0
        _uiState.update { it.copy(
            isConnectionLost = false,
            errorMessage = null
        )}
        addLog("Obnovování připojení...", false)
    }

    fun saveSettings(startTime: String, runMinutes: Int, feedbackTimeoutSec: Int) {
        viewModelScope.launch {
            val result: Result<Unit> = repository.saveSettings(
                startTime = startTime,
                runMinutes = runMinutes,
                feedbackTimeoutSec = feedbackTimeoutSec
            )
            result.onSuccess { 
                addLog("Změna nastavení plánu spouštění", false)
                refreshStatus() 
            }
            result.onFailure { 
                addLog("Chyba při ukládání nastavení: ${it.message}", true)
                setError(it) 
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun refreshStatus() {
        _uiState.update { it.copy(isLoading = true) }

        repository.getStatus()
            .onSuccess { status ->
                if (retryCount >= maxRetries) {
                    addLog("Připojení k ESP obnoveno", false)
                }
                retryCount = 0
                
                // Kontrola změn v chybách pro logování
                if (status.errors.wifi && !lastWifiError) addLog("Chyba WiFi hlášena modulem", true)
                if (status.errors.time && !lastTimeError) addLog("Chyba času hlášena modulem", true)
                if (status.errors.pump && !lastPumpError) addLog("Chyba čerpadla hlášena modulem", true)
                
                lastWifiError = status.errors.wifi
                lastTimeError = status.errors.time
                lastPumpError = status.errors.pump

                _uiState.update { it.copy(
                    status = status,
                    isLoading = false,
                    errorMessage = null,
                    isConnectionLost = false
                )}
            }
            .onFailure { throwable ->
                val wasLost = retryCount >= maxRetries
                retryCount++
                val isLost = retryCount >= maxRetries
                
                if (isLost && !wasLost) {
                    addLog("Kritické: Spojení s ESP modulem přerušeno (${throwable.message})", true)
                }

                _uiState.update { it.copy(
                    isLoading = false,
                    errorMessage = if (isLost) "Připojení k ESP ztraceno" else throwable.message,
                    isConnectionLost = isLost
                )}
            }
    }

    private fun setError(throwable: Throwable?) {
        _uiState.update { it.copy(
            errorMessage = throwable?.message,
            isLoading = false
        )}
    }
}
