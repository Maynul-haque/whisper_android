package com.whispertflite.asr

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

class Player(context: Context) {

    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackStopped()
    }

    private val context = context
    private var mediaPlayer: MediaPlayer? = null
    private var playbackListener: PlaybackListener? = null

    fun setListener(listener: PlaybackListener?) {
        this.playbackListener = listener
    }

    fun initializePlayer(filePath: String) {
        val waveFileUri = Uri.parse(filePath)
        if (waveFileUri == null) {
            Log.e("WavePlayer", "File path is null. Cannot initialize MediaPlayer.")
            return
        }

        releaseMediaPlayer() // Release any existing MediaPlayer

        val player = MediaPlayer.create(context, waveFileUri)
        mediaPlayer = player
        if (player != null) {
            player.setOnPreparedListener {
                playbackListener?.onPlaybackStarted()
                player.start()
            }

            player.setOnCompletionListener {
                playbackListener?.onPlaybackStopped()
                releaseMediaPlayer()
            }
        } else {
            playbackListener?.onPlaybackStopped()
        }
    }

    fun startPlayback() {
        val player = mediaPlayer
        if (player != null && !player.isPlaying) {
            player.start()
            playbackListener?.onPlaybackStarted()
        }
    }

    fun stopPlayback() {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            player.stop()
            playbackListener?.onPlaybackStopped()
            releaseMediaPlayer()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
