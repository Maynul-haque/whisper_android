package com.whispertflite.asr

import android.content.Context
import android.util.Log
import com.whispertflite.engine.WhisperEngineNative
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class Whisper(private val context: Context) {

    private val TAG = "Whisper"
    val MSG_PROCESSING = "Processing..."
    val MSG_PROCESSING_DONE = "Processing done...!"
    val MSG_FILE_NOT_FOUND = "Input file doesn't exist..!"

    enum class Action {
        TRANSLATE, TRANSCRIBE
    }

    private val mInProgress = AtomicBoolean(false)
    private val audioBufferChannel = Channel<FloatArray>(Channel.UNLIMITED)
    private val whisperEngine = WhisperEngineNative(context)
    private var mAction: Action? = null
    private var mWavFilePath: String? = null

    private val _updates = MutableSharedFlow<String>()
    val updates: SharedFlow<String> = _updates

    private val _results = MutableSharedFlow<String>()
    val results: SharedFlow<String> = _results

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var transcriptionJob: Job? = null
    private var bufferTranscriptionJob: Job? = null

    private val engineMutex = Mutex()

    init {
        startBufferTranscriptionLoop()
    }

    fun loadModel(modelPath: File, vocabPath: File, isMultilingual: Boolean) {
        loadModel(modelPath.absolutePath, vocabPath.absolutePath, isMultilingual)
    }

    fun loadModel(modelPath: String, vocabPath: String, isMultilingual: Boolean) {
        coroutineScope.launch {
            try {
                whisperEngine.initialize(modelPath, vocabPath, isMultilingual)
            } catch (e: IOException) {
                Log.e(TAG, "Error initializing model...", e)
                _updates.emit("Model initialization failed")
            }
        }
    }

    fun unloadModel() {
        whisperEngine.deinitialize()
    }

    fun setAction(action: Action) {
        this.mAction = action
    }

    fun setFilePath(wavFile: String) {
        this.mWavFilePath = wavFile
    }

    fun start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution is already in progress...")
            return
        }

        transcriptionJob = coroutineScope.launch {
            transcribeFile()
        }
    }

    fun stop() {
        mInProgress.set(false)
        transcriptionJob?.cancel()
    }

    fun isInProgress(): Boolean {
        return mInProgress.get()
    }

    private suspend fun transcribeFile() {
        try {
            if (whisperEngine.isInitialized() && mWavFilePath != null) {
                val waveFile = File(mWavFilePath!!)
                if (waveFile.exists()) {
                    val startTime = System.currentTimeMillis()
                    _updates.emit(MSG_PROCESSING)

                    var result: String? = null
                    engineMutex.withLock {
                        if (mAction == Action.TRANSCRIBE) {
                            result = whisperEngine.transcribeFile(mWavFilePath!!)
                        } else {
                            Log.d(TAG, "TRANSLATE feature is not implemented")
                        }
                    }

                    if (result != null) {
                        _results.emit(result!!)
                    }

                    val timeTaken = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Time Taken for transcription: ${timeTaken}ms")
                    _updates.emit(MSG_PROCESSING_DONE)
                } else {
                    _updates.emit(MSG_FILE_NOT_FOUND)
                }
            } else {
                _updates.emit("Engine not initialized or file path not set")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            _updates.emit("Transcription failed: ${e.message}")
        } finally {
            mInProgress.set(false)
        }
    }

    private fun startBufferTranscriptionLoop() {
        bufferTranscriptionJob = coroutineScope.launch {
            for (samples in audioBufferChannel) {
                engineMutex.withLock {
                    if (whisperEngine.isInitialized()) {
                        val result = whisperEngine.transcribeBuffer(samples)
                        _results.emit(result)
                    }
                }
            }
        }
    }

    fun writeBuffer(samples: FloatArray) {
        audioBufferChannel.trySend(samples)
    }
}
