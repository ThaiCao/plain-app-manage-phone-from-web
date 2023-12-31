package com.ismartcoding.plain.features.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.media.AudioManagerCompat
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.lib.media.AudioFocusHelper
import com.ismartcoding.lib.media.IMediaPlayer
import com.ismartcoding.plain.LocalStorage
import com.ismartcoding.plain.MainApp
import com.ismartcoding.plain.features.AudioActionEvent

class AudioPlayer : IMediaPlayer {
    companion object {
        val instance = AudioPlayer()
    }

    override fun isPlaying(): Boolean {
        return mediaPlayer.isPlaying
    }

    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private val audioManager by lazy { MainApp.instance.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    override var isPausedByTransientLossOfFocus = false
    private val audioFocusRequest = AudioFocusHelper.createRequest(this)
    private var playerProgress: Int = 0    // player progress

    var pendingQuit: Boolean = false

    init {
        setListen()
    }

    fun setPlayerProgress(progress: Int) {
        playerProgress = progress * 1000
    }

    fun getPlayerProgress(): Int {
        return if (mediaPlayer.isPlaying) {
            mediaPlayer.currentPosition / 1000
        } else {
            playerProgress / 1000
        }
    }

    fun play(path: String) {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.reset()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mediaPlayer.setDataSource(MainApp.instance, Uri.parse(path))
        mediaPlayer.prepareAsync()
        requestFocus()
    }

    override fun play() {
        try {
            play(LocalStorage.audioPlaying!!.path)
        } catch (e: Exception) {
            LogCat.e(e.toString())
            LocalStorage.audioPlaying?.let {
                LocalStorage.deletePlaylistAudio(it.path)
            }
            setChangedNotify(AudioAction.NOT_FOUND)
        }
    }

    fun seekTo(progress: Int) {
        if (mediaPlayer.isPlaying) {
            playerProgress = progress * 1000
            mediaPlayer.seekTo(playerProgress)
        } else {
            setPlayerProgress(progress)
            play()
        }
        setChangedNotify(AudioAction.SEEK)
    }

    fun skipToNext() {
        skipTo(isNext = true)
    }

    fun skipToPrevious() {
        skipTo(isNext = false)
    }

    private fun skipTo(isNext: Boolean) {
        var playerAudioList = LocalStorage.audioPlaylist
        if (playerAudioList.isEmpty()) {
            if (LocalStorage.audioPlaying != null) {
                LocalStorage.addPlaylistAudio(LocalStorage.audioPlaying!!)
                playerAudioList = LocalStorage.audioPlaylist
            } else {
                return
            }
        }

        if (LocalStorage.audioPlayMode == MediaPlayMode.SHUFFLE) {
            LocalStorage.audioPlaying = playerAudioList.random()
        } else {
            if (LocalStorage.audioPlaying != null) {
                var index = playerAudioList.indexOfFirst { it.path == LocalStorage.audioPlaying?.path }
                if (isNext) {
                    index++
                    if (index > playerAudioList.size - 1) {
                        index = 0
                    }
                } else {
                    index--
                    if (index < 0) {
                        index = playerAudioList.size - 1
                    }
                }
                LocalStorage.audioPlaying = playerAudioList[index]
            } else {
                LocalStorage.audioPlaying = playerAudioList[if (isNext) 0 else (playerAudioList.size - 1)]
            }
        }

        playerProgress = 0
        play()
    }

    override fun pause() {
        playerProgress = mediaPlayer.currentPosition

        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }

        setChangedNotify(AudioAction.PAUSE)
    }

    fun showNotification() {
        setChangedNotify(AudioAction.NOTIFICATION)
    }

    override fun stop() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
            abandonFocus()
        }
    }

    override fun setVolume(volume: Float) {
        mediaPlayer.setVolume(volume, volume)
    }

    private fun setListen() {
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.seekTo(playerProgress)
            mediaPlayer.start()
            setChangedNotify(AudioAction.PLAY)
        }

        mediaPlayer.setOnCompletionListener {
            setChangedNotify(AudioAction.COMPLETE)
        }

        mediaPlayer.setOnErrorListener { mp, what, extra ->
            LogCat.e("MediaPlayer error type:$what, code:$extra, currentPosition:${mp.currentPosition}")
            return@setOnErrorListener false
        }
    }

    private fun setChangedNotify(action: AudioAction) {
        sendEvent(AudioActionEvent(action))
    }

    private fun requestFocus(): Boolean {
        return AudioManagerCompat.requestAudioFocus(
            audioManager,
            audioFocusRequest
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        LogCat.e("abandonFocus")
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }
}