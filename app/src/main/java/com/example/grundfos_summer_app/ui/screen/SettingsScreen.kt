package com.example.grundfos_summer_app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.grundfos_summer_app.data.model.EspSchedule
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var startTime by rememberSaveable { mutableStateOf("") }
    var runMinutesText by rememberSaveable { mutableStateOf("5") }
    var feedbackTimeoutText by rememberSaveable { mutableStateOf("60") }

    // Validace formátu času HH:MM
    val isTimeValid = remember(startTime) {
        Regex("^([01]\\d|2[0-3]):([0-5]\\d)$").matches(startTime)
    }

    // Synchronizace polí se stavem ze serveru při načtení
    LaunchedEffect(uiState.status?.schedule) {
        uiState.status?.schedule?.let { schedule ->
            startTime = "%02d:%02d".format(schedule.startHour, schedule.startMinute)
            runMinutesText = schedule.durationMinutes.toString()
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                item {
                    CurrentScheduleCard(schedule = uiState.status?.schedule)
                }

                item {
                    PlanCard(
                        startTime = startTime,
                        isTimeValid = isTimeValid,
                        runMinutesText = runMinutesText,
                        feedbackTimeoutText = feedbackTimeoutText,
                        onStartTimeChanged = { input ->
                            // Povolit pouze číslice a dvojtečku, max 5 znaků
                            if (input.length <= 5 && input.all { it.isDigit() || it == ':' }) {
                                startTime = input
                            }
                        },
                        onRunMinutesChanged = { input ->
                            // Povolit pouze číslice
                            if (input.all { it.isDigit() }) {
                                runMinutesText = input
                            }
                        },
                        onFeedbackTimeoutChanged = { input ->
                            // Povolit pouze číslice
                            if (input.all { it.isDigit() }) {
                                feedbackTimeoutText = input
                            }
                        },
                        onSave = {
                            val runMinutes = runMinutesText.toIntOrNull() ?: 5
                            val feedbackTimeout = feedbackTimeoutText.toIntOrNull() ?: 60
                            viewModel.saveSettings(startTime, runMinutes, feedbackTimeout)
                        }
                    )
                }
            }

            if (uiState.isLoading && uiState.status == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun CurrentScheduleCard(
    schedule: EspSchedule?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Aktuální plán",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (schedule != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Čas spuštění",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "%02d:%02d".format(schedule.startHour, schedule.startMinute),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Doba běhu",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "${schedule.durationMinutes} min",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                Text(
                    "Data o plánu nejsou k dispozici",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    startTime: String,
    isTimeValid: Boolean,
    runMinutesText: String,
    feedbackTimeoutText: String,
    onStartTimeChanged: (String) -> Unit,
    onRunMinutesChanged: (String) -> Unit,
    onFeedbackTimeoutChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Validace: musí být číslo a větší než 0
    val isRunMinutesValid = remember(runMinutesText) { 
        runMinutesText.toIntOrNull()?.let { it > 0 } ?: false 
    }
    val isFeedbackTimeoutValid = remember(feedbackTimeoutText) { 
        feedbackTimeoutText.toIntOrNull()?.let { it > 0 } ?: false 
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Změnit nastavení", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = startTime,
                onValueChange = onStartTimeChanged,
                label = { Text("Čas spuštění (HH:MM)") },
                isError = !isTimeValid && startTime.isNotEmpty(),
                supportingText = {
                    if (!isTimeValid && startTime.isNotEmpty()) {
                        Text("Zadejte platný čas (např. 08:30)", color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("např. 07:30") }
            )

            OutlinedTextField(
                value = runMinutesText,
                onValueChange = onRunMinutesChanged,
                label = { Text("Doba běhu (min)") },
                isError = !isRunMinutesValid && runMinutesText.isNotEmpty(),
                supportingText = {
                    if (!isRunMinutesValid && runMinutesText.isNotEmpty()) {
                        Text("Zadejte číslo větší než 0", color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = feedbackTimeoutText,
                onValueChange = onFeedbackTimeoutChanged,
                label = { Text("Timeout zpětné vazby (s)") },
                isError = !isFeedbackTimeoutValid && feedbackTimeoutText.isNotEmpty(),
                supportingText = {
                    if (!isFeedbackTimeoutValid && feedbackTimeoutText.isNotEmpty()) {
                        Text("Zadejte číslo větší než 0", color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onSave,
                enabled = isTimeValid && isRunMinutesValid && isFeedbackTimeoutValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Uložit do Grundfos Summer")
            }
        }
    }
}
