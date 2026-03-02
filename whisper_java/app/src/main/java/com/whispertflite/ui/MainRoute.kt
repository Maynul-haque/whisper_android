package com.whispertflite.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun MainRoute(
    viewModel: MainViewModel,
    onModelSelected: (String) -> Unit,
    onWaveSelected: (String) -> Unit,
    onRecordClick: () -> Unit,
    onPlayClick: () -> Unit,
    onTranscribeClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    MainScreen(
        state = state,
        onModelSelected = onModelSelected,
        onWaveSelected = onWaveSelected,
        onRecordClick = onRecordClick,
        onPlayClick = onPlayClick,
        onTranscribeClick = onTranscribeClick,
        onCopyClick = onCopyClick,
    )
}
