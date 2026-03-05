package com.whispertflite.engine

import android.content.Context
import android.util.Log

class WhisperEngineNative(private val context: Context) : WhisperEngine {

    private val TAG = "WhisperEngineNative"
    private val nativePtr: Long // Native pointer to the TFLiteEngine instance
    private var isEngineInitialized = false

    init {
        nativePtr = createTFLiteEngine()
    }

    override fun isInitialized(): Boolean {
        return isEngineInitialized
    }

    override fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean {
        loadModel(modelPath, multilingual)
        Log.d(TAG, "Model is loaded...$modelPath")
        isEngineInitialized = true
        return true
    }

    override fun deinitialize() {
        freeModel()
        isEngineInitialized = false
    }

    override fun transcribeBuffer(samples: FloatArray): String {
        return transcribeBuffer(nativePtr, samples)
    }

    override fun transcribeFile(waveFile: String): String {
        return transcribeFile(nativePtr, waveFile)
    }

    private fun loadModel(modelPath: String, isMultilingual: Boolean): Int {
        return loadModel(nativePtr, modelPath, isMultilingual)
    }

    private fun freeModel() {
        freeModel(nativePtr)
    }

    companion object {
        init {
            System.loadLibrary("audioEngine")
        }
    }

    // Native methods
    private external fun createTFLiteEngine(): Long
    private external fun loadModel(nativePtr: Long, modelPath: String, isMultilingual: Boolean): Int
    private external fun freeModel(nativePtr: Long)
    private external fun transcribeBuffer(nativePtr: Long, samples: FloatArray): String
    private external fun transcribeFile(nativePtr: Long, waveFile: String): String
}
