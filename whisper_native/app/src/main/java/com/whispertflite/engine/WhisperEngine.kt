package com.whispertflite.engine

interface WhisperEngine {
    fun isInitialized(): Boolean
    fun initialize(modelPath: String, vocabPath: String, multilingual: Boolean): Boolean
    fun deinitialize()
    fun transcribeBuffer(samples: FloatArray): String
    fun transcribeFile(waveFile: String): String
}
