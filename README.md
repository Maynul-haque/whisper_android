# Whisper ASR for Android (Kotlin)

Fully offline speech-to-text on Android, powered by OpenAI Whisper models running on TensorFlow Lite. Record from the microphone or transcribe a WAV file, no internet required.

![App screenshot](demo_and_apk/Screenshot.jpg)

## Features

- **Fully offline** — model and vocabulary are bundled in the app; nothing leaves the device
- **Live transcription** — real-time streaming from the microphone
- **File transcription** — WAV input, 16 kHz / mono / 16-bit
- **Two bundled models** — `whisper-tiny` (multilingual) and `whisper-tiny.en` (English), selectable in-app
- **Kotlin-first core** — the whole inference/recording core is written in Kotlin, with a Compose UI

## Download

Pre-built APK (debug-signed, bundles both tiny models, ~112 MB):
[WhisperASR-kotlin.apk — release v1.0-kotlin](https://github.com/Maynul-haque/whisper_android/releases/tag/v1.0-kotlin)

## Project structure

| Folder | What it is |
| --- | --- |
| `whisper_java` | The Android app. Core sources are Kotlin: `MainActivity`, `asr` (Whisper / Recorder / Player), `engine` (WhisperEngine / WhisperEngineJava), `utils` (WaveUtil / WhisperUtil). UI layer is Jetpack Compose. |
| `whisper_native` | An alternative app using the TensorFlow Lite Native (C++) API for inference. |
| `models_and_scripts` | `generate_model.py` for converting Whisper checkpoints to TFLite, plus pre-generated models. |
| `demo_and_apk` | Pre-built APK, screenshot and demo video. |

## Building

Open `whisper_java` in Android Studio, or from the command line:

```bash
cd whisper_java
./gradlew :app:assembleDebug
```

Requirements: JDK 17, Android SDK with platform 35 (`compileSdk 35`, `targetSdk 34`, `minSdk 26`).

## Integration guide

Use `Whisper` for speech recognition and `Recorder` for audio capture.

**Whisper (speech recognition)**

```kotlin
// Initialize
val whisper = Whisper(this)

// Load model and vocabulary (multilingual = true for whisper-tiny, false for whisper-tiny.en)
whisper.loadModel(modelPath, vocabPath, true)

// Listen for status updates and results
whisper.setListener(object : IWhisperListener {
    override fun onUpdateReceived(message: String) {
        // model status updates
    }
    override fun onResultReceived(result: String) {
        // transcribed text
    }
})

// Transcribe a WAV file (16 kHz, mono, 16-bit)
whisper.setFilePath(waveFilePath)
whisper.setAction(Whisper.ACTION_TRANSCRIBE)
whisper.start()

// Stop when done
whisper.stop()
```

**Recorder (audio capture, 16 kHz / mono / 16-bit)**

```kotlin
val recorder = Recorder(this)

recorder.setListener(object : IRecorderListener {
    override fun onUpdateReceived(message: String) {
        // recording status updates
    }
    override fun onDataReceived(samples: FloatArray) {
        // forward live audio to Whisper for real-time recognition
        whisper.writeBuffer(samples)
    }
})

recorder.setFilePath(waveFilePath)
recorder.start()
// ...
recorder.stop()
```

Handle `RECORD_AUDIO` permission yourself before starting the recorder.

## Demo videos

- [Whisper ASR demo](https://www.youtube.com/watch?v=w9pohi9NQrg)
- [Live transcription](https://www.youtube.com/watch?v=qUg6_v7b9Io)

## Credits

Based on the original Whisper TFLite Android project by **Vilas Ninawe** (MIT, © 2023) — see `LICENSE.txt`. The demo videos above are from the original project.

`ROADMAP.md` has a phased plan for further modernization.

## License

MIT — see [LICENSE.txt](LICENSE.txt).
