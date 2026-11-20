package com.whispertflite.engine

import android.content.Context
import android.util.Log
import com.whispertflite.utils.WaveUtil
import com.whispertflite.utils.WhisperUtil
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class WhisperEngineJava(context: Context) : WhisperEngine {

    private val TAG = "WhisperEngineJava"
    private val whisperUtil = WhisperUtil()
    private val context = context

    @Volatile
    private var isInitializedFlag = false

    private var interpreter: Interpreter? = null

    override val isInitialized: Boolean
        get() = isInitializedFlag

    override fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean {
        // Load model
        loadModel(modelPath)
        Log.d(TAG, "Model is loaded...$modelPath")

        // Load filters and vocab
        val ret = whisperUtil.loadFiltersAndVocab(multilingual, vocabPath)
        if (ret) {
            isInitializedFlag = true
            Log.d(TAG, "Filters and Vocab are loaded...$vocabPath")
        } else {
            isInitializedFlag = false
            Log.d(TAG, "Failed to load Filters and Vocab...")
        }

        return isInitializedFlag
    }

    // Unload the model by closing the interpreter
    override fun deinitialize() {
        interpreter?.close()
        interpreter = null // Optional: Set to null to avoid accidental reuse
    }

    override fun transcribeFile(wavePath: String): String {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...")
        val melSpectrogram = getMelSpectrogram(wavePath)
        Log.d(TAG, "Mel spectrogram is calculated...!")

        // Perform inference
        val result = runInference(melSpectrogram)
        Log.d(TAG, "Inference is executed...!")

        return result
    }

    override fun transcribeBuffer(samples: FloatArray): String? = null

    // Load TFLite model
    @Throws(IOException::class)
    private fun loadModel(modelPath: String) {
        FileInputStream(modelPath).use { fileInputStream ->
            val fileChannel = fileInputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()
            val tfliteModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            // Set the number of threads for inference
            val options = Interpreter.Options()
            options.numThreads = Runtime.getRuntime().availableProcessors()

            interpreter = Interpreter(tfliteModel, options)
        }
    }

    private fun getMelSpectrogram(wavePath: String): FloatArray {
        // Get samples in PCM_FLOAT format
        val samples = WaveUtil.getSamples(wavePath)

        val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
        val inputSamples = FloatArray(fixedInputSize)
        val copyLength = minOf(samples.size, fixedInputSize)
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)

        val cores = Runtime.getRuntime().availableProcessors()
        return whisperUtil.getMelSpectrogram(inputSamples, inputSamples.size, cores)
    }

    private fun runInference(inputData: FloatArray): String {
        val engine = requireNotNull(interpreter) { "Interpreter not initialized" }

        // Create input tensor
        val inputTensor = engine.getInputTensor(0)
        val inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType())

        // Create output tensor
        val outputTensor = engine.getOutputTensor(0)
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32)

        // Load input data
        val inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * java.lang.Float.BYTES
        val inputBuf = ByteBuffer.allocateDirect(inputSize)
        inputBuf.order(ByteOrder.nativeOrder())
        for (input in inputData) {
            inputBuf.putFloat(input)
        }

        inputBuffer.loadBuffer(inputBuf)

        // Run inference
        engine.run(inputBuffer.buffer, outputBuffer.buffer)

        // Retrieve the results
        val outputLen = outputBuffer.intArray.size
        Log.d(TAG, "output_len: $outputLen")
        val result = StringBuilder()
        for (i in 0 until outputLen) {
            val token = outputBuffer.buffer.int
            if (token == whisperUtil.tokenEOT) break

            // Get word for token and Skip additional token
            if (token < whisperUtil.tokenEOT) {
                val word = whisperUtil.getWordFromToken(token)
                result.append(word)
            } else {
                if (token == whisperUtil.tokenTranscribe) Log.d(TAG, "It is Transcription...")
                if (token == whisperUtil.tokenTranslate) Log.d(TAG, "It is Translation...")

                val word = whisperUtil.getWordFromToken(token)
                Log.d(TAG, "Skipping token: $token, word: $word")
            }
        }

        return result.toString()
    }

    private fun printTensorDump(message: String, tensor: Tensor) {
        Log.d(TAG, "Output Tensor Dump ==>")
        Log.d(TAG, "  shape.length: " + tensor.shape().size)
        for (i in tensor.shape().indices) {
            Log.d(TAG, "    shape[$i]: " + tensor.shape()[i])
        }
        Log.d(TAG, "  dataType: " + tensor.dataType())
        Log.d(TAG, "  name: " + tensor.name())
        Log.d(TAG, "  numBytes: " + tensor.numBytes())
        Log.d(TAG, "  index: " + tensor.index())
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions())
        Log.d(TAG, "  numElements: " + tensor.numElements())
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().size)
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().scale)
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().zeroPoint)
        Log.d(TAG, "==================================================================")
    }
}
