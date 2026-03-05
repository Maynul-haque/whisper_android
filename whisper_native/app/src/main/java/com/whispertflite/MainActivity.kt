package com.whispertflite

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.whispertflite.models.ModelDownloader
import com.whispertflite.utils.WaveUtil
import com.whispertflite.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private val DEFAULT_MODEL_TO_USE = "whisper-tiny.tflite"
    private val ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite"
    private val ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin"
    private val MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin"
    private val EXTENSIONS_TO_COPY = arrayOf("tflite", "bin", "wav", "pcm")

    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var fabCopy: FloatingActionButton
    private lateinit var fabExport: FloatingActionButton
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnTranscribe: Button
    private lateinit var btnDownloadModel: Button

    private var sdcardDataFolder: File? = null
    private var selectedWaveFile: File? = null
    private var selectedTfliteFile: File? = null

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        sdcardDataFolder = this.getExternalFilesDir(null)
        copyAssetsToSdcard(this, sdcardDataFolder, EXTENSIONS_TO_COPY)

        val tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite")
        val waveFiles = getFilesWithExtension(sdcardDataFolder, ".wav")

        selectedTfliteFile = File(sdcardDataFolder, DEFAULT_MODEL_TO_USE)

        val spinnerTflite: Spinner = findViewById(R.id.spnrTfliteFiles)
        spinnerTflite.adapter = getFileArrayAdapter(tfliteFiles)
        btnDownloadModel = findViewById(R.id.btnDownloadModel)
        btnDownloadModel.setOnClickListener {
            showDownloadDialog()
        }
        spinnerTflite.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                viewModel.unloadModel()
                selectedTfliteFile = parent.getItemAtPosition(position) as File
                initModel(selectedTfliteFile!!)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val spinnerWave: Spinner = findViewById(R.id.spnrWaveFiles)
        spinnerWave.adapter = getFileArrayAdapter(waveFiles)
        spinnerWave.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedWaveFile = parent.getItemAtPosition(position) as File
                if (selectedWaveFile?.name == WaveUtil.RECORDING_FILE) {
                    btnRecord.visibility = View.VISIBLE
                } else {
                    btnRecord.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener {
            selectedWaveFile?.let {
                val isCurrentlyRecording = viewModel.isRecordingInProgress()
                if (isCurrentlyRecording) {
                    Log.d(TAG, "Recording is in progress... stopping...")
                } else {
                    Log.d(TAG, "Start recording...")
                }
                viewModel.toggleRecording(it)
            }
        }

        btnPlay = findViewById(R.id.btnPlay)
        btnPlay.setOnClickListener {
            selectedWaveFile?.let {
                viewModel.togglePlayback(it.absolutePath)
            }
        }

        btnTranscribe = findViewById(R.id.btnTranscb)
        btnTranscribe.setOnClickListener {
            if (viewModel.isRecordingInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...")
                viewModel.stopRecording()
            }

            if (!viewModel.isTranscriptionInProgress()) {
                Log.d(TAG, "Start transcription...")
                selectedWaveFile?.let {
                    viewModel.startTranscription(it.absolutePath)
                }
            } else {
                Log.d(TAG, "Whisper is already in progress...!")
                viewModel.stopTranscription()
            }
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)
        fabCopy = findViewById(R.id.fabCopy)
        fabCopy.setOnClickListener {
            val textToCopy = tvResult.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", textToCopy)
            clipboard.setPrimaryClip(clip)
        }

        fabExport = findViewById(R.id.fabExport)
        fabExport.setOnClickListener {
            val textToExport = tvResult.text.toString()
            if (textToExport.isNotBlank()) {
                exportTextToFile(textToExport)
            }
        }

        checkRecordPermission()
        observeViewModel()
        initModel(selectedTfliteFile!!) // initial load
    }

    private fun exportTextToFile(text: String) {
        try {
            val exportDir = File(cacheDir, "Transcripts")
            if (!exportDir.exists()) exportDir.mkdirs()

            val fileName = "transcript_${System.currentTimeMillis()}.txt"
            val file = File(exportDir, fileName)

            FileOutputStream(file).use {
                it.write(text.toByteArray())
            }

            Log.d(TAG, "Exported transcript to: ${file.absolutePath}")

            // Trigger Share Intent
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Transcript"))

            viewModel.updateStatus("Exported to ${file.name}")
        } catch (e: Exception) {
            e.printStackTrace()
            viewModel.updateStatus("Failed to export: ${e.message}")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { status ->
                tvStatus.text = status
            }
        }

        lifecycleScope.launch {
            viewModel.transcriptionResult.collectLatest { result ->
                tvResult.text = result
            }
        }

        lifecycleScope.launch {
            viewModel.isRecording.collectLatest { isRecording ->
                if (isRecording) {
                    btnRecord.setText(R.string.stop)
                } else {
                    btnRecord.setText(R.string.record)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isPlaying.collectLatest { isPlaying ->
                if (isPlaying) {
                    btnPlay.setText(R.string.stop)
                } else {
                    btnPlay.setText(R.string.play)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.downloadStatus.collectLatest { status ->
                if (status.isNotEmpty()) {
                    tvStatus.text = status
                }
            }
        }
    }

    private fun showDownloadDialog() {
        val models = ModelDownloader.availableModels
        val modelNames = models.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Download Additional Whisper Models")
            .setItems(modelNames) { _, which ->
                val selectedModel = models[which]
                sdcardDataFolder?.let { folder ->
                    viewModel.downloadModel(selectedModel, folder)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initModel(modelFile: File) {
        val isMultilingualModel = !modelFile.name.endsWith(ENGLISH_ONLY_MODEL_EXTENSION)
        val vocabFileName = if (isMultilingualModel) MULTILINGUAL_VOCAB_FILE else ENGLISH_ONLY_VOCAB_FILE
        val vocabFile = File(sdcardDataFolder, vocabFileName)
        viewModel.loadModel(modelFile, vocabFile, isMultilingualModel)
    }

    private fun getFileArrayAdapter(waveFiles: ArrayList<File>): ArrayAdapter<File> {
        val adapter = object : ArrayAdapter<File>(this, android.R.layout.simple_spinner_item, waveFiles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = getItem(position)?.name
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = getItem(position)?.name
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun checkRecordPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted")
        } else {
            Log.d(TAG, "Requesting record permission")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted")
        } else {
            Log.d(TAG, "Record permission is not granted")
        }
    }

    private fun copyAssetsToSdcard(context: Context, destFolder: File?, extensions: Array<String>) {
        val assetManager = context.assets
        if (destFolder == null) return

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
            e.printStackTrace()
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
