package com.whispertflite.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.whispertflite.utils.WaveUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class Recorder(context: Context) {

    interface RecorderListener {
        fun onUpdateReceived(message: String)
        fun onDataReceived(samples: FloatArray)
    }

    companion object {
        private const val TAG = "Recorder"
        const val ACTION_STOP = "Stop"
        const val ACTION_RECORD = "Record"
        const val MSG_RECORDING = "Recording..."
        const val MSG_RECORDING_DONE = "Recording done...!"
    }

    private val context = context
    private val inProgress = AtomicBoolean(false)

    private var wavFilePath: String? = null
    private var listener: RecorderListener? = null

    private val lock = ReentrantLock()
    private val hasTask = lock.newCondition()
    private val fileSavedLock = Object() // Lock object for wait/notify

    @Volatile
    private var shouldStartRecording = false

    init {
        // Initialize and start the worker thread
        thread(name = "Recorder-Worker") { recordLoop() }
    }

    fun setListener(listener: RecorderListener?) {
        this.listener = listener
    }

    fun setFilePath(wavFile: String) {
        this.wavFilePath = wavFile
    }

    fun start() {
        if (!inProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...")
            return
        }
        lock.withLock {
            shouldStartRecording = true
            hasTask.signal()
        }
    }

    fun stop() {
        inProgress.set(false)

        // Wait for the recording thread to finish
        synchronized(fileSavedLock) {
            try {
                @Suppress("PLATFORM_CLASS_WAIT_FOR_NOT")
                (fileSavedLock as Object).wait() // Wait until notified by the recording thread
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt() // Restore interrupted status
            }
        }
    }

    fun isInProgress(): Boolean = inProgress.get()

    private fun sendUpdate(message: String) {
        listener?.onUpdateReceived(message)
    }

    private fun sendData(samples: FloatArray) {
        listener?.onDataReceived(samples)
    }

    private fun recordLoop() {
        while (true) {
            lock.withLock {
                try {
                    while (!shouldStartRecording) {
                        hasTask.await()
                    }
                    shouldStartRecording = false
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }

            // Start recording process
            try {
                recordAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Recording error...", e)
                sendUpdate(e.message ?: "Recording error")
            } finally {
                inProgress.set(false)
            }
        }
    }

    private fun recordAudio() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted")
            sendUpdate("Permission not granted for recording")
            return
        }

        sendUpdate(MSG_RECORDING)

        val channels = 1
        val bytesPerSample = 2
        val sampleRateInHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val audioSource = MediaRecorder.AudioSource.MIC

        val bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        val audioRecord = AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize)
        audioRecord.startRecording()

        // Calculate maximum byte counts for 30 seconds (for saving)
        val bytesForThirtySeconds = sampleRateInHz * bytesPerSample * channels * 30
        val bytesForThreeSeconds = sampleRateInHz * bytesPerSample * channels * 3

        val outputBuffer = ByteArrayOutputStream()  // Buffer for saving data in wave file
        val realtimeBuffer = ByteArrayOutputStream() // Buffer for real-time processing

        val audioData = ByteArray(bufferSize)
        var totalBytesRead = 0

        while (inProgress.get() && totalBytesRead < bytesForThirtySeconds) {
            val bytesRead = audioRecord.read(audioData, 0, bufferSize)
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead)  // Save all bytes read up to 30 seconds
                realtimeBuffer.write(audioData, 0, bytesRead) // Accumulate real-time audio data
                totalBytesRead += bytesRead

                // Check if realtimeBuffer has more than 3 seconds of data
                if (realtimeBuffer.size() >= bytesForThreeSeconds) {
                    val samples = convertToFloatArray(java.nio.ByteBuffer.wrap(realtimeBuffer.toByteArray()))
                    realtimeBuffer.reset() // Clear the buffer for the next accumulation
                    sendData(samples) // Send real-time data for processing
                }
            } else {
                Log.d(TAG, "AudioRecord error, bytes read: $bytesRead")
                break
            }
        }

        audioRecord.stop()
        audioRecord.release()

        // Save recorded audio data to file (up to 30 seconds)
        wavFilePath?.let {
            WaveUtil.createWaveFile(it, outputBuffer.toByteArray(), sampleRateInHz, channels, bytesPerSample)
        }
        sendUpdate(MSG_RECORDING_DONE)

        // Notify the waiting thread that recording is complete
        synchronized(fileSavedLock) {
            @Suppress("PLATFORM_CLASS_WAIT_FOR_NOT")
            (fileSavedLock as Object).notify() // Notify that recording is finished
        }
    }

    private fun convertToFloatArray(buffer: java.nio.ByteBuffer): FloatArray {
        buffer.order(java.nio.ByteOrder.nativeOrder())
        val samples = FloatArray(buffer.remaining() / 2)
        for (i in samples.indices) {
            samples[i] = buffer.short / 32768.0f
        }
        return samples
    }
}
