package com.example.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.IptvChannelEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay

data class VideoQualityTrack(
    val label: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val isAuto: Boolean = false
)

@OptIn(UnstableApi::class)
@Composable
fun NativeMedia3PlayerScreen(
    channels: List<IptvChannelEntity>,
    initialChannel: IptvChannelEntity?,
    autoRetryEnabled: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var activeChannel by remember(initialChannel, channels) {
        mutableStateOf(initialChannel ?: channels.firstOrNull())
    }

    // Build list of servers for active channel (Primary + Backups)
    val availableServers = remember(activeChannel) {
        val ch = activeChannel ?: return@remember emptyList<Pair<String, String>>()
        val list = mutableListOf("السيرفر الرئيسي (${ch.streamFormat})" to ch.streamUrl)
        ch.backupServersCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEachIndexed { idx, url ->
                list.add("سيرفر احتياطي #${idx + 2}" to url)
            }
        list
    }

    var selectedServerIndex by remember(activeChannel) { mutableIntStateOf(0) }
    val activeStreamUrl = remember(availableServers, selectedServerIndex) {
        availableServers.getOrNull(selectedServerIndex)?.second ?: activeChannel?.streamUrl.orEmpty()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var isFullScreenLandscape by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var volumeLevel by remember {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(cur.toFloat() / max.toFloat())
    }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var availableQualities by remember {
        mutableStateOf(listOf(VideoQualityTrack("تلقائي (Adaptive Auto)", 0, 0, 0, isAuto = true)))
    }
    var selectedQualityLabel by remember { mutableStateOf("تلقائي (Auto)") }

    // Create TrackSelector & ExoPlayer 1.5.1 instance
    val trackSelector = remember { DefaultTrackSelector(context) }
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // Prepare media item whenever activeStreamUrl changes
    LaunchedEffect(activeStreamUrl) {
        if (activeStreamUrl.isBlank()) return@LaunchedEffect
        playbackError = null
        isBuffering = true

        val lower = activeStreamUrl.lowercase()
        val mediaItemBuilder = MediaItem.Builder().setUri(activeStreamUrl)
        when {
            lower.contains(".m3u8") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.contains(".mpd") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            lower.contains(".mp4") -> mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Auto-retry coroutine when stream error occurs
    LaunchedEffect(playbackError, retryCount) {
        if (playbackError != null && autoRetryEnabled && retryCount < 3) {
            delay(3500L)
            retryCount++
            // If backup server exists and retry 2+, switch to backup server automatically!
            if (retryCount >= 2 && selectedServerIndex + 1 < availableServers.size) {
                selectedServerIndex++
            } else {
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // Attach ExoPlayer listener & release resources strictly on dispose
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                val qualityList = mutableListOf(
                    VideoQualityTrack("تلقائي (Adaptive Auto)", 0, 0, 0, isAuto = true)
                )
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                val label = "${format.height}p (${format.width}x${format.height})"
                                if (qualityList.none { it.height == format.height }) {
                                    qualityList.add(
                                        VideoQualityTrack(
                                            label = label,
                                            width = format.width,
                                            height = format.height,
                                            bitrate = format.bitrate
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                availableQualities = qualityList.sortedByDescending { it.height }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = "تعذر تشغيل البث (${error.errorCodeName}): ${error.localizedMessage ?: "انقطاع الاتصال بالسيرفر"}"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val toggleLandscape = {
        isFullScreenLandscape = !isFullScreenLandscape
        activity?.requestedOrientation = if (isFullScreenLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("native_media3_player_screen")
    ) {
        // Top Header Bar (Hidden when in Fullscreen Landscape mode unless tapped)
        if (!isFullScreenLandscape) {
            Surface(
                color = StudioSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                exoPlayer.stop()
                                onBack()
                            },
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = NeonCyan
                            )
                        }

                        if (!activeChannel?.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = activeChannel?.logoUrl,
                                contentDescription = activeChannel?.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioSurfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeChannel?.name ?: "مشغل Media3 ExoPlayer 1.5.1",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (playbackError == null) EmeraldGreen else CrimsonError)
                                )
                                Text(
                                    text = "${activeChannel?.category ?: "عام"} • Media3 1.5.1 Native",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showQualitySheet = true },
                            modifier = Modifier.testTag("player_quality_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HighQuality,
                                contentDescription = "جودة الفيديو",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = toggleLandscape,
                            modifier = Modifier.testTag("player_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "ملء الشاشة",
                                tint = NeonCyan
                            )
                        }
                    }
                }
            }
        }

        // Main Video Surface Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isFullScreenLandscape) 1f else 0.48f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("exo_player_surface_view")
            )

            // Buffering Indicator Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isBuffering && playbackError == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "جاري التحميل والتخزين المؤقت للبث المباشر...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = activeChannel?.streamFormat ?: "HLS / DASH / MP4",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan
                        )
                    }
                }
            }

            // Playback Error & Auto-Retry Banner
            androidx.compose.animation.AnimatedVisibility(
                visible = playbackError != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    color = StudioSurface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonError),
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "خطأ في البث",
                            tint = CrimsonError,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playbackError ?: "تعذر تشغيل البث المباشر",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        if (autoRetryEnabled && retryCount < 3) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "محاولة إعادة الاتصال التلقائي ($retryCount / 3)...",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberWarning
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    playbackError = null
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إعادة المحاولة الآن", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            if (availableServers.size > 1) {
                                OutlinedButton(
                                    onClick = {
                                        selectedServerIndex = (selectedServerIndex + 1) % availableServers.size
                                    }
                                ) {
                                    Text("تبديل السيرفر", color = NeonCyan)
                                }
                            }
                        }
                    }
                }
            }

            // Fullscreen Top Floating Overlay when in Landscape
            if (isFullScreenLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = toggleLandscape) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "خروج من ملء الشاشة", tint = Color.White)
                        }
                        Text(
                            text = activeChannel?.name ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row {
                        IconButton(onClick = { showChannelDrawer = !showChannelDrawer }) {
                            Icon(Icons.Default.Tv, contentDescription = "قائمة القنوات", tint = NeonCyan)
                        }
                        IconButton(onClick = toggleLandscape) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "تصغير الشاشة", tint = NeonCyan)
                        }
                    }
                }
            }
        }

        // Custom Studio Controls, Volume, Server Selector & Channel Switcher (Visible in Portrait/Standard mode)
        if (!isFullScreenLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Transport & Volume Row
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Play / Pause Button
                        FilledIconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonCyan),
                            modifier = Modifier.testTag("player_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.Black
                            )
                        }

                        // Volume Slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else volumeLevel
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMuted || volumeLevel == 0f) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = "الصوت",
                                    tint = NeonCyan
                                )
                            }
                            Slider(
                                value = if (isMuted) 0f else volumeLevel,
                                onValueChange = { newVal ->
                                    isMuted = false
                                    volumeLevel = newVal
                                    exoPlayer.volume = newVal
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (newVal * max).toInt(),
                                        0
                                    )
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Quality Chip
                        AssistChip(
                            onClick = { showQualitySheet = true },
                            label = { Text(selectedQualityLabel, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = NeonCyan
                                )
                            }
                        )
                    }
                }

                // Multi-Server Selector (If available)
                if (availableServers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "سيرفرات البث المتوفرة للقناة:",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableServers.size) { index ->
                                val (serverTitle, _) = availableServers[index]
                                val isSelected = index == selectedServerIndex
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedServerIndex = index },
                                    label = { Text(serverTitle) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Dns,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricViolet,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Channel Selection List inside Native Player
                Text(
                    text = "قائمة القنوات المدمجة (${channels.size} قناة):",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(channels, key = { "${it.id}_${it.name}" }) { channel ->
                        val isCurrent = channel.streamUrl == activeChannel?.streamUrl && channel.name == activeChannel?.name
                        Surface(
                            color = if (isCurrent) StudioSurfaceVariant else StudioSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrent) NeonCyan else StudioCardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeChannel = channel
                                    selectedServerIndex = 0
                                    retryCount = 0
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = channel.logoUrl.ifBlank { "https://picsum.photos/seed/${channel.name.hashCode()}/100/100" },
                                    contentDescription = channel.name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(StudioBackground),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCurrent) NeonCyan else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${channel.category} • ${channel.streamFormat}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                if (isCurrent) {
                                    Badge(containerColor = EmeraldGreen) {
                                        Text("يعمل الآن", color = Color.Black, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleFilled,
                                        contentDescription = "تشغيل",
                                        tint = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Video Quality Selection Dialog
    if (showQualitySheet) {
        AlertDialog(
            onDismissRequest = { showQualitySheet = false },
            containerColor = StudioSurface,
            title = {
                Text("اختيار جودة البث (Media3 TrackSelector)", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableQualities.forEach { track ->
                        Surface(
                            color = StudioSurfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (track.isAuto) {
                                        trackSelector.parameters = trackSelector
                                            .buildUponParameters()
                                            .clearVideoSizeConstraints()
                                            .build()
                                        selectedQualityLabel = "تلقائي (Auto)"
                                    } else {
                                        trackSelector.parameters = trackSelector
                                            .buildUponParameters()
                                            .setMaxVideoSize(track.width, track.height)
                                            .build()
                                        selectedQualityLabel = "${track.height}p"
                                    }
                                    showQualitySheet = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track.label, color = TextPrimary)
                                if (selectedQualityLabel.contains(track.height.toString()) || (track.isAuto && selectedQualityLabel.contains("Auto"))) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualitySheet = false }) {
                    Text("إغلاق", color = NeonCyan)
                }
            }
        )
    }
}
