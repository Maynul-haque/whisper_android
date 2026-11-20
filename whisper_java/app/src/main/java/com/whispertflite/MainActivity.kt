package com.whispertflite

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.whispertflite.asr.Player
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
import com.whispertflite.ui.MainRoute
import com.whispertflite.ui.MainViewModel
import com.whispertflite.utils.WaveUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        private const val DEFAULT_MODEL_TO_USE = "whisper-tiny.tflite"
        private const val ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite"
        private const val ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin"
        private const val MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin"
        private val EXTENSIONS_TO_COPY = arrayOf("tflite", "bin", "wav", "pcm")
    }

    private lateinit var mPlayer: Player
    private lateinit var mRecorder: Recorder
    private var mWhisper: Whisper? = null

    private var sdcardDataFolder: File? = null
    private var selectedWaveFile: File? = null
    private var selectedTfliteFile: File? = null

    private lateinit var viewModel: MainViewModel
    private var startTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        sdcardDataFolder = getExternalFilesDir(null) ?: filesDir
        val dataFolder = sdcardDataFolder!!
        copyAssetsToSdcard(this, dataFolder, EXTENSIONS_TO_COPY)

        val tfliteFiles = getFilesWithExtension(dataFolder, ".tflite")
        val waveFiles = getFilesWithExtension(dataFolder, ".wav")

        selectedTfliteFile = File(dataFolder, DEFAULT_MODEL_TO_USE)
        selectedWaveFile = waveFiles.firstOrNull()

        viewModel.setFiles(
            tfliteFiles.map { it.name },
            waveFiles.map { it.name },
            DEFAULT_MODEL_TO_USE,
        )

        setContent {
            MainRoute(
                viewModel = viewModel,
                onModelSelected = { fileName -> onModelSelected(fileName) },
                onWaveSelected = { fileName -> onWaveSelected(fileName) },
                onRecordClick = { onRecordPressed() },
                onPlayClick = { onPlayPressed() },
                onTranscribeClick = { onTranscribePressed() },
                onCopyClick = { copyTranscript() },
            )
        }

        mRecorder = Recorder(this)
        mRecorder.setListener(object : Recorder.RecorderListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Recorder update: $message")
                handler.post {
                    viewModel.setStatus(message)
                    if (message == Recorder.MSG_RECORDING) {
                        viewModel.clearTranscript()
                        viewModel.setRecording(true)
                    } else if (message == Recorder.MSG_RECORDING_DONE) {
                        viewModel.setRecording(false)
                    }
                }
            }

            override fun onDataReceived(samples: FloatArray) {
                // mWhisper.writeBuffer(samples)
            }
        })

        mPlayer = Player(this)
        mPlayer.setListener(object : Player.PlaybackListener {
            override fun onPlaybackStarted() {
                handler.post { viewModel.setPlaying(true) }
            }

            override fun onPlaybackStopped() {
                handler.post { viewModel.setPlaying(false) }
            }
        })

        checkRecordPermission()
    }

    private fun onModelSelected(modelName: String) {
        deinitModel()
        selectedTfliteFile = File(sdcardDataFolder!!, modelName)
        viewModel.onModelSelected(modelName)
    }

    private fun onWaveSelected(waveName: String) {
        selectedWaveFile = File(sdcardDataFolder!!, waveName)
        viewModel.onWaveSelected(waveName)
    }

    private fun onRecordPressed() {
        if (mRecorder.isInProgress()) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun onPlayPressed() {
        val waveFile = selectedWaveFile
        if (waveFile == null) {
            viewModel.setStatus("No wave file available")
            return
        }

        if (!mPlayer.isPlaying()) {
            mPlayer.initializePlayer(waveFile.absolutePath)
            mPlayer.startPlayback()
        } else {
            mPlayer.stopPlayback()
        }
    }

    private fun onTranscribePressed() {
        if (selectedWaveFile == null || selectedTfliteFile == null) {
            viewModel.setStatus("Please select model and input file")
            return
        }

        if (mRecorder.isInProgress()) {
            stopRecording()
        }

        if (mWhisper == null) {
            initModel(selectedTfliteFile!!)
        }

        val whisper = mWhisper
        if (whisper != null && !whisper.isInProgress()) {
            startTranscription(selectedWaveFile!!.absolutePath)
            viewModel.setTranscribing(true)
        } else {
            stopTranscription()
            viewModel.setTranscribing(false)
        }
    }

    private fun initModel(modelFile: File) {
        val isMultilingualModel = !modelFile.name.endsWith(ENGLISH_ONLY_MODEL_EXTENSION)
        val vocabFileName = if (isMultilingualModel) MULTILINGUAL_VOCAB_FILE else ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder!!, vocabFileName)

        mWhisper = Whisper(this)
        mWhisper?.loadModel(modelFile, vocabFile, isMultilingualModel)
        mWhisper?.setListener(object : Whisper.WhisperListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Whisper update: $message")

                if (message == Whisper.MSG_PROCESSING) {
                    startTime = System.currentTimeMillis()
                    handler.post {
                        viewModel.setStatus(message)
                        viewModel.clearTranscript()
                        viewModel.setTranscribing(true)
                    }
                } else if (message == Whisper.MSG_PROCESSING_DONE) {
                    handler.post { viewModel.setTranscribing(false) }
                } else if (message == Whisper.MSG_FILE_NOT_FOUND) {
                    handler.post {
                        viewModel.setStatus(message)
                        viewModel.setTranscribing(false)
                    }
                }
            }

            override fun onResultReceived(result: String) {
                val timeTaken = System.currentTimeMillis() - startTime
                handler.post {
                    viewModel.setStatus("Processing done in " + timeTaken + "ms")
                    viewModel.appendTranscript(result)
                }
            }
        })
    }

    private fun deinitModel() {
        mWhisper?.unloadModel()
        mWhisper = null
    }

    private fun checkRecordPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            viewModel.setStatus(getString(R.string.need_record_audio_permission))
        }
    }

    private fun startRecording() {
        checkRecordPermission()

        val waveFile = File(sdcardDataFolder!!, WaveUtil.RECORDING_FILE)
        mRecorder.setFilePath(waveFile.absolutePath)
        mRecorder.start()

        selectedWaveFile = waveFile
        viewModel.onWaveSelected(waveFile.name)
    }

    private fun stopRecording() {
        mRecorder.stop()
    }

    private fun startTranscription(waveFilePath: String) {
        mWhisper?.let {
            it.setFilePath(waveFilePath)
            it.setAction(Whisper.ACTION_TRANSCRIBE)
            it.start()
        }
    }

    private fun stopTranscription() {
        mWhisper?.stop()
    }

    private fun copyTranscript() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = viewModel.uiState.value.transcript
        val clip = ClipData.newPlainText("Transcript", textToCopy)
        clipboard.setPrimaryClip(clip)
        viewModel.setStatus("Transcript copied")
    }

    private fun copyAssetsToSdcard(context: Context, destFolder: File, extensions: Array<String>) {
        val assetManager: AssetManager = context.assets

        try {
            val assetFiles = assetManager.list("") ?: return

            for (assetFileName in assetFiles) {
                for (extension in extensions) {
                    if (assetFileName.endsWith(".$extension")) {
                        val outFile = File(destFolder, assetFileName)

                        if (outFile.exists()) break

                        assetManager.open(assetFileName).use { inputStream ->
                            FileOutputStream(outFile).use { outputStream ->
                                val buffer = ByteArray(1024)
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                        break
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy assets", e)
        }
    }

    private fun getFilesWithExtension(directory: File?, extension: String): ArrayList<File> {
        val filteredFiles = ArrayList<File>()

        if (directory != null && directory.exists()) {
            val files = directory.listFiles()

            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.name.endsWith(extension)) {
                        filteredFiles.add(file)
                    }
                }
            }
        }

        return filteredFiles
    }
}
