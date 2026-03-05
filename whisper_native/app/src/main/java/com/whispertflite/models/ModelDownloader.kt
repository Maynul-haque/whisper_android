package com.whispertflite.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object ModelDownloader {

    data class WhisperModel(
        val name: String,
        val filename: String,
        val url: String,
        val sizeMB: Int,
        val isMultilingual: Boolean
    )

    // Example remote URLs (Ideally hosted somewhere like Hugging Face or an S3 bucket)
    // For this example, we mock the URLs. Users can host their models from "models_and_scripts"
    // and provide actual URLs here.
    val availableModels = listOf(
        WhisperModel("Tiny (English)", "whisper-tiny.en.tflite", "https://example.com/whisper-tiny.en.tflite", 39, false),
        WhisperModel("Base (Multi)", "whisper-base.tflite", "https://example.com/whisper-base.tflite", 74, true),
        WhisperModel("Small (Multi)", "whisper-small.tflite", "https://example.com/whisper-small.tflite", 244, true)
    )

    private val _downloadStatus = MutableStateFlow<String>("")
    val downloadStatus: StateFlow<String> = _downloadStatus

    suspend fun downloadModel(context: Context, model: WhisperModel, destFolder: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val outFile = File(destFolder, model.filename)
                if (outFile.exists()) {
                    _downloadStatus.value = "Model ${model.name} already exists."
                    return@withContext true
                }

                _downloadStatus.value = "Starting download for ${model.name}..."

                val url = URL(model.url)
                val connection = url.openConnection()
                connection.connect()

                val fileLength = connection.contentLength
                val inputStream = connection.getInputStream()
                val outputStream = FileOutputStream(outFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (inputStream.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        _downloadStatus.value = "Downloading ${model.name}: $progress%"
                    }
                    outputStream.write(data, 0, count)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _downloadStatus.value = "Download complete: ${model.name}"
                true
            } catch (e: Exception) {
                _downloadStatus.value = "Download failed: ${e.message}"
                false
            }
        }
    }
}
