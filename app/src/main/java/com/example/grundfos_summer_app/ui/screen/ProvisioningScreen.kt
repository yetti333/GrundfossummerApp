package com.example.grundfos_summer_app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.sharp.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.grundfos_summer_app.data.model.EspStatus
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showInfo by rememberSaveable { mutableStateOf(true) }

    val isSsidValid = remember(ssid) { ssid.trim().isNotEmpty() }
    val showInitialLoading = uiState.isLoading && uiState.status == null && !uiState.isConnectionLost

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "OK",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.Dismissed || result == SnackbarResult.ActionPerformed) {
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.provisioningSuccessMessage) {
        val message = uiState.provisioningSuccessMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "OK",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.Dismissed || result == SnackbarResult.ActionPerformed) {
            viewModel.clearProvisioningSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Připojení a provisioning") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatusNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Obnovit stav")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showInitialLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showInfo = true },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (showInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Informace")
                            }
                            Button(
                                onClick = { showInfo = false },
                                modifier = Modifier.weight(1f),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (!showInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Icon(Icons.Sharp.Wifi, contentDescription = null)
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Připojení")
                            }
                        }
                    }
                    item {
                        ProvisioningStateCard(
                            showInfo = showInfo,
                            isConnectionLost = uiState.isConnectionLost,
                            errorMessage = uiState.errorMessage,
                            status = uiState.status
                        )
                    }

                    if (showInfo) {
                        val status = uiState.status
                        item {
                            DeviceOverviewCard(status = status)
                        }
                        item {
                            DeviceDetailsCard(
                                status = status,
                                lastSuccessfulStatusAt = uiState.lastSuccessfulStatusAt
                            )
                        }
                    } else {
                        item {
                            ProvisioningIntroCard()
                        }
                        item {
                            ProvisioningStepsCard()
                        }
                        item {
                            ProvisioningFormCard(
                                ssid = ssid,
                                password = password,
                                isSsidValid = isSsidValid,
                                isSubmitting = uiState.isProvisioningSubmitting,
                                onSsidChange = { ssid = it },
                                onPasswordChange = { password = it },
                                onSubmit = {
                                    viewModel.submitProvisioning(
                                        ssid = ssid,
                                        password = password
                                    )
                                }
                            )
                        }
                    }

                    item {
                        RefreshCard(
                            isRefreshing = uiState.isLoading,
                            showInfo = showInfo,
                            onRefresh = { viewModel.refreshStatusNow() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvisioningStateCard(
    showInfo: Boolean,
    isConnectionLost: Boolean,
    errorMessage: String?,
    status: EspStatus?,
    modifier: Modifier = Modifier
) {
    val isProvisioningDetected = status != null && !showInfo
    val containerColor = when {
        showInfo -> MaterialTheme.colorScheme.primaryContainer
        isConnectionLost -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when {
        showInfo -> MaterialTheme.colorScheme.onPrimaryContainer
        isConnectionLost -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showInfo) "Připojené zařízení" else "Provisioning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                StatusChip(
                    label = when {
                        showInfo -> "Dostupné"
                        isProvisioningDetected -> "Čeká na nastavení"
                        else -> "Nedostupné"
                    },
                    containerColor = when {
                        showInfo -> Color(0xFFD9F2E3)
                        isProvisioningDetected -> Color(0xFFFFF3CD)
                        else -> Color(0xFFFDECEA)
                    },
                    labelColor = when {
                        showInfo -> Color(0xFF2E7D32)
                        isProvisioningDetected -> Color(0xFF8D6E00)
                        else -> Color(0xFFC62828)
                    }
                )
            }

            Text(
                text = when {
                    showInfo -> "ESP zařízení odpovídá a jsou k dispozici stavové informace. Níže vidíte přehled síťových a systémových údajů."
                    isConnectionLost -> "Zařízení se nepodařilo kontaktovat. Pokud je v AP režimu, připojte telefon k síti Grundfos-Provision a odešlete nové Wi‑Fi údaje."
                    else -> "Zařízení čeká na nastavení Wi‑Fi nebo běží v provisioning/AP režimu. Připojte se k jeho dočasné síti a odešlete cílové přihlašovací údaje."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )

            if (!errorMessage.isNullOrBlank() && !showInfo) {
                Text(
                    text = "Poslední chyba: $errorMessage",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun DeviceOverviewCard(
    status: EspStatus?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Přehled zařízení", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LabelValueRow("IP adresa", status?.resolvedIpAddress().orNotAvailable())
            LabelValueRow("MAC adresa", status?.resolvedMacAddress().orNotAvailable())
            LabelValueRow("Hostname", status?.resolvedHostname().orNotAvailable())
            LabelValueRow("mDNS", status?.resolvedMdnsHost().orNotAvailable(defaultValue = "grundfos-pump.local"))
            LabelValueRow("Stav připojení", status.connectionStateLabel())
            LabelValueRow("Wi‑Fi SSID", status?.resolvedSsid().orNotAvailable())
            LabelValueRow("State", status?.state.orNotAvailable())
            LabelValueRow("Mode", status?.mode.orNotAvailable())
        }
    }
}

@Composable
private fun DeviceDetailsCard(
    status: EspStatus?,
    lastSuccessfulStatusAt: Long?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Další informace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            LabelValueRow("RSSI / signál", status?.resolvedRssi()?.let { "$it dBm" }.orNotAvailable())
            LabelValueRow("Uptime", status?.resolvedUptime().orNotAvailable())
            LabelValueRow("Firmware", status?.resolvedFirmwareVersion().orNotAvailable())
            LabelValueRow("AP režim", status.booleanFlagLabel { resolvedApMode() })
            LabelValueRow("Station režim", status.booleanFlagLabel { resolvedStationMode() })
            LabelValueRow("Poslední komunikace", status.resolvedLastSeen(lastSuccessfulStatusAt))
        }
    }
}

@Composable
private fun ProvisioningIntroCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Sharp.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Zařízení čeká na nastavení Wi‑Fi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = "Pokud ESP není připojeno do cílové sítě, přepne se do provisioning režimu. Připojte telefon k jeho dočasné Wi‑Fi a odešlete cílové přihlašovací údaje.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            LabelValueRow(
                label = "Provisioning Wi‑Fi",
                value = "Grundfos-Provision",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            LabelValueRow(
                label = "Heslo",
                value = "grundfos123",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            LabelValueRow(
                label = "mDNS adresa",
                value = "grundfos-pump.local",
                valueColor = MaterialTheme.colorScheme.onSecondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ProvisioningStepsCard(
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        "Připoj telefon k Wi‑Fi Grundfos-Provision",
        "Heslo je grundfos123",
        "Zařízení je dostupné na grundfos-pump.local",
        "Zadej cílovou Wi‑Fi síť a heslo",
        "Odešli provisioning",
        "Počkej na připojení zařízení do cílové sítě"
    )

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Postup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ProvisioningFormCard(
    ssid: String,
    password: String,
    isSsidValid: Boolean,
    isSubmitting: Boolean,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Odeslat provisioning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SSID") },
                placeholder = { Text("Název cílové Wi‑Fi") },
                isError = ssid.isNotEmpty() && !isSsidValid,
                supportingText = {
                    if (ssid.isNotEmpty() && !isSsidValid) {
                        Text(
                            text = "SSID nesmí být prázdné.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                singleLine = true,
                enabled = !isSubmitting
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                placeholder = { Text("Heslo cílové Wi‑Fi") },
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                        enabled = !isSubmitting
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (isPasswordVisible) {
                                "Skrýt heslo"
                            } else {
                                "Zobrazit heslo"
                            }
                        )
                    }
                },
                singleLine = true,
                enabled = !isSubmitting
            )

            Button(
                onClick = onSubmit,
                enabled = isSsidValid && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Odesílám provisioning...")
                } else {
                    Text("Odeslat provisioning")
                }
            }
        }
    }
}

@Composable
private fun RefreshCard(
    isRefreshing: Boolean,
    showInfo: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Aktualizace stavu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = if (showInfo) {
                    "Načte aktuální informace o zařízení a ověří, zda je stále dostupné."
                } else {
                    "Po odeslání údajů nebo po připojení telefonu k AP síti můžete ručně ověřit aktuální stav zařízení."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            TextButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (isRefreshing) "Obnovuji..." else "Obnovit stav")
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    containerColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                color = labelColor
            )
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = containerColor)
    )
}

@Composable
private fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun String?.orNotAvailable(defaultValue: String = "Není k dispozici"): String {
    return this?.takeIf { it.isNotBlank() } ?: defaultValue
}

private fun EspStatus?.connectionStateLabel(): String {
    if (this == null) return "Není k dispozici"

    return when {
        networkInfo?.connected == true -> "Připojeno"
        resolvedApMode() == true -> "Provisioning / AP režim"
        errors.wifi -> "Wi‑Fi chyba"
        else -> "Dostupné"
    }
}

private inline fun EspStatus?.booleanFlagLabel(selector: EspStatus.() -> Boolean?): String {
    val value = this?.selector()
    return when (value) {
        true -> "Ano"
        false -> "Ne"
        null -> "Není k dispozici"
    }
}

private fun EspStatus?.resolvedLastSeen(lastSuccessfulStatusAt: Long?): String {
    val serverValue = this?.networkInfo?.lastSeen ?: this?.lastSeen
    if (!serverValue.isNullOrBlank()) {
        return serverValue
    }

    return lastSuccessfulStatusAt?.let(::formatTimestamp) ?: "Není k dispozici"
}

private fun EspStatus.resolvedIpAddress(): String? = networkInfo?.ip ?: ip

private fun EspStatus.resolvedMacAddress(): String? = networkInfo?.macAddress ?: macAddress

private fun EspStatus.resolvedSsid(): String? = networkInfo?.ssid ?: ssid

private fun EspStatus.resolvedHostname(): String? =
    deviceInfo?.hostname ?: networkInfo?.hostname ?: hostname

private fun EspStatus.resolvedMdnsHost(): String =
    deviceInfo?.mdnsHost ?: networkInfo?.mdnsHost ?: mdnsHost ?: "grundfos-pump.local"

private fun EspStatus.resolvedRssi(): Int? = networkInfo?.rssi ?: rssi

private fun EspStatus.resolvedFirmwareVersion(): String? =
    deviceInfo?.firmwareVersion ?: firmwareVersion

private fun EspStatus.resolvedUptime(): String? {
    return deviceInfo?.uptime?.takeIf { it.isNotBlank() }
        ?: uptime?.takeIf { it.isNotBlank() }
        ?: deviceInfo?.uptimeSeconds?.let(::formatDuration)
        ?: uptimeSeconds?.let(::formatDuration)
}

private fun EspStatus.resolvedApMode(): Boolean? = networkInfo?.apMode ?: apMode

private fun EspStatus.resolvedStationMode(): Boolean? = networkInfo?.stationMode ?: stationMode

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) append("${hours} h ")
        if (minutes > 0 || hours > 0) append("${minutes} min ")
        append("${seconds} s")
    }.trim()
}
