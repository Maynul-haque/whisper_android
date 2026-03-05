package com.whispertflite.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whispertflite.asr.Player
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
import com.whispertflite.models.ModelDownloader
import com.whispertflite.utils.WaveUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "MainViewModel"

    private val recorder = Recorder(application)
    private val whisper = Whisper(application)
    private val player = Player(application)

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _transcriptionResult = MutableStateFlow("")
    val transcriptionResult: StateFlow<String> = _transcriptionResult

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var startTime: Long = 0

    val downloadStatus: StateFlow<String> = ModelDownloader.downloadStatus

    init {
        // Collect updates from recorder
        viewModelScope.launch {
            recorder.updates.collect { message ->
                Log.d(TAG, "Recorder Update: $message")
                _statusMessage.value = message
                if (message == recorder.MSG_RECORDING) {
                    _transcriptionResult.value = ""
                    _isRecording.value = true
                } else if (message == recorder.MSG_RECORDING_DONE) {
                    _isRecording.value = false
                }
            }
        }

        // Collect audio data for real-time transcription
        viewModelScope.launch {
            recorder.data.collect { samples ->
                whisper.writeBuffer(samples)
            }
        }

        // Collect updates from whisper
        viewModelScope.launch {
            whisper.updates.collect { message ->
                Log.d(TAG, "Whisper Update: $message")
                if (message == whisper.MSG_PROCESSING) {
                    _statusMessage.value = message
                    _transcriptionResult.value = ""
                    startTime = System.currentTimeMillis()
                } else if (message == whisper.MSG_PROCESSING_DONE) {
                    val timeTaken = System.currentTimeMillis() - startTime
                    _statusMessage.value = "Processing done in ${timeTaken}ms"
                } else {
                    _statusMessage.value = message
                }
            }
        }

        // Collect results from whisper
        viewModelScope.launch {
            whisper.results.collect { result ->
                Log.d(TAG, "Whisper Result: $result")
                _transcriptionResult.value += result
            }
        }

        // Setup Player listener
        player.setListener(object : Player.PlaybackListener {
            override fun onPlaybackStarted() {
                _isPlaying.value = true
            }

            override fun onPlaybackStopped() {
                _isPlaying.value = false
            }
        })
    }

    fun loadModel(modelFile: File, vocabFile: File, isMultilingual: Boolean) {
        whisper.loadModel(modelFile, vocabFile, isMultilingual)
    }

    fun unloadModel() {
        whisper.unloadModel()
    }

    fun toggleRecording(waveFile: File) {
        if (recorder.isInProgress()) {
            recorder.stop()
        } else {
            recorder.setFilePath(waveFile.absolutePath)
            recorder.start()
        }
    }

    fun stopRecording() {
        if (recorder.isInProgress()) {
            recorder.stop()
        }
    }

    fun isRecordingInProgress(): Boolean {
        return recorder.isInProgress()
    }

    fun startTranscription(waveFilePath: String) {
        if (!whisper.isInProgress()) {
            whisper.setFilePath(waveFilePath)
            whisper.setAction(Whisper.Action.TRANSCRIBE)
            whisper.start()
        }
    }

    fun stopTranscription() {
        if (whisper.isInProgress()) {
            whisper.stop()
        }
    }

    fun isTranscriptionInProgress(): Boolean {
        return whisper.isInProgress()
    }

    fun updateStatus(message: String) {
        _statusMessage.value = message
    }

    fun togglePlayback(waveFilePath: String) {
        if (!player.isPlaying()) {
            player.initializePlayer(waveFilePath)
            player.startPlayback()
        } else {
            player.stopPlayback()
        }
    }

    fun downloadModel(model: ModelDownloader.WhisperModel, destFolder: File) {
        viewModelScope.launch {
            ModelDownloader.downloadModel(getApplication(), model, destFolder)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.stop()
        whisper.stop()
        whisper.unloadModel()
        player.stopPlayback()
    }
}
