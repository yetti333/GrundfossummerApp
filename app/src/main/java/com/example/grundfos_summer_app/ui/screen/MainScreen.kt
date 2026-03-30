package com.example.grundfos_summer_app.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.sharp.Description
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.sharp.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToProvisioning: () -> Unit,
    viewModel: MainViewModel
) {
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

    Scaffold(
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Titulek nahoře
                            Text(
                                text = "Grundfos Summer",
                                color = Color(0xFF9B111E), // RAL 3003 (Rubínová červená)
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                            )
                            // Tři velká tlačítka přes celou šířku
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Připojení
                                TechnicalButton(
                                    icon = Icons.Sharp.Wifi,
                                    label = "Připojení",
                                    color = if (uiState.isConnectionLost) MaterialTheme.colorScheme.error else Color(0xFF1565C0),
                                    onClick = {
                                        if (uiState.isConnectionLost) {
                                            viewModel.resetConnectionTimeout()
                                        }
                                        onNavigateToProvisioning()
                                    },
                                    modifier = Modifier.weight(1f),
                                    isPulsing = uiState.isLoading
                                )
                                // Nastavení
                                TechnicalButton(
                                    icon = Icons.Sharp.Settings,
                                    label = "Nastavení",
                                    color = Color(0xFF37474F), // technická ocelová šedá
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier.weight(1f)
                                )
                                // Zprávy (dříve Chyby)
                                TechnicalButton(
                                    icon = Icons.Sharp.Description,
                                    label = "Zprávy",
                                    color = Color(0xFF455A64), // modrošedá pro logy/zprávy
                                    onClick = onNavigateToMessages,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    val displayMode = if (uiState.isConnectionLost) "ODPOJENO" else {
                        val baseMode = uiState.status?.mode ?: "–"
                        uiState.status?.let { status ->
                            if (baseMode == "AUTO" && status.schedule != null) {
                                val schedule = status.schedule
                                "AUTO (${schedule.startHour.toString().padStart(2, '0')}:${schedule.startMinute.toString().padStart(2, '0')})"
                            } else {
                                baseMode
                            }
                        } ?: baseMode
                    }
                    StatusCard(
                        mode = displayMode,
                        baseMode = if (uiState.isConnectionLost) "ODPOJENO" else (uiState.status?.mode ?: "–"),
                        pumpRunning = if (uiState.isConnectionLost) false else (uiState.status?.pump?.running == true),
                        feedback = if (uiState.isConnectionLost) "???" else (uiState.status?.pump?.pulseCountLastMinute?.toString() ?: "–"),
                        feedbackStable = if (uiState.isConnectionLost) false else (uiState.status?.pump?.pulseOk == true),
                        bypass = uiState.status?.bypass == true,
                        wifiError = uiState.isConnectionLost || (uiState.status?.errors?.wifi == true),
                        timeError = uiState.status?.errors?.time == true,
                        pumpError = uiState.status?.errors?.pump == true,
                        onWifiErrorClick = {
                            if (uiState.isConnectionLost) {
                                viewModel.resetConnectionTimeout()
                            }
                            onNavigateToProvisioning()
                        },
                        onPumpErrorClick = viewModel::resetPumpError,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    ControlsCard(
                        mode = if (uiState.isConnectionLost) null else uiState.status?.mode,
                        pumpRunning = uiState.status?.pump?.running == true,
                        bypass = uiState.status?.bypass ?: false,
                        onModeSelected = viewModel::setMode,
                        onBypassChanged = viewModel::setBypass,
                        onPumpStart = viewModel::pumpStart,
                        onPumpStop = viewModel::pumpStop,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechnicalButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconModifier = if (isPulsing) {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Modifier.size(28.dp).scale(scale)
            } else {
                Modifier.size(28.dp)
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = iconModifier
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusCard(
    mode: String,
    baseMode: String,
    pumpRunning: Boolean,
    feedback: String,
    feedbackStable: Boolean,
    bypass: Boolean,
    wifiError: Boolean,
    timeError: Boolean,
    pumpError: Boolean,
    onWifiErrorClick: () -> Unit,
    onPumpErrorClick: () -> Unit,
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
            InfoRow("Režim:", mode, valueColor = when(baseMode) {
                "AUTO" -> if (pumpError) Color.Red else Color(0xFF388E3C)
                "MANUAL" -> Color(0xFF1565C0)
                else -> Color.Unspecified
            })
            IconRow("Čerpadlo běží:", pumpRunning, animated = true)
            InfoRow("Pulzy zpětné vazby:", feedback)
            IconRow("Zpětná vazba stabilní:", feedbackStable)
            IconRow("Bypass aktivní:", bypass)

            Text("Chyby:", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ErrorChip(
                    label = "WiFi", 
                    isError = wifiError,
                    onClick = onWifiErrorClick,
                    modifier = Modifier.weight(1f)
                )
                ErrorChip(label = "Čas", isError = timeError, modifier = Modifier.weight(1f))
                ErrorChip(
                    label = "Čerpadlo", 
                    isError = pumpError,
                    onClick = onPumpErrorClick,
                    modifier = Modifier.weight(1f)
                )
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
    val selectedIndex = if (mode == null) -1 else options.indexOf(mode).coerceAtLeast(0)

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
                        selected = selectedIndex == index,
                        enabled = mode != null
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
                    enabled = mode != null && !pumpRunning && mode != "MANUAL"
                )
            }

            Button(
                onClick = onPumpStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = mode == "MANUAL" && !pumpRunning
            ) {
                Text("Spustit čerpadlo")
            }

            Button(
                onClick = onPumpStop,
                modifier = Modifier.fillMaxWidth(),
                enabled = mode == "MANUAL" && pumpRunning
            ) {
                Text("Zastavit čerpadlo")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun IconRow(label: String, active: Boolean, animated: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        if (animated && active) {
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color(0xFF388E3C),
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
            )
        } else {
            Icon(
                imageVector = if (active) Icons.Default.Check else Icons.Default.Remove,
                contentDescription = null,
                tint = if (active) Color(0xFF388E3C) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorChip(
    label: String, 
    isError: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    AssistChip(
        onClick = onClick,
        label = { Text(modifier = Modifier.fillMaxWidth(), text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isError) Color(0xFFFDECEA) else Color(0xFFF5F5F5),
            labelColor = if (isError) Color(0xFFD32F2F) else Color.Gray
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = if (isError) Color(0xFFEF9A9A) else Color.LightGray
        ),
        modifier = modifier
    )
}
