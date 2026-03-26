package com.example.grundfos_summer_app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.grundfos_summer_app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
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
        topBar = {
            TopAppBar(
                title = { Text("Chybová hlášení") },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                val errors = uiState.status?.errors
                
                item {
                    ErrorItem(
                        title = "WiFi Připojení",
                        description = "Chyba připojení k lokální síti nebo internetu.",
                        isError = errors?.wifi == true
                    )
                }

                item {
                    ErrorItem(
                        title = "Synchronizace času",
                        description = "Nepodařilo se získat aktuální čas z NTP serveru.",
                        isError = errors?.time == true
                    )
                }

                item {
                    ErrorItem(
                        title = "Zpětná vazba čerpadla",
                        description = "Čerpadlo neběží nebo nedává pulzy zpětné vazby.",
                        isError = errors?.pump == true
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.resetErrors() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD84315) // Varovná oranžová z MainScreen
                        )
                    ) {
                        Text("Resetovat chyby", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (uiState.isLoading && uiState.status == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun ErrorItem(
    title: String,
    description: String,
    isError: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) Color(0xFFFFF1F0) else Color(0xFFF1F8E9)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) Color(0xFFD32F2F) else Color(0xFF388E3C),
                modifier = Modifier.size(32.dp)
            )
            
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isError) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
            
            if (isError) {
                Text(
                    text = "CHYBA",
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "OK",
                    color = Color(0xFF388E3C),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}
