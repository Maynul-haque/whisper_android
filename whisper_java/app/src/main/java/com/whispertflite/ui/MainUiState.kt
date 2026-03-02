package com.whispertflite.ui

data class MainUiState(
    val modelFiles: List<String> = emptyList(),
    val waveFiles: List<String> = emptyList(),
    val selectedModel: String? = null,
    val selectedWave: String? = null,
    val status: String = "",
    val transcript: String = "",
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val isTranscribing: Boolean = false,
)
