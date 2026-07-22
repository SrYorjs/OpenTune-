/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.arturo254.opentune.ui.screens.playlist

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.arturo254.opentune.LocalDatabase
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.LocalPlayerConnection
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.AppBarHeight
import com.arturo254.opentune.constants.DisableBlurKey
import com.arturo254.opentune.constants.InnerTubeCookieKey
import com.arturo254.opentune.extensions.togglePlayPause
import com.arturo254.opentune.models.MediaMetadata
import com.arturo254.opentune.db.entities.Playlist
import com.arturo254.opentune.db.entities.PlaylistEntity
import com.arturo254.opentune.innertube.YouTube
import com.arturo254.opentune.playback.ExoDownloadService
import com.arturo254.opentune.spotify.SpotifyMapper
import com.arturo254.opentune.spotify.SpotifyPlaybackResolver
import com.arturo254.opentune.spotify.SpotifyPlaylistQueue
import com.arturo254.opentune.spotify.SpotifyPlaylistViewModel
import com.arturo254.opentune.spotify.models.SpotifyTrack
import com.arturo254.opentune.ui.component.DraggableScrollbar
import com.arturo254.opentune.ui.component.EmptyPlaceholder
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.component.SpotifyTrackListItem
import com.arturo254.opentune.ui.theme.PlayerColorExtractor
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.ui.utils.resize
import com.arturo254.opentune.utils.makeTimeString
import com.arturo254.opentune.utils.rememberPreference
import com.arturo254.opentune.ui.component.ExpressivePullToRefreshBox
import java.time.LocalDateTime
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val playlist = state.playlist
    val tracks = state.tracks
    val lazyListState = rememberLazyListState()
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    val filteredTracks =
        remember(tracks, query.text) {
            if (query.text.isBlank()) {
                tracks
            } else {
                tracks.filter { track ->
                    track.name.contains(query.text, ignoreCase = true) ||
                            track.artists.any { artist ->
                                artist.name.contains(
                                    query.text,
                                    ignoreCase = true
                                )
                            } ||
                            track.album?.name?.contains(query.text, ignoreCase = true) == true
                }
            }
        }

    val loadedDurationMs =
        remember(tracks) {
            tracks.sumOf { track -> track.durationMs.toLong() }
        }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val thumbnailUrl =
        remember(playlist) {
            playlist?.let { SpotifyMapper.getPlaylistThumbnail(it)?.resize(544, 544) }
        }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                        PlayerColorExtractor.Config.IMAGE_SIZE
                    )
                    .allowHardware(false)
                    .build()

            val result =
                runCatching {
                    context.imageLoader.execute(request)
                }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }

                    gradientColors =
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf { !disableBlur && !showTopBarTitle && !isSearching }
    }

    val topAppBarColors =
        if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            )
        }

    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    fun playPlaylist(
        startIndex: Int = 0,
        shuffled: Boolean = false,
    ) {
        val currentPlaylist = playlist ?: return
        val queueTracks = if (shuffled) tracks.shuffled() else tracks
        if (queueTracks.isEmpty()) return
        val boundedStartIndex = startIndex.coerceIn(queueTracks.indices)
        val preloadTrack = queueTracks[boundedStartIndex]
        if (resolvingTrackId != null) return

        coroutineScope.launch {
            resolvingTrackId = preloadTrack.id
            try {
                val preloadItem = SpotifyPlaybackResolver.resolveToMetadata(preloadTrack)
                playerConnection?.playQueue(
                    SpotifyPlaylistQueue(
                        playlistId = currentPlaylist.id,
                        title = currentPlaylist.name,
                        initialTracks = queueTracks,
                        startIndex = boundedStartIndex,
                        preloadItem = preloadItem,
                    ),
                )
            } finally {
                resolvingTrackId = null
            }
        }
    }

    // ── Descargas ────────────────────────────────────────────────────────
    // Reutiliza exactamente la misma resolución a YouTube Music que ya usa
    // playPlaylist() para reproducir, y el mismo pipeline de descarga que
    // usa el resto de la app (DownloadRequest + DownloadService), solo que
    // en vez de encolarla en el reproductor, la manda a descargar offline.
    val trackDownloadState = remember { mutableStateMapOf<String, SpotifyTrackDownloadState>() }
    var isDownloadingAll by remember { mutableStateOf(false) }
    var downloadAllProgress by remember { mutableStateOf(0 to 0) }

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()
    var isCreatingYtmPlaylist by remember { mutableStateOf(false) }
    var ytmCreateProgress by remember { mutableStateOf(0 to 0) }

    fun createYouTubeMusicPlaylist() {
        if (isCreatingYtmPlaylist || tracks.isEmpty() || !isSignedIn) return
        coroutineScope.launch {
            isCreatingYtmPlaylist = true
            val spotifyPlaylist = state.playlist
            val playlistName = spotifyPlaylist?.name ?: "Spotify"
            val playlistDescription = spotifyPlaylist?.description?.takeIf { it.isNotBlank() }
            val playlistCoverUrl = spotifyPlaylist?.images?.firstOrNull()?.url?.takeIf { it.isNotBlank() }

            val browseId = YouTube.createPlaylist(playlistName, playlistDescription).getOrNull()
            if (browseId == null) {
                isCreatingYtmPlaylist = false
                Toast.makeText(context, context.getString(R.string.spotify_ytm_create_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            val entity = PlaylistEntity(
                name = playlistName,
                browseId = browseId,
                bookmarkedAt = LocalDateTime.now(),
                isEditable = true,
                thumbnailUrl = playlistCoverUrl,
            )
            // Todo el trabajo de base de datos va en UN solo bloque .transaction{},
            // que despacha al executor de Room en background — llamar a estas
            // funciones directamente en la corrutina (que corre en el hilo
            // principal de Compose) es lo que causaba el crash de Room.
            database.transaction {
                insert(entity)
            }
            val playlist = Playlist(playlist = entity, songCount = 0, songThumbnails = emptyList())
            var notFoundCount = 0
            tracks.forEachIndexed { index, track ->
                ytmCreateProgress = index to tracks.size
                val metadata = SpotifyPlaybackResolver.resolveToMetadata(track)
                if (metadata == null) {
                    notFoundCount++
                } else {
                    database.transaction {
                        insert(metadata)
                        addSongToPlaylist(playlist, listOf(metadata.id))
                    }
                    YouTube.addToPlaylist(browseId, metadata.id)
                }
            }
            ytmCreateProgress = tracks.size to tracks.size
            isCreatingYtmPlaylist = false
            if (notFoundCount > 0) {
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(
                        R.plurals.spotify_download_not_found,
                        notFoundCount,
                        notFoundCount,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    suspend fun downloadSpotifyTrack(track: SpotifyTrack): Boolean {
        trackDownloadState[track.id] = SpotifyTrackDownloadState.RESOLVING
        val metadata = SpotifyPlaybackResolver.resolveToMetadata(track)
        if (metadata == null) {
            trackDownloadState[track.id] = SpotifyTrackDownloadState.NOT_FOUND
            return false
        }
        database.transaction {
            insert(metadata)
        }
        val downloadRequest = DownloadRequest
            .Builder(metadata.id, metadata.id.toUri())
            .setCustomCacheKey(metadata.id)
            .setData(metadata.title.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            downloadRequest,
            false,
        )
        trackDownloadState[track.id] = SpotifyTrackDownloadState.QUEUED
        return true
    }

    fun downloadTrack(track: SpotifyTrack) {
        if (trackDownloadState[track.id] == SpotifyTrackDownloadState.RESOLVING) return
        coroutineScope.launch {
            downloadSpotifyTrack(track)
        }
    }

    fun downloadPlaylist() {
        if (isDownloadingAll || tracks.isEmpty()) return
        coroutineScope.launch {
            isDownloadingAll = true
            var notFoundCount = 0
            tracks.forEachIndexed { index, track ->
                downloadAllProgress = index to tracks.size
                if (trackDownloadState[track.id] != SpotifyTrackDownloadState.QUEUED) {
                    if (!downloadSpotifyTrack(track)) notFoundCount++
                }
            }
            downloadAllProgress = tracks.size to tracks.size
            isDownloadingAll = false
            if (notFoundCount > 0) {
                Toast
                    .makeText(
                        context,
                        context.resources.getQuantityString(
                            R.plurals.spotify_download_not_found,
                            notFoundCount,
                            notFoundCount,
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = viewModel::reload,
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxSize(0.55f)
                        .align(Alignment.TopCenter)
                        .zIndex(-1f)
                        .drawBehind {
                            val width = size.width
                            val height = size.height

                            if (gradientColors.size >= 3) {
                                val c0 = gradientColors[0]
                                val c1 = gradientColors[1]
                                val c2 = gradientColors[2]
                                val c3 = gradientColors.getOrElse(3) { c0 }
                                val c4 = gradientColors.getOrElse(4) { c1 }
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c0.copy(alpha = gradientAlpha * 0.75f),
                                                    c0.copy(alpha = gradientAlpha * 0.4f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.15f),
                                            radius = width * 0.8f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c1.copy(alpha = gradientAlpha * 0.55f),
                                                    c1.copy(alpha = gradientAlpha * 0.3f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.1f, height * 0.4f),
                                            radius = width * 0.6f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c2.copy(alpha = gradientAlpha * 0.5f),
                                                    c2.copy(alpha = gradientAlpha * 0.25f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.9f, height * 0.35f),
                                            radius = width * 0.55f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c3.copy(alpha = gradientAlpha * 0.35f),
                                                    c3.copy(alpha = gradientAlpha * 0.18f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.25f, height * 0.65f),
                                            radius = width * 0.75f,
                                        ),
                                )
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c4.copy(alpha = gradientAlpha * 0.3f),
                                                    c4.copy(alpha = gradientAlpha * 0.15f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.55f, height * 0.85f),
                                            radius = width * 0.9f,
                                        ),
                                )
                            } else if (gradientColors.isNotEmpty()) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.25f),
                                            radius = width * 0.85f,
                                        ),
                                )
                            }
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                                surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                                surfaceColor,
                                            ),
                                        startY = height * 0.4f,
                                        endY = height,
                                    ),
                            )
                        },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isSearching) {
                playlist?.let { currentPlaylist ->
                    item(key = "header") {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + AppBarHeight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .padding(top = 8.dp, bottom = 20.dp),
                            ) {
                                Surface(
                                    modifier =
                                        Modifier
                                            .size(240.dp)
                                            .shadow(
                                                elevation = 24.dp,
                                                shape = RoundedCornerShape(16.dp),
                                                spotColor =
                                                    gradientColors.getOrNull(0)?.copy(alpha = 0.5f)
                                                        ?: MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.3f
                                                        ),
                                            ),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    if (thumbnailUrl != null) {
                                        AsyncImage(
                                            model = thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.queue_music),
                                                contentDescription = null,
                                                modifier = Modifier.size(80.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = currentPlaylist.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )

                            currentPlaylist.owner?.displayName?.takeIf(String::isNotBlank)
                                ?.let { owner ->
                                    Text(
                                        text = owner,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier =
                                            Modifier
                                                .padding(top = 8.dp)
                                                .padding(horizontal = 32.dp),
                                    )
                                }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val trackCount = currentPlaylist.tracks?.total ?: tracks.size
                                MetadataChip(
                                    icon = R.drawable.music_note,
                                    text = pluralStringResource(
                                        R.plurals.n_song,
                                        trackCount,
                                        trackCount
                                    ),
                                )

                                if (loadedDurationMs > 0L) {
                                    MetadataChip(
                                        icon = R.drawable.timer,
                                        text = makeTimeString(loadedDurationMs),
                                    )
                                }
                            }

                            currentPlaylist.description?.takeIf(String::isNotBlank)
                                ?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier =
                                            Modifier
                                                .padding(top = 16.dp)
                                                .padding(horizontal = 32.dp),
                                    )
                                }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { viewModel.reload() },
                                    modifier = Modifier.size(48.dp),
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.sync),
                                        contentDescription = stringResource(R.string.spotify_reload_playlist),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { playPlaylist() },
                                    enabled = tracks.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { downloadPlaylist() },
                                    enabled = tracks.isNotEmpty() && !isDownloadingAll,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                ) {
                                    if (isDownloadingAll) {
                                        CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.download),
                                            contentDescription = stringResource(R.string.spotify_download_playlist),
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { playPlaylist(shuffled = true) },
                                    enabled = tracks.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = stringResource(R.string.shuffle),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            if (isDownloadingAll) {
                                Text(
                                    text = stringResource(
                                        R.string.spotify_downloading_playlist,
                                        downloadAllProgress.first,
                                        downloadAllProgress.second,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }

                            Button(
                                onClick = { createYouTubeMusicPlaylist() },
                                enabled = tracks.isNotEmpty() && isSignedIn && !isCreatingYtmPlaylist,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            ) {
                                if (isCreatingYtmPlaylist) {
                                    CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.playlist_add),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.spotify_create_ytm_playlist))
                                }
                            }

                            if (isCreatingYtmPlaylist) {
                                Text(
                                    text = stringResource(
                                        R.string.spotify_creating_ytm_playlist,
                                        ytmCreateProgress.first,
                                        ytmCreateProgress.second,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            } else if (!isSignedIn) {
                                Text(
                                    text = stringResource(R.string.not_logged_in_youtube),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { playPlaylist(shuffled = true) },
                                    enabled = tracks.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.mix),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }

            if (state.isLoading && tracks.isEmpty()) {
                item(key = "loading") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }

            state.errorMessage?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            if (!state.isLoading && state.errorMessage == null && filteredTracks.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text =
                            stringResource(
                                if (query.text.isBlank()) {
                                    R.string.spotify_no_tracks
                                } else {
                                    R.string.ai_model_no_results
                                },
                            ),
                    )
                }
            }

            itemsIndexed(
                items = filteredTracks,
                key = { index, track -> "spotify_track_${track.id}_$index" },
                contentType = { _, _ -> "spotify_track" },
            ) { index, track ->
                val trackIsActive =
                    remember(track, mediaMetadata) {
                        track.isResolvedAs(mediaMetadata)
                    }
                val trackIsResolving = resolvingTrackId == track.id

                SpotifyTrackListItem(
                    track = track,
                    isActive = trackIsActive || trackIsResolving,
                    isPlaying = isPlaying && !trackIsResolving,
                    trailingContent = {
                        if (trackIsResolving) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        when (trackDownloadState[track.id]) {
                            SpotifyTrackDownloadState.RESOLVING -> {
                                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                            SpotifyTrackDownloadState.QUEUED -> {
                                Icon(
                                    painter = painterResource(R.drawable.offline),
                                    contentDescription = stringResource(R.string.downloading),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            SpotifyTrackDownloadState.NOT_FOUND -> {
                                IconButton(
                                    onClick = { downloadTrack(track) },
                                    onLongClick = {},
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.error),
                                        contentDescription = stringResource(R.string.action_download),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            null -> {
                                IconButton(
                                    onClick = { downloadTrack(track) },
                                    onLongClick = {},
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = stringResource(R.string.action_download),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = resolvingTrackId == null || trackIsActive) {
                                if (trackIsActive) {
                                    playerConnection?.player?.togglePlayPause()
                                } else {
                                    val startIndex =
                                        tracks
                                            .indexOfFirst { item -> item.id == track.id }
                                            .takeIf { itemIndex -> itemIndex >= 0 }
                                            ?: index
                                    playPlaylist(startIndex = startIndex)
                                }
                            },
                )
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime)
                            .asPaddingValues(),
                    )
                    .align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = if (!isSearching && playlist != null) 1 else 0,
        )

        TopAppBar(
            colors = topAppBarColors,
            title = {
                if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = playlist?.name ?: stringResource(R.string.spotify_playlists),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching) navController.backToMain()
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isSearching) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

private enum class SpotifyTrackDownloadState { RESOLVING, QUEUED, NOT_FOUND }

private fun SpotifyTrack.isResolvedAs(mediaMetadata: MediaMetadata?): Boolean {
    if (mediaMetadata == null) return false

    mediaMetadata.spotifyTrackId?.let { spotifyTrackId ->
        return id.isNotBlank() && spotifyTrackId == id
    }

    val titleMatches = name.equals(mediaMetadata.title, ignoreCase = true)
    val durationMatches =
        durationMs <= 0 ||
                mediaMetadata.duration <= 0 ||
                abs(durationMs.toLong() - mediaMetadata.duration * 1000L) <= 1_000L
    val albumMatches =
        album?.let { spotifyAlbum ->
            val currentAlbum = mediaMetadata.album ?: return false
            spotifyAlbum.id.isNotBlank() && spotifyAlbum.id == currentAlbum.id ||
                    spotifyAlbum.name.equals(currentAlbum.title, ignoreCase = true)
        } ?: true
    val artistMatches =
        artists.isEmpty() ||
                mediaMetadata.artists.isEmpty() ||
                artists.any { spotifyArtist ->
                    mediaMetadata.artists.any { artist ->
                        spotifyArtist.name.equals(artist.name, ignoreCase = true)
                    }
                }
    val thumbnailMatches =
        SpotifyMapper.getTrackThumbnail(this)?.let { thumbnail ->
            thumbnail == mediaMetadata.thumbnailUrl
        } ?: true

    return titleMatches && durationMatches && albumMatches && artistMatches && thumbnailMatches
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}