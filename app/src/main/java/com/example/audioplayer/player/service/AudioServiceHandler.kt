package com.example.audioplayer.player.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class AudioServiceHandler @Inject constructor(
    private val exoPlayer: ExoPlayer
): Player.Listener {

    private val _audioState : MutableStateFlow<AudioState> = MutableStateFlow(AudioState.Initial)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var job: Job? = null

    init {
        exoPlayer.addListener(this )
    }

    /*fun addMediaItem(mediaItem: MediaItem){
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }*/

    fun setMediaItemList(mediaItems:List<MediaItem>){
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()
    }

    fun clearMediaItemList(){
        exoPlayer.clearMediaItems()
    }

    fun stopMedia(){
        exoPlayer.pause()
    }

    suspend fun onPlayerEvents(
        playerEvent: PlayerEvent,
        selectedAudioIndex:Int = -1,
        seekPosition:Long = 0
    ) {
        when(playerEvent){
            PlayerEvent.BackWard -> exoPlayer.seekBack()
            PlayerEvent.Forward -> exoPlayer.seekForward()
            PlayerEvent.SeekToNext -> exoPlayer.seekToNext()
            PlayerEvent.SeekToPrevious -> exoPlayer.seekToPrevious()
            PlayerEvent.PlayPause -> playOrPause()
            PlayerEvent.SeekTo -> exoPlayer.seekTo(seekPosition)
            PlayerEvent.SelectedAudioChange -> {
                when(selectedAudioIndex){
                    exoPlayer.currentMediaItemIndex -> {
                        playOrPause()
                    }
                    else -> {
                        exoPlayer.seekToDefaultPosition(selectedAudioIndex)
                        _audioState.value = AudioState.Playing(
                            isPlaying = true
                        )
                        exoPlayer.playWhenReady = true
                        startProgressUpdate()
                    }
                }
            }
            PlayerEvent.Stop -> stopProgressUpdate()
            is PlayerEvent.UpdateProgress -> {
                exoPlayer.seekTo(
                    (exoPlayer.duration * playerEvent.newProgress).toLong()
                )
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        exoPlayer.seekBack()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when(playbackState){
            ExoPlayer.STATE_BUFFERING -> _audioState.value =
                AudioState.Buffering(exoPlayer.currentPosition)

            ExoPlayer.STATE_READY -> _audioState.value =
                AudioState.Ready(exoPlayer.duration)

            Player.STATE_ENDED -> {

            }

            Player.STATE_IDLE -> {

            }
        }
    }



    @OptIn(DelicateCoroutinesApi::class)
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _audioState.value = AudioState.Playing(isPlaying = isPlaying)
        _audioState.value = AudioState.CurrentPlaying(exoPlayer.currentMediaItemIndex)
        if (isPlaying){
            GlobalScope.launch(Dispatchers.Main) {
                startProgressUpdate()
            }
        }else{
            stopProgressUpdate()
        }
    }

    private suspend fun playOrPause(){
        if (exoPlayer.isPlaying){
            exoPlayer.pause()
            stopProgressUpdate()
        }else{
            exoPlayer.play()
            _audioState.value = AudioState.Playing(
                isPlaying = true
            )
            startProgressUpdate()
        }
    }
    private suspend fun startProgressUpdate() = job.run {
        while (true){
            delay(500)
            _audioState.value = AudioState.Progress(exoPlayer.currentPosition)
        }
    }
    private fun stopProgressUpdate(){
        job?.cancel()
        _audioState.value = AudioState.Playing(isPlaying = false)
    }
}

sealed class PlayerEvent{
    data object PlayPause:PlayerEvent()
    data object SelectedAudioChange:PlayerEvent()
    data object BackWard:PlayerEvent()
    data object SeekToNext:PlayerEvent()
    data object SeekToPrevious:PlayerEvent()
    data object Forward:PlayerEvent()
    data object SeekTo:PlayerEvent()
    data object Stop:PlayerEvent()
    data class UpdateProgress(val newProgress:Float):PlayerEvent()
}

sealed class AudioState{
    data object Initial:AudioState()
    data class Ready(val duration: Long):AudioState()
    data class Progress(val progress: Long):AudioState()
    data class Buffering(val progress: Long):AudioState()
    data class Playing(val isPlaying: Boolean):AudioState()
    data class CurrentPlaying(val mediaItemIndex: Int):AudioState()
}