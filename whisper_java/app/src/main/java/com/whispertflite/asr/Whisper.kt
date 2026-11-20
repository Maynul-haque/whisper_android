package com.whispertflite.asr

import android.content.Context
import android.util.Log
import com.whispertflite.engine.WhisperEngine
import com.whispertflite.engine.WhisperEngineJava
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class Whisper(context: Context) {

    interface WhisperListener {
        fun onUpdateReceived(message: String)
        fun onResultReceived(result: String)
    }

    enum class Action { TRANSLATE, TRANSCRIBE }

    companion object {
        private const val TAG = "Whisper"
        const val MSG_PROCESSING = "Processing..."
        const val MSG_PROCESSING_DONE = "Processing done...!"
        const val MSG_FILE_NOT_FOUND = "Input file doesn't exist..!"

        val ACTION_TRANSCRIBE = Action.TRANSCRIBE
        val ACTION_TRANSLATE = Action.TRANSLATE
    }

    private val inProgress = AtomicBoolean(false)
    private val audioBufferQueue: Queue<FloatArray> = LinkedList()

    private val whisperEngine: WhisperEngine = WhisperEngineJava(context)

    private var action: Action? = null
    private var wavFilePath: String? = null
    private var updateListener: WhisperListener? = null

    private val taskLock = ReentrantLock()
    private val hasTask = taskLock.newCondition()

    @Volatile
    private var taskAvailable = false

    init {
        // Start thread for file transcription
        thread(name = "Whisper-FileTranscribe") { transcribeFileLoop() }

        // Start thread for buffer transcription (live mic feed)
        thread(name = "Whisper-BufferTranscribe") { transcribeBufferLoop() }
    }

    fun setListener(listener: WhisperListener?) {
        this.updateListener = listener
    }

    fun loadModel(modelPath: File, vocabPath: File, isMultilingual: Boolean) {
        loadModel(modelPath.absolutePath, vocabPath.absolutePath, isMultilingual)
    }

    fun loadModel(modelPath: String, vocabPath: String, isMultilingual: Boolean) {
        try {
            whisperEngine.initialize(modelPath, vocabPath, isMultilingual)
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing model...", e)
            sendUpdate("Model initialization failed")
        }
    }

    fun unloadModel() {
        whisperEngine.deinitialize()
    }

    fun setAction(action: Action) {
        this.action = action
    }

    fun setFilePath(wavFile: String) {
        this.wavFilePath = wavFile
    }

    fun start() {
        if (!inProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Execution is already in progress...")
            return
        }
        taskLock.withLock {
            taskAvailable = true
            hasTask.signal()
        }
    }

    fun stop() {
        inProgress.set(false)
    }

    fun isInProgress(): Boolean = inProgress.get()

    private fun transcribeFileLoop() {
        while (!Thread.currentThread().isInterrupted) {
            taskLock.withLock {
                try {
                    while (!taskAvailable) {
                        hasTask.await()
                    }
                    transcribeFile()
                    taskAvailable = false
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun transcribeFile() {
        try {
            val path = wavFilePath
            if (whisperEngine.isInitialized && path != null) {
                val waveFile = File(path)
                if (waveFile.exists()) {
                    val startTime = System.currentTimeMillis()
                    sendUpdate(MSG_PROCESSING)

                    val result: String? = synchronized(whisperEngine) {
                        if (action == Action.TRANSCRIBE) {
                            whisperEngine.transcribeFile(path)
                        } else {
                            Log.d(TAG, "TRANSLATE feature is not implemented")
                            null
                        }
                    }
                    sendResult(result)

                    val timeTaken = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Time Taken for transcription: " + timeTaken + "ms")
                    sendUpdate(MSG_PROCESSING_DONE)
                } else {
                    sendUpdate(MSG_FILE_NOT_FOUND)
                }
            } else {
                sendUpdate("Engine not initialized or file path not set")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            sendUpdate("Transcription failed: " + e.message)
        } finally {
            inProgress.set(false)
        }
    }

    private fun sendUpdate(message: String) {
        updateListener?.onUpdateReceived(message)
    }

    private fun sendResult(result: String?) {
        result?.let { updateListener?.onResultReceived(it) }
    }

    /////////////////////// Live MIC feed transcription calls /////////////////////////////////

    private fun transcribeBufferLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val samples = readBuffer() ?: continue
            synchronized(whisperEngine) {
                val result = whisperEngine.transcribeBuffer(samples)
                sendResult(result)
            }
        }
    }

    fun writeBuffer(samples: FloatArray) {
        synchronized(audioBufferQueue) {
            audioBufferQueue.add(samples)
            @Suppress("PLATFORM_CLASS_WAIT_FOR_NOT")
            (audioBufferQueue as Object).notify()
        }
    }

    private fun readBuffer(): FloatArray? {
        synchronized(audioBufferQueue) {
            while (audioBufferQueue.isEmpty()) {
                try {
                    @Suppress("PLATFORM_CLASS_WAIT_FOR_NOT")
                    (audioBufferQueue as Object).wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
            return audioBufferQueue.poll()
        }
    }
}
