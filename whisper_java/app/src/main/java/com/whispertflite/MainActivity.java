package com.whispertflite;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.compose.SetContentKt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.whispertflite.asr.Player;
import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;
import com.whispertflite.ui.MainRouteKt;
import com.whispertflite.ui.MainViewModel;
import com.whispertflite.utils.WaveUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

public class MainActivity extends ComponentActivity {
    private static final String TAG = "MainActivity";

    private static final String DEFAULT_MODEL_TO_USE = "whisper-tiny.tflite";
    private static final String ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite";
    private static final String ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin";
    private static final String MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin";
    private static final String[] EXTENSIONS_TO_COPY = {"tflite", "bin", "wav", "pcm"};

    private Player mPlayer;
    private Recorder mRecorder;
    private Whisper mWhisper;

    private File sdcardDataFolder;
    private File selectedWaveFile;
    private File selectedTfliteFile;

    private MainViewModel viewModel;
    private long startTime = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        sdcardDataFolder = this.getExternalFilesDir(null);
        copyAssetsToSdcard(this, sdcardDataFolder, EXTENSIONS_TO_COPY);

        ArrayList<File> tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite");
        ArrayList<File> waveFiles = getFilesWithExtension(sdcardDataFolder, ".wav");

        selectedTfliteFile = new File(sdcardDataFolder, DEFAULT_MODEL_TO_USE);
        selectedWaveFile = waveFiles.isEmpty() ? null : waveFiles.get(0);

        viewModel.setFiles(toNames(tfliteFiles), toNames(waveFiles), DEFAULT_MODEL_TO_USE);

        SetContentKt.setContent(this, () -> {
            MainRouteKt.MainRoute(
                    viewModel,
                    fileName -> {
                        onModelSelected(fileName);
                        return Unit.INSTANCE;
                    },
                    fileName -> {
                        onWaveSelected(fileName);
                        return Unit.INSTANCE;
                    },
                    () -> {
                        onRecordPressed();
                        return Unit.INSTANCE;
                    },
                    () -> {
                        onPlayPressed();
                        return Unit.INSTANCE;
                    },
                    () -> {
                        onTranscribePressed();
                        return Unit.INSTANCE;
                    },
                    () -> {
                        copyTranscript();
                        return Unit.INSTANCE;
                    }
            );
            return Unit.INSTANCE;
        });

        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Recorder update: " + message);
                handler.post(() -> {
                    viewModel.setStatus(message);
                    if (message.equals(Recorder.MSG_RECORDING)) {
                        viewModel.clearTranscript();
                        viewModel.setRecording(true);
                    } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                        viewModel.setRecording(false);
                    }
                });
            }

            @Override
            public void onDataReceived(float[] samples) {
                // mWhisper.writeBuffer(samples);
            }
        });

        mPlayer = new Player(this);
        mPlayer.setListener(new Player.PlaybackListener() {
            @Override
            public void onPlaybackStarted() {
                handler.post(() -> viewModel.setPlaying(true));
            }

            @Override
            public void onPlaybackStopped() {
                handler.post(() -> viewModel.setPlaying(false));
            }
        });

        checkRecordPermission();
    }

    private void onModelSelected(String modelName) {
        deinitModel();
        selectedTfliteFile = new File(sdcardDataFolder, modelName);
        viewModel.onModelSelected(modelName);
    }

    private void onWaveSelected(String waveName) {
        selectedWaveFile = new File(sdcardDataFolder, waveName);
        viewModel.onWaveSelected(waveName);
    }

    private void onRecordPressed() {
        if (mRecorder != null && mRecorder.isInProgress()) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void onPlayPressed() {
        if (selectedWaveFile == null) {
            viewModel.setStatus("No wave file available");
            return;
        }

        if (!mPlayer.isPlaying()) {
            mPlayer.initializePlayer(selectedWaveFile.getAbsolutePath());
            mPlayer.startPlayback();
        } else {
            mPlayer.stopPlayback();
        }
    }

    private void onTranscribePressed() {
        if (selectedWaveFile == null || selectedTfliteFile == null) {
            viewModel.setStatus("Please select model and input file");
            return;
        }

        if (mRecorder != null && mRecorder.isInProgress()) {
            stopRecording();
        }

        if (mWhisper == null) {
            initModel(selectedTfliteFile);
        }

        if (!mWhisper.isInProgress()) {
            startTranscription(selectedWaveFile.getAbsolutePath());
            viewModel.setTranscribing(true);
        } else {
            stopTranscription();
            viewModel.setTranscribing(false);
        }
    }

    private void initModel(File modelFile) {
        boolean isMultilingualModel = !(modelFile.getName().endsWith(ENGLISH_ONLY_MODEL_EXTENSION));
        String vocabFileName = isMultilingualModel ? MULTILINGUAL_VOCAB_FILE : ENGLISH_ONLY_VOCAB_FILE;
        File vocabFile = new File(sdcardDataFolder, vocabFileName);

        mWhisper = new Whisper(this);
        mWhisper.loadModel(modelFile, vocabFile, isMultilingualModel);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "Whisper update: " + message);

                if (message.equals(Whisper.MSG_PROCESSING)) {
                    startTime = System.currentTimeMillis();
                    handler.post(() -> {
                        viewModel.setStatus(message);
                        viewModel.clearTranscript();
                        viewModel.setTranscribing(true);
                    });
                } else if (message.equals(Whisper.MSG_PROCESSING_DONE)) {
                    handler.post(() -> viewModel.setTranscribing(false));
                } else if (message.equals(Whisper.MSG_FILE_NOT_FOUND)) {
                    handler.post(() -> {
                        viewModel.setStatus(message);
                        viewModel.setTranscribing(false);
                    });
                }
            }

            @Override
            public void onResultReceived(String result) {
                long timeTaken = System.currentTimeMillis() - startTime;
                handler.post(() -> {
                    viewModel.setStatus("Processing done in " + timeTaken + "ms");
                    viewModel.appendTranscript(result);
                });
            }
        });
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            viewModel.setStatus(getString(R.string.need_record_audio_permission));
        }
    }

    private void startRecording() {
        checkRecordPermission();

        File waveFile = new File(sdcardDataFolder, WaveUtil.RECORDING_FILE);
        mRecorder.setFilePath(waveFile.getAbsolutePath());
        mRecorder.start();

        selectedWaveFile = waveFile;
        viewModel.onWaveSelected(waveFile.getName());
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    private void startTranscription(String waveFilePath) {
        mWhisper.setFilePath(waveFilePath);
        mWhisper.setAction(Whisper.ACTION_TRANSCRIBE);
        mWhisper.start();
    }

    private void stopTranscription() {
        mWhisper.stop();
    }

    private void copyTranscript() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String textToCopy = viewModel.getUiState().getValue().getTranscript();
        ClipData clip = ClipData.newPlainText("Transcript", textToCopy);
        clipboard.setPrimaryClip(clip);
        viewModel.setStatus("Transcript copied");
    }

    private static void copyAssetsToSdcard(Context context, File destFolder, String[] extensions) {
        AssetManager assetManager = context.getAssets();

        try {
            String[] assetFiles = assetManager.list("");
            if (assetFiles == null) return;

            for (String assetFileName : assetFiles) {
                for (String extension : extensions) {
                    if (assetFileName.endsWith("." + extension)) {
                        File outFile = new File(destFolder, assetFileName);

                        if (outFile.exists()) break;

                        try (InputStream inputStream = assetManager.open(assetFileName);
                             OutputStream outputStream = new FileOutputStream(outFile)) {

                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy assets", e);
        }
    }

    private ArrayList<File> getFilesWithExtension(File directory, String extension) {
        ArrayList<File> filteredFiles = new ArrayList<>();

        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(extension)) {
                        filteredFiles.add(file);
                    }
                }
            }
        }

        return filteredFiles;
    }

    private static List<String> toNames(List<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }
}
