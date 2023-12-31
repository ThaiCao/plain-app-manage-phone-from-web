package com.ismartcoding.plain.web.models

import com.ismartcoding.plain.features.audio.DAudio
import com.ismartcoding.plain.features.audio.DPlaylistAudio
import com.ismartcoding.plain.helpers.FileHelper

data class Audio(
    val id: ID,
    val title: String,
    val artist: String,
    val path: String,
    val duration: Long,
    val size: Long,
)

data class PlaylistAudio(
    val title: String,
    val artist: String,
    val path: String,
    val duration: Long,
)

fun DAudio.toModel(): Audio {
    return Audio(ID(id), title, artist, path, duration, size)
}

fun DPlaylistAudio.toModel(): PlaylistAudio {
    return PlaylistAudio(title, artist, path, duration)
}

