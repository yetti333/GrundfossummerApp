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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
	val uiState by viewModel.uiState.collectAsStateWithLifecycle()
	val snackbarHostState = remember { SnackbarHostState() }

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

	var startTime by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
	var runMinutesText by rememberSaveable { androidx.compose.runtime.mutableStateOf("5") }
	var feedbackTimeoutText by rememberSaveable { androidx.compose.runtime.mutableStateOf("60") }
	var isDebugUrlExpanded by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
	var urlInput by rememberSaveable { androidx.compose.runtime.mutableStateOf(uiState.baseUrl) }

	LaunchedEffect(uiState.baseUrl) {
		urlInput = uiState.baseUrl
	}

	Scaffold(
		topBar = {
			TopAppBar(title = { Text("Grundfos Controller") })
		},
		snackbarHost = {
			SnackbarHost(hostState = snackbarHostState)
		}
	) { paddingValues ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
		) {
			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				item {
					DebugUrlCard(
						isExpanded = isDebugUrlExpanded,
						url = urlInput,
						onToggleExpanded = { isDebugUrlExpanded = !isDebugUrlExpanded },
						onUrlChange = { urlInput = it },
						onConnect = { viewModel.updateBaseUrl(urlInput) },
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
					)
				}

				item {
					StatusCard(
						mode = uiState.status?.mode ?: "–",
						pumpRunning = uiState.status?.pumpRunning == true,
						feedback = uiState.status?.feedback?.toString() ?: "–",
						feedbackStable = uiState.status?.feedbackStable == true,
						bypass = uiState.status?.bypass == true,
						wifiError = uiState.status?.errors?.wifi == true,
						timeError = uiState.status?.errors?.time == true,
						pumpError = uiState.status?.errors?.pump == true,
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
					)
				}

				item {
					ControlsCard(
						mode = uiState.status?.mode,
						pumpRunning = uiState.status?.pumpRunning == true,
						bypass = uiState.status?.bypass ?: false,
						onModeSelected = viewModel::setMode,
						onBypassChanged = viewModel::setBypass,
						onPumpStart = viewModel::pumpStart,
						onPumpStop = viewModel::pumpStop,
						modifier = Modifier.padding(horizontal = 16.dp)
					)
				}

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
						},
						modifier = Modifier.padding(horizontal = 16.dp)
					)
				}
			}

			if (uiState.isLoading) {
				CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
			}
		}
	}
}

@Composable
private fun DebugUrlCard(
	isExpanded: Boolean,
	url: String,
	onToggleExpanded: () -> Unit,
	onUrlChange: (String) -> Unit,
	onConnect: () -> Unit,
	modifier: Modifier = Modifier
) {
	Card(modifier = modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text("Debug IP", style = MaterialTheme.typography.titleSmall)
				TextButton(onClick = onToggleExpanded) {
					Text(if (isExpanded) "Skrýt" else "Rozbalit")
				}
			}

			if (isExpanded) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					OutlinedTextField(
						value = url,
						onValueChange = onUrlChange,
						label = { Text("Base URL") },
						modifier = Modifier.weight(1f),
						singleLine = true
					)

					Button(onClick = onConnect) {
						Text("Připojit")
					}
				}
			}
		}
	}
}

@Composable
private fun StatusCard(
	mode: String,
	pumpRunning: Boolean,
	feedback: String,
	feedbackStable: Boolean,
	bypass: Boolean,
	wifiError: Boolean,
	timeError: Boolean,
	pumpError: Boolean,
	modifier: Modifier = Modifier
) {
	Card(modifier = modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			Text("Stav", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			InfoRow("Režim:", mode)
			IconRow("Čerpadlo běží:", pumpRunning)
			InfoRow("Pulzy zpětné vazby:", feedback)
			IconRow("Zpětná vazba stabilní:", feedbackStable)
			IconRow("Bypass aktivní:", bypass)

			Text("Chyby:", style = MaterialTheme.typography.bodyMedium)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				ErrorChip(label = "WiFi", isError = wifiError)
				ErrorChip(label = "Čas", isError = timeError)
				ErrorChip(label = "Čerpadlo", isError = pumpError)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsCard(
	mode: String?,
	pumpRunning: Boolean,
	bypass: Boolean,
	onModeSelected: (String) -> Unit,
	onBypassChanged: (Boolean) -> Unit,
	onPumpStart: () -> Unit,
	onPumpStop: () -> Unit,
	modifier: Modifier = Modifier
) {
	val options = listOf("AUTO", "MANUAL")
	val selectedIndex = options.indexOf(mode).coerceAtLeast(0)

	Card(modifier = modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text("Ovládání", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

			SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
				options.forEachIndexed { index, label ->
					SegmentedButton(
						shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
						onClick = { onModeSelected(label) },
						selected = selectedIndex == index
					) {
						Text(label)
					}
				}
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text("Bypass")
				Switch(
					checked = bypass,
					onCheckedChange = onBypassChanged,
					enabled = !pumpRunning
				)
			}

			Button(
				onClick = onPumpStart,
				enabled = mode == "MANUAL" && !pumpRunning,
				modifier = Modifier.fillMaxWidth()
			) {
				Text("Spustit čerpadlo")
			}

			Button(
				onClick = onPumpStop,
				enabled = pumpRunning,
				modifier = Modifier.fillMaxWidth()
			) {
				Text("Zastavit čerpadlo")
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

@Composable
private fun InfoRow(label: String, value: String) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(label)
		Text(value)
	}
}

@Composable
private fun IconRow(label: String, isActive: Boolean) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(label)
		Icon(
			imageVector = if (isActive) Icons.Default.Check else Icons.Default.Remove,
			contentDescription = null,
			tint = if (isActive) Color(0xFF2E7D32) else Color.Gray
		)
	}
}

@Composable
private fun ErrorChip(label: String, isError: Boolean) {
	val containerColor = if (isError) Color(0xFFB71C1C) else Color(0xFF1B5E20)
	AssistChip(
		onClick = {},
		label = { Text(label, color = Color.White) },
		colors = AssistChipDefaults.assistChipColors(containerColor = containerColor)
	)
}

