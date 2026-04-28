package com.example.grundfos_summer_app.ui.screen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.grundfos_summer_app.R
import com.example.grundfos_summer_app.data.model.PumpInterpretedState
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpDetailsScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pump = uiState.status?.pump
    val showPumpDetails = pump?.running == true
    val interpretedState = if (showPumpDetails) {
        pump?.interpretedState ?: PumpInterpretedState.UNKNOWN
    } else {
        PumpInterpretedState.UNKNOWN
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.pump_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.pump_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.pump_details_source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_period_ms),
                        value = if (showPumpDetails) {
                            pump?.periodMs?.let { stringResource(id = R.string.format_ms_2, it) }
                                ?: stringResource(id = R.string.pump_default_value)
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        }
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_high_ms),
                        value = if (showPumpDetails) {
                            pump?.highMs?.let { stringResource(id = R.string.format_ms_2, it) }
                                ?: stringResource(id = R.string.pump_default_value)
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        }
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_duty_percent),
                        value = if (showPumpDetails) {
                            pump?.dutyPercent?.let { stringResource(id = R.string.format_percent_1, it) }
                                ?: stringResource(id = R.string.pump_default_value)
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        }
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_power_estimate),
                        value = if (showPumpDetails) {
                            pump?.dutyPercent?.let { stringResource(id = R.string.format_watts, it.roundToInt()) }
                                ?: stringResource(id = R.string.pump_default_value)
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        }
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_valid_frequency),
                        value = if (showPumpDetails) {
                            when (pump?.validFrequency) {
                                true -> stringResource(id = R.string.pump_ok)
                                false -> stringResource(id = R.string.pump_not_ok)
                                null -> stringResource(id = R.string.pump_default_value)
                            }
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        }
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_interpreted_state),
                        value = if (showPumpDetails) {
                            interpretedState.toLocalizedLabel()
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        },
                        valueColor = if (showPumpDetails && interpretedState.isAlarm) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                    DetailRow(
                        label = stringResource(id = R.string.pump_timestamp),
                        value = if (showPumpDetails) {
                            pump?.timestamp?.toString() ?: stringResource(id = R.string.pump_default_value)
                        } else {
                            stringResource(id = R.string.pump_default_value)
                        }
                    )
                }
            }
            if (showPumpDetails && interpretedState.isAlarm) {
                Text(
                    text = stringResource(id = R.string.pump_alarm_hint),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
@Composable
private fun PumpInterpretedState.toLocalizedLabel(): String {
    return when (this) {
        PumpInterpretedState.STANDBY -> stringResource(id = R.string.pump_state_standby)
        PumpInterpretedState.READY_STANDBY -> stringResource(id = R.string.pump_state_ready_standby)
        PumpInterpretedState.LOW_OPERATION -> stringResource(id = R.string.pump_state_low_operation)
        PumpInterpretedState.NORMAL_OPERATION -> stringResource(id = R.string.pump_state_normal_operation)
        PumpInterpretedState.LOW_VOLTAGE_WARNING -> stringResource(id = R.string.pump_state_low_voltage_warning)
        PumpInterpretedState.ROTOR_BLOCKED -> stringResource(id = R.string.pump_state_rotor_blocked)
        PumpInterpretedState.ELECTRICAL_FAULT -> stringResource(id = R.string.pump_state_electrical_fault)
        PumpInterpretedState.UNKNOWN -> stringResource(id = R.string.pump_state_unknown)
    }
}
