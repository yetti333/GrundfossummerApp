package com.example.grundfos_summer_app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var startTime by rememberSaveable { mutableStateOf("") }
    var runMinutesText by rememberSaveable { mutableStateOf("5") }
    var feedbackTimeoutText by rememberSaveable { mutableStateOf("60") }

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
                    PlanCard(
                        startTime = startTime,
                        runMinutesText = runMinutesText,
                        feedbackTimeoutText = feedbackTimeoutText,
                        onStartTimeChanged = { startTime = it },
                        onRunMinutesChanged = { runMinutesText = it },
                        onFeedbackTimeoutChanged = { feedbackTimeoutText = it },
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
private fun PlanCard(
    startTime: String,
    runMinutesText: String,
    feedbackTimeoutText: String,
    onStartTimeChanged: (String) -> Unit,
    onRunMinutesChanged: (String) -> Unit,
    onFeedbackTimeoutChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Plán spouštění", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = startTime,
                onValueChange = onStartTimeChanged,
                label = { Text("Čas spuštění (HH:MM)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = runMinutesText,
                onValueChange = onRunMinutesChanged,
                label = { Text("Doba běhu (min)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = feedbackTimeoutText,
                onValueChange = onFeedbackTimeoutChanged,
                label = { Text("Timeout zpětné vazby (s)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Uložit")
            }
        }
    }
}
