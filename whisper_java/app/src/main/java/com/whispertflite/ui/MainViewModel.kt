package com.whispertflite.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setFiles(modelFiles: List<String>, waveFiles: List<String>, defaultModel: String?) {
        _uiState.update {
            val selectedModel = defaultModel?.takeIf(modelFiles::contains) ?: modelFiles.firstOrNull()
            val selectedWave = waveFiles.firstOrNull()
            it.copy(
                modelFiles = modelFiles,
                waveFiles = waveFiles,
                selectedModel = selectedModel,
                selectedWave = selectedWave,
            )
        }
    }

    fun onModelSelected(fileName: String) {
        _uiState.update { it.copy(selectedModel = fileName) }
    }

    fun onWaveSelected(fileName: String) {
        _uiState.update { it.copy(selectedWave = fileName) }
    }

    fun setStatus(status: String) {
        _uiState.update { it.copy(status = status) }
    }

    fun clearTranscript() {
        _uiState.update { it.copy(transcript = "") }
    }

    fun appendTranscript(text: String) {
        _uiState.update { it.copy(transcript = it.transcript + text) }
    }

    fun setRecording(recording: Boolean) {
        _uiState.update { it.copy(isRecording = recording) }
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun setTranscribing(transcribing: Boolean) {
        _uiState.update { it.copy(isTranscribing = transcribing) }
    }
}
