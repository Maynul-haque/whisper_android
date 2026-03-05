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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class Recorder(private val context: Context) {

    private val TAG = "Recorder"
    val ACTION_STOP = "Stop"
    val ACTION_RECORD = "Record"
    val MSG_RECORDING = "Recording..."
    val MSG_RECORDING_DONE = "Recording done...!"
    val RECORDING_DURATION = 60 // 60 seconds

    private val mInProgress = AtomicBoolean(false)
    private var mWavFilePath: String? = null

    private val _updates = MutableSharedFlow<String>()
    val updates: SharedFlow<String> = _updates

    private val _data = MutableSharedFlow<FloatArray>(extraBufferCapacity = 64)
    val data: SharedFlow<FloatArray> = _data

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null

    fun setFilePath(wavFile: String) {
        this.mWavFilePath = wavFile
    }

    fun start() {
        if (!mInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Recording is already in progress...")
            return
        }

        recordingJob = coroutineScope.launch {
            recordAudio()
        }
    }

    fun stop() {
        mInProgress.set(false)
        recordingJob?.cancel()
    }

    fun isInProgress(): Boolean {
        return mInProgress.get()
    }

    private suspend fun recordAudio() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "AudioRecord permission is not granted")
            _updates.emit("Permission not granted for recording")
            return
        }

        _updates.emit(MSG_RECORDING)

        val channels = 1
        val bytesPerSample = 2
        val sampleRateInHz = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val audioSource = MediaRecorder.AudioSource.MIC

        val bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        val audioRecord = AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize)

        audioRecord.startRecording()

        val bytesForOneSecond = sampleRateInHz * bytesPerSample * channels
        val bytesForThreeSeconds = bytesForOneSecond * 3
        val bytesForSixtySeconds = bytesForOneSecond * RECORDING_DURATION

        val outputBuffer = ByteArrayOutputStream() // Buffer for saving data in wave file
        val realtimeBuffer = ByteArrayOutputStream() // Buffer for real-time processing

        val audioData = ByteArray(bufferSize)
        var totalBytesRead = 0

        try {
            while (mInProgress.get() && totalBytesRead < bytesForSixtySeconds && currentCoroutineContext().isActive) {
                val bytesRead = audioRecord.read(audioData, 0, bufferSize)
                if (bytesRead > 0) {
                    outputBuffer.write(audioData, 0, bytesRead)
                    realtimeBuffer.write(audioData, 0, bytesRead)
                    totalBytesRead += bytesRead

                    // Check if realtimeBuffer has more than 3 seconds of data
                    if (realtimeBuffer.size() >= bytesForThreeSeconds) {
                        val samples = convertToFloatArray(ByteBuffer.wrap(realtimeBuffer.toByteArray()))
                        realtimeBuffer.reset() // Clear the buffer
                        _data.emit(samples) // Send real-time data for processing
                    }
                } else {
                    Log.d(TAG, "AudioRecord error, bytes read: $bytesRead")
                    break
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()

            // Save recorded audio data to file
            mWavFilePath?.let {
                WaveUtil.createWaveFile(it, outputBuffer.toByteArray(), sampleRateInHz, channels, bytesPerSample)
            }

            _updates.emit(MSG_RECORDING_DONE)
            mInProgress.set(false)
        }
    }

    private fun convertToFloatArray(buffer: ByteBuffer): FloatArray {
        buffer.order(ByteOrder.nativeOrder())
        val samples = FloatArray(buffer.remaining() / 2)
        for (i in samples.indices) {
            samples[i] = buffer.short / 32768.0f
        }
        return samples
    }
}
