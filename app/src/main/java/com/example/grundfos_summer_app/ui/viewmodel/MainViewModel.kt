package com.example.grundfos_summer_app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.grundfos_summer_app.data.discovery.NsdDiscovery
import com.example.grundfos_summer_app.data.local.DeviceIpStore
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
    val provisioningSuccessMessage: String? = null,
    val isConnectionLost: Boolean = false,
    val isProvisioningRequired: Boolean = true,
    val isProvisioningSubmitting: Boolean = false,
    val lastSuccessfulStatusAt: Long? = null,
    val logs: List<LogEntry> = emptyList(),
    val selectedLog: LogEntry? = null,
    val showDeviceInfoInProvisioning: Boolean = false
)

/**
 * MainViewModel uses AndroidViewModel to access Application context for:
 *  - DeviceIpStore (SharedPreferences IP persistence)
 *  - NsdDiscovery (NsdManager requires Context)
 *
 * Connection flow:
 *  1. Load cached IP from DeviceIpStore → use directly for HTTP (no DNS lookup)
 *  2. If no cached IP → run NSD discovery → cache discovered IP
 *  3. Poll /status every 2 s while app is active
 *  4. On connection failure → retry once → trigger background NSD rediscovery
 *  5. Provisioning always goes via hardcoded AP IP (10.66.12.184)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var retryCount = 0
    private val maxRetries = 1
    private var lastWifiError = false
    private var lastTimeError = false
    private var lastPumpError = false

    /** Guard against launching multiple concurrent NSD discoveries. */
    @Volatile private var isDiscovering = false

    private val ipStore = DeviceIpStore(application)
    private val nsdDiscovery = NsdDiscovery(application)

    private companion object {
        private const val TAG = "ESP_API"

        /** Fixed AP IP used only during provisioning (ESP access-point mode). */
        const val PROVISIONING_AP_URL = "http://10.66.12.184/"

        /** Placeholder primary URL; overridden immediately from cached IP or NSD. */
        const val INITIAL_BASE_URL = "http://grundfos-pump.local/"

        /** Status poll interval in ms (reference recommends 2 s for /heartbeat; we poll /status). */
        const val POLL_INTERVAL_MS = 2000L
    }

    private val repository: EspRepository = EspRepository(
        api = RetrofitProvider.buildRetrofit(INITIAL_BASE_URL),
        provisioningApi = RetrofitProvider.buildRetrofit(PROVISIONING_AP_URL),
        primaryBaseUrl = INITIAL_BASE_URL,
        apiFactory = RetrofitProvider::buildRetrofit
    )

    init {
        viewModelScope.launch {
            // Phase 1: apply cached IP or run NSD discovery before first poll
            setupInitialConnection()

            // Phase 2: polling loop – every 2 s as recommended by the ESP reference
            while (true) {
                refreshStatusInternal(showLoading = _uiState.value.status == null)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // ── Connection setup ──────────────────────────────────────────────────────

    private suspend fun setupInitialConnection() {
        val cachedUrl = ipStore.buildBaseUrl()
        if (cachedUrl != null) {
            Log.d(TAG, "Startup: using cached IP ${ipStore.cachedIp}")
            repository.updatePrimaryUrl(cachedUrl)
        } else {
            Log.d(TAG, "Startup: no cached IP, running NSD discovery")
            addLog("Hledám ESP v síti (NSD)...", false)
            runNsdDiscovery()
        }
    }

    /**
     * Runs NSD discovery, stores discovered IP and updates repository.
     * Called at startup (no cached IP) or after connection loss.
     */
    private suspend fun runNsdDiscovery() {
        val ip = nsdDiscovery.discoverEspIp(timeoutMs = 6000L)
        if (ip != null) {
            Log.d(TAG, "NSD: discovered ESP at $ip")
            ipStore.cachedIp = ip
            repository.updatePrimaryUrl("http://$ip/")
            addLog("ESP nalezeno na IP $ip", false)
        } else {
            Log.w(TAG, "NSD: discovery timed out / no service found")
            addLog("NSD: ESP nenalezeno v síti", true)
        }
    }

    /**
     * Triggers a background NSD rediscovery after connection loss.
     * Protected by [isDiscovering] flag so it is never called concurrently.
     */
    private fun triggerBackgroundRediscovery() {
        if (isDiscovering) return
        isDiscovering = true
        viewModelScope.launch {
            try {
                addLog("Obnovuji připojení – hledám ESP v síti...", false)
                runNsdDiscovery()
                // Reset retry counter so the next poll attempt is treated as a fresh start
                retryCount = 0
            } finally {
                isDiscovering = false
            }
        }
    }

    // ── Public ViewModel API ──────────────────────────────────────────────────

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
                    if (mode == "MANUAL") {
                        setBypass(true)
                    } else {
                        refreshStatusInternal(showLoading = false)
                    }
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
                    refreshStatusInternal(showLoading = false)
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
                    refreshStatusInternal(showLoading = false)
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
                    refreshStatusInternal(showLoading = false)
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
                    refreshStatusInternal(showLoading = false)
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
                    refreshStatusInternal(showLoading = false)
                }
                .onFailure {
                    addLog("Chyba při resetu čerpadla: ${it.message}", true)
                    setError(it)
                }
        }
    }

    fun resetConnectionTimeout() {
        retryCount = 0
        _uiState.update {
            it.copy(
                isConnectionLost = false,
                errorMessage = null,
                isProvisioningRequired = computeProvisioningRequired(
                    status = it.status,
                    isConnectionLost = false
                ),
                showDeviceInfoInProvisioning = false
            )
        }
        addLog("Obnovování připojení...", false)
        refreshStatusNow(showLoading = false)
    }

    fun saveSettings(startTime: String, runMinutes: Int, feedbackTimeoutSec: Int) {
        viewModelScope.launch {
            val result: Result<Unit> = repository.saveSettings(
                startTime,
                runMinutes,
                feedbackTimeoutSec
            )
            result.onSuccess {
                addLog("Změna nastavení plánu spouštění", false)
                refreshStatusInternal(showLoading = false)
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

    fun clearProvisioningSuccessMessage() {
        _uiState.update { it.copy(provisioningSuccessMessage = null) }
    }

    fun toggleDeviceInfoInProvisioning() {
        _uiState.update { it.copy(showDeviceInfoInProvisioning = !it.showDeviceInfoInProvisioning) }
    }

    fun refreshStatusNow(showLoading: Boolean = true) {
        viewModelScope.launch {
            refreshStatusInternal(showLoading = showLoading)
        }
    }

    fun submitProvisioning(ssid: String, password: String) {
        val trimmedSsid = ssid.trim()
        if (trimmedSsid.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "SSID nesmí být prázdné.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProvisioningSubmitting = true,
                    provisioningSuccessMessage = null,
                    errorMessage = null
                )
            }

            repository.submitProvisioning(trimmedSsid, password)
                .onSuccess {
                    addLog("Provisioning Wi\u2011Fi byl odeslán pro síť $trimmedSsid", false)
                    _uiState.update { currentState ->
                        currentState.copy(
                            isProvisioningSubmitting = false,
                            provisioningSuccessMessage = "Přihlašovací údaje byly odeslány. Zařízení se nyní pokusí připojit k zadané síti.",
                            errorMessage = null
                        )
                    }
                    // Clear cached IP – device restarts and may get a new DHCP IP
                    ipStore.cachedIp = null
                    delay(1000)
                    // Re-discover the device on the home network after provisioning
                    addLog("Hledám ESP v nové síti po provisioningu...", false)
                    runNsdDiscovery()
                    refreshStatusInternal(showLoading = false)
                }
                .onFailure {
                    addLog("Chyba při provisioningu: ${it.message}", true)
                    _uiState.update { currentState ->
                        currentState.copy(
                            isProvisioningSubmitting = false,
                            errorMessage = it.message ?: "Provisioning se nezdařil."
                        )
                    }
                }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun refreshStatusInternal(showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        repository.getStatus()
            .onSuccess { status ->
                if (retryCount >= maxRetries) {
                    addLog("Připojení k ESP obnoveno", false)
                }
                retryCount = 0

                // Log new error conditions
                if (status.errors.wifi && !lastWifiError) addLog("Chyba WiFi hlášena modulem", true)
                if (status.errors.time && !lastTimeError) addLog("Chyba času hlášena modulem", true)
                if (status.errors.pump && !lastPumpError) addLog("Chyba čerpadla hlášena modulem", true)

                lastWifiError = status.errors.wifi
                lastTimeError = status.errors.time
                lastPumpError = status.errors.pump

                // Persist newly discovered IP from status response (ESP may report its own IP)
                val reportedIp = status.networkInfo?.ip ?: status.ip
                if (!reportedIp.isNullOrBlank() && reportedIp != ipStore.cachedIp) {
                    Log.d(TAG, "Updating cached IP from status response: $reportedIp")
                    ipStore.cachedIp = reportedIp
                    repository.updatePrimaryUrl("http://$reportedIp/")
                }

                _uiState.update {
                    it.copy(
                        status = status,
                        isLoading = false,
                        errorMessage = null,
                        isConnectionLost = false,
                        isProvisioningRequired = computeProvisioningRequired(
                            status = status,
                            isConnectionLost = false
                        ),
                        lastSuccessfulStatusAt = System.currentTimeMillis(),
                        showDeviceInfoInProvisioning = false
                    )
                }
            }
            .onFailure { throwable ->
                val wasLost = retryCount >= maxRetries
                retryCount++
                val isLost = retryCount >= maxRetries

                if (isLost && !wasLost) {
                    addLog("Kritické: Spojení s ESP modulem přerušeno (${throwable.message})", true)
                    // Reference: on IOException/timeout retry once → re-run NSD discovery
                    triggerBackgroundRediscovery()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (isLost) "Připojení k ESP ztraceno" else throwable.message,
                        isConnectionLost = isLost,
                        isProvisioningRequired = computeProvisioningRequired(
                            status = it.status,
                            isConnectionLost = isLost
                        ),
                        showDeviceInfoInProvisioning = false
                    )
                }
            }
    }

    private fun computeProvisioningRequired(
        status: EspStatus?,
        isConnectionLost: Boolean
    ): Boolean {
        if (isConnectionLost || status == null) {
            return true
        }

        if (status.provisioningRequired == true || status.deviceInfo?.provisioningRequired == true) {
            return true
        }

        if (status.apMode == true || status.networkInfo?.apMode == true) {
            return true
        }

        val state = status.state.uppercase()
        if (state.contains("PROVISION")) {
            return true
        }

        val connected = status.networkInfo?.connected
        val stationMode = status.networkInfo?.stationMode ?: status.stationMode
        val hasWifiConfig = !status.networkInfo?.ssid.isNullOrBlank() || !status.ssid.isNullOrBlank()
        val wifiErrorSuggestsProvisioning = status.errors.wifi && (
            connected == false ||
                stationMode == false ||
                status.apMode == true ||
                status.networkInfo?.apMode == true ||
                (!hasWifiConfig && state == "WIFI_ERROR")
            )

        if (wifiErrorSuggestsProvisioning) {
            return true
        }

        return false
    }

    private fun setError(throwable: Throwable?) {
        _uiState.update {
            it.copy(
                errorMessage = throwable?.message,
                isLoading = false
            )
        }
    }
}
