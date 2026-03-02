# Whisper Android Modernization Roadmap

This document outlines a practical plan to improve this repository and upgrade it with modern Android and on-device ASR features.

## Goals

- Keep fully offline speech recognition as the core value proposition.
- Improve transcription quality, latency, and stability across devices.
- Modernize the developer experience and release workflow.

## Phase 1 — Foundation Upgrades (1–2 weeks)

### 1. Toolchain and dependencies
- Upgrade Android Gradle Plugin, Gradle wrapper, Kotlin plugin, and AndroidX libs to latest stable.
- Upgrade TensorFlow Lite Java/native dependencies and verify ABI compatibility for `armeabi-v7a` and `arm64-v8a`.
- Keep Java/Kotlin compatibility aligned with current Android Studio defaults.

### 2. Engine architecture cleanup
- Consolidate shared ASR behavior into a single core abstraction used by both Java and Native apps.
- Add explicit runtime engine/delegate strategy (CPU, NNAPI, GPU, native JNI).
- Remove dead/commented code paths and introduce explicit feature flags.

### 3. Model generation reliability
- Replace the current placeholder `models_and_scripts/generate_model.py` with a real CLI workflow.
- Inputs: model ID, multilingual flag, quantization mode.
- Outputs: `.tflite` model, matching vocab/filter binaries, metadata JSON.
- Keep notebook for experimentation, but enforce script as source-of-truth.

## Phase 2 — Modern ASR Features (2–4 weeks)

### 1. Real-time streaming transcription
- Fully wire microphone stream chunks into the live buffer API.
- Emit partial + final transcript updates to UI.
- Introduce chunk/session boundaries with rolling context.

### 2. Voice Activity Detection (VAD)
- Add VAD-based silence trimming and speech segmentation.
- Reduce redundant inference and improve responsiveness in noisy environments.

### 3. Model manager
- Add model catalog and local cache management.
- Support downloading/installing multiple model sizes and language variants.
- Present model metadata (size, expected speed, target use-case).

### 4. Translation mode completion
- Complete `TRANSLATE` action path end-to-end with clear UI controls.

## Phase 3 — Performance, Quality, and Observability (2–3 weeks)

### 1. Device-aware runtime optimization
- Add automatic delegate selection with fallback strategy.
- Tune thread count dynamically based on device capabilities.

### 2. Benchmarking and regressions
- Add repeatable benchmark suite over bundled test WAV files.
- Track:
  - cold start time,
  - time-to-first-token,
  - real-time factor,
  - memory use,
  - WER/CER.

### 3. Stability hardening
- Add stricter lifecycle handling for record/play/transcribe concurrency.
- Improve cancellation and shutdown behavior for background transcription threads.

## Phase 4 — Product and DX Polish (1–2 weeks)

### 1. UX improvements
- Improve transcript display with timestamps and session history.
- Add export/copy/share options for transcript output.
- Optional: incremental migration from XML views to Jetpack Compose.

### 2. CI/CD and quality gates
- Add CI for lint, unit tests, and build verification of both modules.
- Add release workflow for signed APK generation.
- Add PR template and contribution checklist.

### 3. Documentation refresh
- Update README with architecture overview and module trade-offs.
- Add quickstart for model creation and delegate tuning.
- Add troubleshooting guide for common Android/TFLite issues.

## Prioritized Backlog (Suggested Execution Order)

1. Implement `generate_model.py` CLI and document usage.
2. Enable streaming live transcription in app flow.
3. Add delegate strategy selector and default auto mode.
4. Add benchmark + quality regression suite.
5. Complete translation mode UX and engine behavior.
6. Add model download/cache management.

## Definition of Done (per milestone)

- Functional demo on at least one mid-tier and one high-end Android device.
- No regressions in offline transcription functionality.
- Measured improvement against baseline latency/quality metrics.
- Updated docs and reproducible commands for all new workflows.
