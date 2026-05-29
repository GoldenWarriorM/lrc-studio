package com.lrcstudio.app.util

import android.media.MediaMetadataRetriever

actual fun extractAudioMetadata(path: String): AudioMetadata {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: ""
        retriever.release()
        AudioMetadata(title, artist, album, composer)
    } catch (_: Exception) {
        AudioMetadata()
    }
}
