package com.whispertflite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onModelSelected: (String) -> Unit,
    onWaveSelected: (String) -> Unit,
    onRecordClick: () -> Unit,
    onPlayClick: () -> Unit,
    onTranscribeClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    var modelExpanded by remember { mutableStateOf(false) }
    var waveExpanded by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Text(text = "Whisper ASR", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = !modelExpanded },
            ) {
                OutlinedTextField(
                    value = state.selectedModel.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                    state.modelFiles.forEach { fileName ->
                        DropdownMenuItem(
                            text = { Text(fileName) },
                            onClick = {
                                onModelSelected(fileName)
                                modelExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = waveExpanded,
                onExpandedChange = { waveExpanded = !waveExpanded },
            ) {
                OutlinedTextField(
                    value = state.selectedWave.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Input audio") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = waveExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = waveExpanded, onDismissRequest = { waveExpanded = false }) {
                    state.waveFiles.forEach { fileName ->
                        DropdownMenuItem(
                            text = { Text(fileName) },
                            onClick = {
                                onWaveSelected(fileName)
                                waveExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRecordClick, modifier = Modifier.weight(1f)) {
                    Text(if (state.isRecording) "Stop" else "Record")
                }
                Button(onClick = onPlayClick, modifier = Modifier.weight(1f)) {
                    Text(if (state.isPlaying) "Stop" else "Play")
                }
                Button(onClick = onTranscribeClick, modifier = Modifier.weight(1f)) {
                    Text(if (state.isTranscribing) "Stop" else "Transcribe")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Status: ${state.status}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = state.transcript, style = MaterialTheme.typography.bodyLarge)
            }

            Button(onClick = onCopyClick, modifier = Modifier.fillMaxWidth()) {
                Text("Copy transcript")
            }
        }
    }
}
