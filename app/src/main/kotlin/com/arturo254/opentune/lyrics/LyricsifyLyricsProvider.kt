package com.arturo254.opentune.lyrics

import android.content.Context
import com.arturo254.opentune.constants.EnableLyricsifyKey
import com.arturo254.opentune.lyricsify.Lyricsify
import com.arturo254.opentune.utils.dataStore
import com.arturo254.opentune.utils.get

object LyricsifyLyricsProvider : LyricsProvider {
    override val name = "Lyricsify"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLyricsifyKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = Lyricsify.getLyrics(title, artist)
}
