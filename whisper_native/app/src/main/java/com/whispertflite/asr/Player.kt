package com.whispertflite.asr

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

class Player(private val context: Context) {

    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackStopped()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playbackListener: PlaybackListener? = null

    fun setListener(listener: PlaybackListener) {
        this.playbackListener = listener
    }

    fun initializePlayer(filePath: String) {
        val waveFileUri = Uri.parse(filePath)

        releaseMediaPlayer() // Release any existing MediaPlayer

        mediaPlayer = MediaPlayer.create(context, waveFileUri)
        if (mediaPlayer != null) {
            mediaPlayer?.setOnPreparedListener {
                playbackListener?.onPlaybackStarted()
                mediaPlayer?.start()
            }

            mediaPlayer?.setOnCompletionListener {
                playbackListener?.onPlaybackStopped()
                releaseMediaPlayer()
            }
        } else {
            playbackListener?.onPlaybackStopped()
        }
    }

    fun startPlayback() {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
            playbackListener?.onPlaybackStarted()
        }
    }

    fun stopPlayback() {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            mediaPlayer?.stop()
            playbackListener?.onPlaybackStopped()
            releaseMediaPlayer()
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer != null && mediaPlayer!!.isPlaying
    }

    private fun releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
}
