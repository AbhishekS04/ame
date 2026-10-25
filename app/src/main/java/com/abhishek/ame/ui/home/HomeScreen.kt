package com.abhishek.ame.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhishek.ame.R
import com.abhishek.ame.data.CatalogPlaylist
import com.abhishek.ame.data.CatalogTrack
import com.abhishek.ame.data.SearchItem
import com.abhishek.ame.ui.MainViewModel
import com.abhishek.ame.ui.components.AppGlyph
import com.abhishek.ame.ui.components.Artwork
import com.abhishek.ame.ui.components.FilterChip
import com.abhishek.ame.ui.glass.LiquidButton
import com.abhishek.ame.ui.glass.LocalGlassEffectConfig
import com.abhishek.ame.ui.glass.ProgressiveBlur
import com.abhishek.ame.ui.glass.ProgressiveBlurPosition
import com.abhishek.ame.ui.glass.backdrop.Backdrop
import com.abhishek.ame.ui.glass.liquidGlass
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import com.abhishek.ame.ui.theme.softShadow
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.BookmarkSimple
import com.adamglin.phosphoricons.fill.Pause
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.regular.BookmarkSimple
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.DotsThree
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.User
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.abhishek.ame.ui.components.ProfilePicture
import java.io.File
import java.util.Calendar

private const val FADE_FRACTION = 0.02f
private const val QUICK_PICK_ROWS = 4
private const val QUICK_PICK_MINIMUM = 4

/**
 * Filter categories for Spotify mode.
 */
private enum class Feed(@StringRes val label: Int, val chip: com.abhishek.ame.data.TabContents.Chip? = null) {
    ALL(R.string.category_all, null),
    MUSIC(R.string.category_music, com.abhishek.ame.data.TabContents.Chip.FOR_YOU),
    PODCASTS(R.string.podcasts, null),
}

private val PLAYFUL_GREETINGS = listOf(
    "Howdy",
    "Hola",
    "Namaste",
    "Hey there",
    "Hello",
    "Welcome back",
)

private fun greetingText(accountName: String?): String {
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val hourChunk = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 4
    val greetingIndex = Math.floorMod(dayOfYear * 7 + hourChunk, PLAYFUL_GREETINGS.size)
    val playfulGreeting = PLAYFUL_GREETINGS[greetingIndex]

    val firstName = accountName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
    return if (!firstName.isNullOrBlank()) {
        "$playfulGreeting, $firstName"
    } else {
        "$playfulGreeting!"
    }
}

/**
 * Top Navbar:
 * - Clean typography without enclosing tab-bar-like pill/border background.
 * - Left: Dynamic time-of-day greeting (with user's first name if logged in).
 * - Right: Liquid glass circular profile icon button.
 */
@Composable
private fun HomeTopNavbar(
    accountName: String?,
    backdrop: Backdrop,
    topPadding: Dp,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    customAvatarPath: String? = null,
    customAvatarCloudUrl: String? = null,
    defaultAvatarUrl: String? = null,
) {
    val greeting = remember(accountName) { greetingText(accountName) }
    val context = LocalContext.current
    val statusBarInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopPadding = maxOf(topPadding, maxOf(statusBarInsets, cutoutInsets)) + 12.dp

    val avatarModel = remember(customAvatarPath, customAvatarCloudUrl, defaultAvatarUrl) {
        when {
            customAvatarPath != null && File(customAvatarPath).exists() -> File(customAvatarPath)
            !customAvatarCloudUrl.isNullOrBlank() -> customAvatarCloudUrl
            !defaultAvatarUrl.isNullOrBlank() -> defaultAvatarUrl
            else -> null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = safeTopPadding, start = 20.dp, end = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.3).sp,
            ),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        LiquidButton(
            onClick = onOpenProfile,
            backdrop = backdrop,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
                .border(0.75.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentHeight = 40.dp,
            contentPadding = 0.dp,
        ) {
            if (avatarModel != null) {
                val avatarRequest = remember(avatarModel) {
                    ImageRequest.Builder(context)
                        .data(avatarModel)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = avatarRequest,
                    contentDescription = stringResource(R.string.profile),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                ProfilePicture(
                    url = null,
                    name = accountName,
                    size = 40.dp,
                    decodeSize = 40.dp,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            }
        }
    }
}

/**
 * Animated dynamic equalizer badge with 4 bouncing bars.
 */
@Composable
fun AnimatedEqualizerBadge(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "equalizerTransition")
    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar1",
    )
    val h2 by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar2",
    )
    val h3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar3",
    )
    val h4 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(390, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar4",
    )

    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFF101010).copy(alpha = 0.85f))
            .border(0.6.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val barCount = 4
            val spacing = 2.dp.toPx()
            val totalSpacing = spacing * (barCount - 1)
            val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(1.5f)
            val maxHeight = size.height * 0.85f
            val heights = listOf(h1, h2, h3, h4)

            heights.forEachIndexed { i, frac ->
                val barH = (maxHeight * frac).coerceAtLeast(2.5f)
                val left = i * (barWidth + spacing)
                val top = (size.height - barH) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
    }
}

/**
 * Filter Chips row matching YouTube Music categories with 'All' at the front.
 */
@Composable
private fun ChipRow(
    chips: List<com.abhishek.ame.backend.HomeChip>,
    selected: String?,
    backdrop: Backdrop,
    onPick: (com.abhishek.ame.backend.HomeChip?) -> Unit,
) {
    val allChip = remember { com.abhishek.ame.backend.HomeChip(title = "All", params = null) }
    val displayChips = remember(chips) {
        listOf(allChip) + chips.filterNot { it.title.equals("All", ignoreCase = true) }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
    ) {
        items(displayChips, key = { it.title }) { chip ->
            val isSelected = if (chip.title == "All") {
                selected == null || selected == "All"
            } else {
                chip.title == selected
            }
            FilterChip(
                label = chip.title,
                selected = isSelected,
                onClick = {
                    if (chip.title == "All") {
                        onPick(null)
                    } else {
                        onPick(if (isSelected) null else chip)
                    }
                },
            )
        }
    }
}

/**
 * Section Heading with optional strapline and title.
 */
@Composable
private fun Heading(text: String, strapline: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
        strapline?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Quick Picks: 4 tracks per column, swipeable horizontally with snap fling.
 */
@Composable
private fun QuickPicks(
    tracks: List<CatalogTrack>,
    currentTrackUri: String?,
    isPlaying: Boolean,
    playingColor: Color,
    onPlay: (Int) -> Unit,
) {
    val columns = tracks.chunked(QUICK_PICK_ROWS)
    val width = LocalConfiguration.current.screenWidthDp.dp - 44.dp
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(rowState),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        itemsIndexed(columns, key = { _, column -> column.first().uri }) { columnIndex, column ->
            Column(
                Modifier.width(width),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                column.forEachIndexed { rowIndex, track ->
                    val isPlayingThis = isPlaying && (currentTrackUri == track.uri)
                    QuickPickRow(
                        track = track,
                        isPlayingThis = isPlayingThis,
                        playingColor = playingColor,
                    ) {
                        onPlay(columnIndex * QUICK_PICK_ROWS + rowIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickRow(
    track: CatalogTrack,
    isPlayingThis: Boolean = false,
    playingColor: Color = Color(0xFF1ED760),
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = track.artworkUrl,
                title = track.name,
                modifier = Modifier.fillMaxSize(),
                corner = 10.dp,
                decodeSize = 52.dp,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlayingThis,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp),
            ) {
                AnimatedEqualizerBadge(
                    color = playingColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                track.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlayingThis) playingColor else Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Playlist or Album Tile in a horizontal shelf.
 * Supports custom banners, bookmark save to library, and dynamic equalizer animation badge.
 */
@Composable
private fun PlaylistTile(
    playlist: CatalogPlaylist,
    isPlayingThis: Boolean = false,
    playingColor: Color = Color(0xFF1ED760),
    isSaved: Boolean = false,
    onSave: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val corner = if (playlist.isArtist) 76.dp else 20.dp
    Column(
        Modifier
            .width(152.dp)
            .pressable(onClick),
    ) {
        Box(modifier = Modifier.size(152.dp)) {
            Artwork(
                url = playlist.artworkUrl,
                title = playlist.name,
                modifier = Modifier
                    .fillMaxSize()
                    .softShadow(RoundedCornerShape(corner), elevation = 18.dp),
                corner = corner,
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isPlayingThis,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                AnimatedEqualizerBadge(color = playingColor)
            }

            if (onSave != null && !playlist.isArtist) {
                val bookmarkShape = CircleShape
                val haptic = LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(bookmarkShape)
                        .background(Color(0xFF121212).copy(alpha = 0.65f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.25f), bookmarkShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSave()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isSaved) PhosphorIcons.Fill.BookmarkSimple else PhosphorIcons.Regular.BookmarkSimple,
                        contentDescription = stringResource(R.string.save_to_library),
                        tint = if (isSaved) Color(0xFF1ED760) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = if (playlist.subtitle == null) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        playlist.subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

/**
 * Track Tile in a horizontal shelf.
 */
@Composable
private fun TrackTile(
    track: CatalogTrack,
    isPlayingThis: Boolean = false,
    playingColor: Color = Color(0xFF1ED760),
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(136.dp)
            .pressable(onClick),
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            Artwork(
                url = track.artworkUrl,
                title = track.name,
                modifier = Modifier
                    .fillMaxSize()
                    .softShadow(RoundedCornerShape(18.dp), elevation = 16.dp),
                corner = 18.dp,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlayingThis,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                AnimatedEqualizerBadge(color = playingColor)
            }
        }
        Text(
            track.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (isPlayingThis) playingColor else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = InkDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun TrackRow(
    tracks: List<CatalogTrack>,
    currentTrackUri: String?,
    isPlaying: Boolean,
    playingColor: Color,
    onPlay: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
            val isPlayingThis = isPlaying && (currentTrackUri == track.uri)
            TrackTile(
                track = track,
                isPlayingThis = isPlayingThis,
                playingColor = playingColor,
            ) { onPlay(index) }
        }
    }
}



/**
 * YouTube Music Home Page:
 * Decluttered & Clean:
 * - Liquid glass navbar with time-of-day greeting (and name if logged in) + liquid glass profile button
 * - Filter chips (Podcasts, Romance, Feel good, Relax, Energize, Workout, Party)
 * - When on main feed: ONLY Quick Picks / Daily Discovery, New Releases, and My History (Played earlier)!
 * - When a filter chip is tapped: dedicated rows for that category.
 */
@Composable
private fun YouTubeHome(
    contentPadding: PaddingValues,
    home: MainViewModel.YouTubeHomeState,
    sourceNew: List<com.abhishek.ame.backend.HomeRow> = emptyList(),
    recent: List<CatalogTrack>,
    accountName: String?,
    backdrop: Backdrop,
    onPlayRecent: (List<CatalogTrack>, Int) -> Unit,
    onPlayTrending: (List<CatalogTrack>, Int) -> Unit,
    onOpenPlaylist: (CatalogPlaylist) -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onSwitchSource: () -> Unit,
    onPickChip: (com.abhishek.ame.backend.HomeChip?) -> Unit,
    onLoadMore: () -> Unit,
    signedIn: Boolean,
    onSignIn: () -> Unit,
    currentTrackUri: String? = null,
    currentContextUri: String? = null,
    isPlaying: Boolean = false,
    playingColor: Color = Color(0xFF1ED760),
    customBanners: Map<String, String> = emptyMap(),
    savedPlaylists: List<CatalogPlaylist> = emptyList(),
    onSavePlaylist: (CatalogPlaylist) -> Unit = {},
    customAvatarPath: String? = null,
    customAvatarCloudUrl: String? = null,
    defaultAvatarUrl: String? = null,
) {
    val listState = rememberLazyListState()

    // Deduplicate shelves by title to avoid repeated rows
    val distinctRows = remember(home.rows) {
        val seen = mutableSetOf<String>()
        home.rows.filter { row ->
            val key = row.title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            if (key.isEmpty()) false else seen.add(key)
        }
    }

    val isPlaylistSaved: (String) -> Boolean = remember(savedPlaylists) {
        { uri -> savedPlaylists.any { it.uri == uri } }
    }

    // Default chips list if YouTube chips are empty initially
    val displayChips = remember(home.chips) {
        if (home.chips.isNotEmpty()) {
            home.chips
        } else {
            listOf("Podcasts", "Romance", "Feel good", "Relax", "Energize", "Workout", "Party").map {
                com.abhishek.ame.backend.HomeChip(title = it, params = null)
            }
        }
    }

    // If a filter chip is active, enable infinite scrolling
    val isCategoryFiltered = home.chip != null
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            isCategoryFiltered && total > 0 && last >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && home.hasMore && !home.loadingMore) {
            onLoadMore()
        }
    }

    // Ensure home feed always starts cleanly at the top
    LaunchedEffect(Unit) {
        listState.scrollToItem(0, 0)
    }
    LaunchedEffect(distinctRows.isNotEmpty()) {
        if (distinctRows.isNotEmpty()) {
            listState.scrollToItem(0, 0)
        }
    }
    LaunchedEffect(home.chip) {
        listState.scrollToItem(0, 0)
    }

    val topPadding = contentPadding.calculateTopPadding()
    val statusBarInsets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTop = maxOf(topPadding, maxOf(statusBarInsets, cutoutInsets)) + 12.dp

    // Pixel-perfect header height: perfectly clears status bar & punch-hole camera,
    // and eliminates huge empty gap above Quick Picks
    val headerHeight = safeTop + 96.dp
    val blurHeight = safeTop + 92.dp

    Box(Modifier.fillMaxSize()) {
        if (distinctRows.isEmpty() && (home.loading || home.rows.isEmpty()) && home.error == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = headerHeight),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
            }
        } else if (isCategoryFiltered) {
            // Category filtered feed (dedicated shelves for selected mood/genre)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = headerHeight,
                    bottom = contentPadding.calculateBottomPadding() + 80.dp,
                ),
            ) {
                distinctRows.forEachIndexed { index, row ->
                    item(key = "filter-head-$index-${row.title}") {
                        Heading(row.title, row.strapline)
                    }

                    if (row.tracks.isNotEmpty()) {
                        item(key = "filter-tracks-$index-${row.title}") {
                            if (row.tracks.size >= QUICK_PICK_MINIMUM) {
                                QuickPicks(
                                    tracks = row.tracks,
                                    currentTrackUri = currentTrackUri,
                                    isPlaying = isPlaying,
                                    playingColor = playingColor,
                                ) { i ->
                                    onPlayTrending(row.tracks, i)
                                }
                            } else {
                                TrackRow(
                                    tracks = row.tracks,
                                    currentTrackUri = currentTrackUri,
                                    isPlaying = isPlaying,
                                    playingColor = playingColor,
                                ) { i ->
                                    onPlayTrending(row.tracks, i)
                                }
                            }
                        }
                    }

                    if (row.items.isNotEmpty()) {
                        item(key = "filter-items-$index-${row.title}") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(top = 14.dp),
                            ) {
                                items(row.items, key = { it.uri }) { entry ->
                                    val isPlayingThis = isPlaying && (currentContextUri == entry.uri || currentTrackUri == entry.uri)
                                    val isSaved = isPlaylistSaved(entry.uri)
                                    val bannerUrl = customBanners[entry.uri] ?: entry.artworkUrl
                                    PlaylistTile(
                                        playlist = entry.copy(artworkUrl = bannerUrl),
                                        isPlayingThis = isPlayingThis,
                                        playingColor = playingColor,
                                        isSaved = isSaved,
                                        onSave = { onSavePlaylist(entry) },
                                        onClick = { onOpenPlaylist(entry) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (home.loadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Ink,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        } else {
            // Main Home Screen: STRICTLY DECLUTTERED
            // 1. Daily Discovery (if present)
            // 2. Quick Picks (The 4-item column horizontal snap carousel)
            // 3. New releases (Standard horizontal carousel)
            // 4. Play again / My History

            val dailyDiscoverRow = distinctRows.find {
                it.title.contains("daily discover", ignoreCase = true) ||
                it.title.contains("discovery", ignoreCase = true)
            }

            val quickPicksRow = distinctRows.find {
                it.title.contains("Quick picks", ignoreCase = true) ||
                it.title.contains("Top picks", ignoreCase = true)
            } ?: distinctRows.firstOrNull { it !== dailyDiscoverRow && it.tracks.size >= 4 }

            val quickPicksTracks = quickPicksRow?.tracks.orEmpty()

            val newReleasesRow = distinctRows.find {
                it !== dailyDiscoverRow && it !== quickPicksRow && (
                    it.title.contains("New", ignoreCase = true) ||
                    it.title.contains("Release", ignoreCase = true) ||
                    it.title.contains("Fresh", ignoreCase = true)
                )
            } ?: sourceNew.firstOrNull { it.items.isNotEmpty() || it.tracks.isNotEmpty() }
              ?: distinctRows.firstOrNull { it !== dailyDiscoverRow && it !== quickPicksRow && (it.items.isNotEmpty() || it.tracks.isNotEmpty()) }

            val newReleasesTitle = remember(newReleasesRow) {
                newReleasesRow?.title?.takeIf { it.isNotBlank() } ?: "New releases"
            }
            val newReleasesStrapline = remember(newReleasesRow) {
                newReleasesRow?.strapline ?: "FRESH MUSIC"
            }

            val newReleaseItems = remember(sourceNew, newReleasesRow) {
                (newReleasesRow?.items.orEmpty() + sourceNew.flatMap { it.items }).distinctBy { it.uri }
            }
            val newReleaseTracks = remember(newReleasesRow, sourceNew) {
                (newReleasesRow?.tracks.orEmpty() + sourceNew.flatMap { it.tracks }).distinctBy { it.uri }
            }

            val historyTracks = remember(recent, home.rows) {
                if (recent.isNotEmpty()) recent
                else home.rows.find {
                    it.title.contains("Listen again", ignoreCase = true) ||
                    it.title.contains("Play again", ignoreCase = true) ||
                    it.title.contains("history", ignoreCase = true)
                }?.tracks.orEmpty()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = headerHeight,
                    bottom = contentPadding.calculateBottomPadding() + 80.dp,
                ),
            ) {
                // 1. Daily Discovery (if present)
                if (dailyDiscoverRow != null && dailyDiscoverRow.tracks.isNotEmpty()) {
                    item(key = "daily_discover_head") {
                        Heading(dailyDiscoverRow.title, dailyDiscoverRow.strapline ?: "DAILY DISCOVERY")
                    }
                    item(key = "daily_discover_tracks") {
                        if (dailyDiscoverRow.tracks.size >= QUICK_PICK_MINIMUM) {
                            QuickPicks(
                                tracks = dailyDiscoverRow.tracks.take(16),
                                currentTrackUri = currentTrackUri,
                                isPlaying = isPlaying,
                                playingColor = playingColor,
                            ) { i ->
                                onPlayTrending(dailyDiscoverRow.tracks, i)
                            }
                        } else {
                            TrackRow(
                                tracks = dailyDiscoverRow.tracks.take(16),
                                currentTrackUri = currentTrackUri,
                                isPlaying = isPlaying,
                                playingColor = playingColor,
                            ) { i ->
                                onPlayTrending(dailyDiscoverRow.tracks, i)
                            }
                        }
                    }
                }

                // 2. Quick Picks (The 4-item column horizontal snap carousel)
                if (quickPicksTracks.isNotEmpty()) {
                    item(key = "quick_picks_head") {
                        val quickPicksTitle = if (quickPicksRow?.title?.contains("pick", ignoreCase = true) == true) {
                            quickPicksRow.title
                        } else {
                            "Quick picks"
                        }
                        Heading(quickPicksTitle, "START RADIO FROM A SONG")
                    }
                    item(key = "quick_picks_tracks") {
                        QuickPicks(
                            tracks = quickPicksTracks.take(16),
                            currentTrackUri = currentTrackUri,
                            isPlaying = isPlaying,
                            playingColor = playingColor,
                        ) { i ->
                            onPlayTrending(quickPicksTracks, i)
                        }
                    }
                }

                // 3. New releases / Featured Music (Standard horizontal carousel)
                if (newReleaseItems.isNotEmpty()) {
                    item(key = "new_releases_head") {
                        Heading(newReleasesTitle, newReleasesStrapline)
                    }
                    item(key = "new_releases_items") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            items(newReleaseItems.take(15), key = { it.uri }) { entry ->
                                val isPlayingThis = isPlaying && (currentContextUri == entry.uri || currentTrackUri == entry.uri)
                                val isSaved = isPlaylistSaved(entry.uri)
                                val bannerUrl = customBanners[entry.uri] ?: entry.artworkUrl
                                PlaylistTile(
                                    playlist = entry.copy(artworkUrl = bannerUrl),
                                    isPlayingThis = isPlayingThis,
                                    playingColor = playingColor,
                                    isSaved = isSaved,
                                    onSave = { onSavePlaylist(entry) },
                                    onClick = { onOpenPlaylist(entry) },
                                )
                            }
                        }
                    }
                } else if (newReleaseTracks.isNotEmpty()) {
                    item(key = "new_releases_head") {
                        Heading(newReleasesTitle, newReleasesStrapline)
                    }
                    item(key = "new_releases_tracks") {
                        TrackRow(
                            tracks = newReleaseTracks.take(15),
                            currentTrackUri = currentTrackUri,
                            isPlaying = isPlaying,
                            playingColor = playingColor,
                        ) { i ->
                            onPlayTrending(newReleaseTracks, i)
                        }
                    }
                }

                // 4. Play again / My History
                if (historyTracks.isNotEmpty()) {
                    item(key = "history_head") {
                        Heading(stringResource(R.string.play_again), "MY HISTORY")
                    }
                    item(key = "history_tracks") {
                        TrackRow(
                            tracks = historyTracks.take(16),
                            currentTrackUri = currentTrackUri,
                            isPlaying = isPlaying,
                            playingColor = playingColor,
                        ) { i ->
                            onPlayRecent(historyTracks, i)
                        }
                    }
                }
            }
        }

        // Magic UI Progressive Blur: Anchored at top, fading smoothly downward over scrolling content
        ProgressiveBlur(
            height = blurHeight,
            position = ProgressiveBlurPosition.Top,
            backdrop = backdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Top Header: Greeting row + Filter Chips row (floating cleanly over the progressive blur)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) {
            HomeTopNavbar(
                accountName = accountName,
                backdrop = backdrop,
                topPadding = topPadding,
                onOpenProfile = onOpenProfile,
                customAvatarPath = customAvatarPath,
                customAvatarCloudUrl = customAvatarCloudUrl,
                defaultAvatarUrl = defaultAvatarUrl,
            )

            ChipRow(
                chips = displayChips,
                selected = home.chip,
                backdrop = backdrop,
                onPick = onPickChip,
            )
        }
    }
}

/**
 * Main HomeScreen entry point.
 */
@Composable
fun HomeScreen(
    state: MainViewModel.UiState,
    contentPadding: PaddingValues,
    onLogIn: () -> Unit,
    onRetry: () -> Unit,
    onGoOffline: () -> Unit = {},
    onLogOut: () -> Unit,
    onOpenPlaylist: (CatalogPlaylist) -> Unit,
    playlistOrder: List<String>,
    recent: List<CatalogTrack>,
    onPlayRecent: (List<CatalogTrack>, Int) -> Unit,
    onPlayFeed: (List<CatalogTrack>, Int, String) -> Unit,
    feed: MainViewModel.FeedState,
    onOpenItem: (SearchItem) -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenSettings: () -> Unit,
    friends: List<com.abhishek.ame.data.FriendListen> = emptyList(),
    onOpenFriends: () -> Unit = {},
    backdrop: Backdrop,
    youtubeMode: Boolean = false,
    onRetryOnline: (suspend () -> Boolean)? = null,
    youtubeHome: MainViewModel.YouTubeHomeState = MainViewModel.YouTubeHomeState(),
    sourceNew: List<com.abhishek.ame.backend.HomeRow> = emptyList(),
    userPlaylists: List<CatalogPlaylist> = emptyList(),
    onPlayTrending: (List<CatalogTrack>, Int) -> Unit = { _, _ -> },
    onPickYouTubeChip: (com.abhishek.ame.backend.HomeChip?) -> Unit = {},
    onLoadMoreYouTube: () -> Unit = {},
    youtubeSignedIn: Boolean = true,
    onYouTubeSignIn: () -> Unit = {},
    onUseYouTube: () -> Unit = {},
    onSwitchSource: () -> Unit = {},
    shelves: List<com.abhishek.ame.data.HomeShelf> = emptyList(),
    shelvesLoading: Boolean = false,
    podcastShelves: List<com.abhishek.ame.data.HomeShelf> = emptyList(),
    podcastsLoading: Boolean = false,
    onWantPodcasts: () -> Unit = {},
    onPlayEpisode: (CatalogPlaylist, String) -> Unit = { _, _ -> },
    currentTrackUri: String? = null,
    currentContextUri: String? = null,
    isPlaying: Boolean = false,
    playingColor: Color = Color(0xFF1ED760),
    customBanners: Map<String, String> = emptyMap(),
    savedPlaylists: List<CatalogPlaylist> = emptyList(),
    onSavePlaylist: (CatalogPlaylist) -> Unit = {},
    accountName: String? = null,
    customAvatarPath: String? = null,
    customAvatarCloudUrl: String? = null,
    defaultAvatarUrl: String? = null,
) {
    if (youtubeMode) {
        YouTubeHome(
            contentPadding = contentPadding,
            home = youtubeHome,
            sourceNew = sourceNew,
            recent = recent,
            accountName = accountName,
            backdrop = backdrop,
            onPlayRecent = onPlayRecent,
            onPlayTrending = onPlayTrending,
            onOpenPlaylist = onOpenPlaylist,
            onOpenProfile = onOpenProfile,
            onOpenSettings = onOpenSettings,
            onSwitchSource = onSwitchSource,
            onPickChip = onPickYouTubeChip,
            onLoadMore = onLoadMoreYouTube,
            signedIn = youtubeSignedIn,
            onSignIn = onYouTubeSignIn,
            currentTrackUri = currentTrackUri,
            currentContextUri = currentContextUri,
            isPlaying = isPlaying,
            playingColor = playingColor,
            customBanners = customBanners,
            savedPlaylists = savedPlaylists,
            onSavePlaylist = onSavePlaylist,
            customAvatarPath = customAvatarPath,
            customAvatarCloudUrl = customAvatarCloudUrl,
            defaultAvatarUrl = defaultAvatarUrl,
        )
        return
    }

    when (state) {
        MainViewModel.UiState.LoggedOut -> Centered {
            AppGlyph(64.dp)
            Text(
                stringResource(R.string.unofficial_client),
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            GlassAction(stringResource(R.string.log_in_with_spotify), backdrop, onLogIn)
            com.abhishek.ame.ui.components.SignedOutOptions(onUseYouTube, onOpenSettings)
        }

        MainViewModel.UiState.Connecting,
        MainViewModel.UiState.Loading,
        -> com.abhishek.ame.ui.components.ConnectingState(backdrop, onGoOffline)

        is MainViewModel.UiState.Failed -> Centered {
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            GlassAction(stringResource(R.string.retry), backdrop, onRetry)
            GlassAction(stringResource(R.string.log_out), backdrop, onLogOut)
        }

        is MainViewModel.UiState.Ready -> {
            var filterName by rememberSaveable { mutableStateOf(Feed.ALL.name) }
            val activeFeed = Feed.entries.find { it.name == filterName } ?: Feed.ALL

            Column(Modifier.fillMaxSize()) {
                HomeTopNavbar(
                    accountName = accountName ?: state.displayName,
                    backdrop = backdrop,
                    topPadding = contentPadding.calculateTopPadding(),
                    onOpenProfile = onOpenProfile,
                    customAvatarPath = customAvatarPath,
                    customAvatarCloudUrl = customAvatarCloudUrl,
                    defaultAvatarUrl = defaultAvatarUrl ?: state.avatarUrl,
                )

                // Chips row
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                ) {
                    items(Feed.entries.toList(), key = { it.name }) { entry ->
                        FilterChip(
                            label = stringResource(entry.label),
                            selected = entry == activeFeed,
                            onClick = { filterName = entry.name },
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 80.dp),
                ) {
                    shelves.forEachIndexed { index, shelf ->
                        item(key = "shelf-$index-${shelf.title}") {
                            Heading(shelf.title)
                        }
                        if (shelf.items.isNotEmpty()) {
                            item(key = "shelf-items-$index-${shelf.title}") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.padding(top = 14.dp),
                                ) {
                                    items(shelf.items, key = { it.uri }) { item ->
                                        PlaylistTile(
                                            playlist = item,
                                            isPlayingThis = isPlaying && (currentContextUri == item.uri || currentTrackUri == item.uri),
                                            playingColor = playingColor,
                                            onClick = { onOpenPlaylist(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One of the other tabs, for a source that lays its own pages out.
 */
@Composable
fun SourceRowsPage(
    title: String,
    rows: List<com.abhishek.ame.backend.HomeRow>,
    loading: Boolean,
    contentPadding: PaddingValues,
    onPlay: (List<CatalogTrack>, Int) -> Unit,
    onOpen: (CatalogPlaylist) -> Unit,
) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(R.string.nothing_here),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDim,
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
    ) {
        item(key = "title") {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp),
            )
        }

        rows.forEachIndexed { index, row ->
            item(key = "head-$index-${row.title}") { Heading(row.title, row.strapline) }
            if (row.tracks.isNotEmpty()) {
                item(key = "tracks-$index-${row.title}") {
                    if (row.tracks.size >= QUICK_PICK_MINIMUM) {
                        QuickPicks(
                            tracks = row.tracks,
                            currentTrackUri = null,
                            isPlaying = false,
                            playingColor = Color(0xFF1ED760),
                        ) { i -> onPlay(row.tracks, i) }
                    } else {
                        TrackRow(
                            tracks = row.tracks,
                            currentTrackUri = null,
                            isPlaying = false,
                            playingColor = Color(0xFF1ED760),
                        ) { i -> onPlay(row.tracks, i) }
                    }
                }
            }
            if (row.items.isNotEmpty()) {
                item(key = "items-$index-${row.title}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        items(row.items, key = { it.uri }) { entry ->
                            PlaylistTile(
                                playlist = entry,
                                onClick = { onOpen(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun GlassAction(label: String, backdrop: Backdrop, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        contentHeight = 50.dp,
        contentPadding = 26.dp,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}
