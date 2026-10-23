package dev.lelonio.square.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.lerp
import dev.lelonio.square.ui.glass.backdrop.Backdrop
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsLeftRight
import com.adamglin.phosphoricons.regular.Gear
import com.adamglin.phosphoricons.regular.SpotifyLogo
import com.adamglin.phosphoricons.regular.YoutubeLogo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import dev.lelonio.square.R
import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.data.SearchItem
import dev.lelonio.square.data.sortedByRecentlyOpened
import dev.lelonio.square.ui.MainViewModel
import dev.lelonio.square.ui.components.AppGlyph
import dev.lelonio.square.ui.components.AppLockup
import dev.lelonio.square.ui.components.Artwork
import dev.lelonio.square.ui.components.SquareWordmark
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import dev.lelonio.square.ui.glass.LiquidButton
import dev.lelonio.square.ui.glass.pressable
import dev.lelonio.square.ui.player.GlassFilm
import dev.lelonio.square.ui.theme.Ink
import dev.lelonio.square.ui.theme.InkDim
import dev.lelonio.square.ui.theme.softShadow
import java.util.Calendar

/**
 * What the feed is showing.
 *
 * Chips rather than a second tab bar: they sit inside the page, under the name,
 * and "Tutto" is genuinely the whole thing rather than a fourth view. The
 * sections keep their order in every one of them, so switching never rearranges
 * what stays on screen.
 *
 * Each chip is a part of Spotify's own home: what it made for the account,
 * what the editors put there, and the rows about an artist; see
 * TabContents.chipOf. There used to be a chip for the library and one for new
 * releases, which were two other tabs of this app said again on this one.
 */
private enum class Feed(@StringRes val label: Int, val chip: dev.lelonio.square.data.TabContents.Chip?) {
    ALL(R.string.feed_all, null),
    FOR_YOU(R.string.feed_for_you, dev.lelonio.square.data.TabContents.Chip.FOR_YOU),
    ARTISTS(R.string.artists, dev.lelonio.square.data.TabContents.Chip.ARTISTS),
    /**
     * Spotify's own podcast page rather than a part of this one: its shows
     * and episodes come from a home of their own, asked for when the chip is.
     */
    PODCASTS(R.string.podcasts, null),
}

/**
 * The home page.
 *
 * Rewritten from scratch rather than adjusted. The previous version had grown
 * from a plain Material list — filled buttons, section headings, a progress
 * spinner in the middle of the page — and adding glass cards on top of it left
 * two design languages sharing a screen. Everything here is drawn on the same
 * material as the bars and the player: no Material containers, no elevation, no
 * accent-filled buttons.
 */
@Composable
fun HomeScreen(
    state: MainViewModel.UiState,
    contentPadding: PaddingValues,
    onLogIn: () -> Unit,
    onRetry: () -> Unit,
    /** Off the network by choice, from the wait; see ConnectingState. */
    onGoOffline: () -> Unit = {},
    onLogOut: () -> Unit,
    onOpenPlaylist: (CatalogPlaylist) -> Unit,
    /** URIs most recently opened first; see PlaylistOrderStore. */
    playlistOrder: List<String>,
    recent: List<CatalogTrack>,
    onPlayRecent: (List<CatalogTrack>, Int) -> Unit,
    /** Plays a row of the feed, told which row it was so the player can say so. */
    onPlayFeed: (List<CatalogTrack>, Int, String) -> Unit,
    feed: MainViewModel.FeedState,
    onOpenItem: (SearchItem) -> Unit,
    onOpenSettings: () -> Unit,
    /** Friend activity, for the faces beside the account picture. */
    friends: List<dev.lelonio.square.data.FriendListen> = emptyList(),
    onOpenFriends: () -> Unit = {},
    /** The layer the glass on this page refracts; see the note in SquareApp. */
    backdrop: Backdrop,
    /**
     * True when the active source is YouTube Music.
     *
     * [state] describes the *Spotify* session, so on YouTube it is permanently
     * `LoggedOut` and this page would offer a login for an account the user
     * chose not to use. There is no account to log into here, so the page shows
     * what it genuinely has instead.
     */
    youtubeMode: Boolean = false,
    /** Tries the servers again from the offline banner; null hides the button. */
    onRetryOnline: (suspend () -> Boolean)? = null,
    youtubeHome: MainViewModel.YouTubeHomeState = MainViewModel.YouTubeHomeState(),
    onPlayTrending: (List<CatalogTrack>, Int) -> Unit = { _, _ -> },
    /** One of YouTube's own filters above the page, or null to clear it. */
    onPickYouTubeChip: (dev.lelonio.square.backend.HomeChip?) -> Unit = {},
    /** Called from the bottom of the page: the next shelves, if there are any. */
    onLoadMoreYouTube: () -> Unit = {},
    /** Whether a Google account is signed in; the page suggests one otherwise. */
    youtubeSignedIn: Boolean = true,
    onYouTubeSignIn: () -> Unit = {},
    /** Signed out of Spotify: play from the other source instead. */
    onUseYouTube: () -> Unit = {},
    onSwitchSource: () -> Unit = {},
    /**
     * Spotify's own personalised shelves, empty when the gateway said nothing.
     *
     * Shown above the rows this app builds itself. They are what the listener
     * recognises as their home page, and unlike everything below them they
     * cannot be reconstructed from the account's playlists.
     */
    shelves: List<dev.lelonio.square.data.HomeShelf> = emptyList(),
    /** Spotify's shelves are on their way; their outline stands in until then. */
    shelvesLoading: Boolean = false,
    /** Spotify's podcast home, for the chip of it; asked for with [onWantPodcasts]. */
    podcastShelves: List<dev.lelonio.square.data.HomeShelf> = emptyList(),
    podcastsLoading: Boolean = false,
    onWantPodcasts: () -> Unit = {},
    /** An episode tile: played, not opened. */
    onPlayEpisode: (CatalogPlaylist, String) -> Unit = { _, _ -> },
) {
    if (youtubeMode) {
        YouTubeHome(
            contentPadding = contentPadding,
            home = youtubeHome,
            recent = recent,
            // Nobody's name while signed out: the library's answer outlived a
            // sign-out, and the header went on naming an account the app was
            // no longer in.
            accountName = (state as? MainViewModel.UiState.Ready)?.displayName.orEmpty()
                .takeIf { youtubeSignedIn }.orEmpty(),
            backdrop = backdrop,
            onPlayRecent = onPlayRecent,
            onPlayTrending = onPlayTrending,
            onOpenPlaylist = onOpenPlaylist,
            onOpenSettings = onOpenSettings,
            onSwitchSource = onSwitchSource,
            onPickChip = onPickYouTubeChip,
            onLoadMore = onLoadMoreYouTube,
            signedIn = youtubeSignedIn,
            onSignIn = onYouTubeSignIn,
        )
        return
    }

    when (state) {
        MainViewModel.UiState.LoggedOut -> Centered {
            AppGlyph(64.dp)
            SquareWordmark(height = 28.dp)
            Text(
                stringResource(R.string.unofficial_client),
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            GlassAction(stringResource(R.string.log_in_with_spotify), backdrop, onLogIn)
            dev.lelonio.square.ui.components.SignedOutOptions(onUseYouTube, onOpenSettings)
        }

        MainViewModel.UiState.Connecting,
        MainViewModel.UiState.Loading,
        -> dev.lelonio.square.ui.components.ConnectingState(backdrop, onGoOffline)

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
            // Saved rather than remembered, like the scroll position below it:
            // both are undone by leaving for a playlist and coming back, and a
            // page that forgets which shelf you were reading is the same
            // complaint as one that forgets where you were in it.
            var filterName by rememberSaveable { mutableStateOf(Feed.ALL.name) }
            // Only the chips that have something under them, and none at all
            // when Spotify's home did not answer: the page is then this app's
            // own sections, which is one view with nothing to filter.
            val chips = remember(shelves) {
                listOf(Feed.ALL) + Feed.entries.filter { entry ->
                    entry.chip != null &&
                        shelves.any { dev.lelonio.square.data.TabContents.chipOf(it) == entry.chip }
                } + listOfNotNull(Feed.PODCASTS.takeIf { shelves.isNotEmpty() })
            }
            val filter = chips.firstOrNull { it.name == filterName } ?: Feed.ALL
            LaunchedEffect(filter) { if (filter == Feed.PODCASTS) onWantPodcasts() }
            // Read once here rather than inside the rows: the label travels
            // with the play so the player can say where the song came from,
            // and that is a plain string by the time it leaves this screen.
            val onRepeatLabel = stringResource(R.string.on_repeat)
            val classicsLabel = stringResource(R.string.all_time_favourites)
            // Spotify's rootlist arrives in the order the account added them,
            // which for an old account is close to arbitrary — the playlist
            // opened every day can sit thirtieth.
            val playlists = remember(state.playlists, playlistOrder) {
                // The phone's own music belongs to the library rather than to
                // this page: home is what the service put together for the
                // listener, and a folder of files is not that.
                state.playlists
                    .filterNot { dev.lelonio.square.data.LocalLibrary.isLocalContext(it.uri) }
                    .sortedByRecentlyOpened(playlistOrder)
            }
            val listState = rememberLazyListState()

            // How far the header has collapsed, 0 to 1.
            //
            // Read from the list rather than driven by a nested-scroll
            // connection: the header is a sibling of the list, not part of it,
            // so it never consumes scroll and the list keeps its own fling
            // untouched. Past the first item the header is simply fully
            // collapsed — asking for the exact offset of something scrolled far
            // off screen means measuring items that no longer exist.
            val offline by dev.lelonio.square.playback.OfflineMode.active
                .collectAsStateWithLifecycle()

            val collapse by remember {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0) 1f
                    else (listState.firstVisibleItemScrollOffset / COLLAPSE_DISTANCE_PX)
                        .coerceIn(0f, 1f)
                }
            }

            // Back to the top when the view changes. The lists have nothing in
            // common, so keeping the old offset drops the new one in the middle
            // of itself.
            //
            // The first run is skipped, and that is the whole point: an effect
            // keyed on the filter also runs when this screen is composed, which
            // is every time the listener comes back from a playlist. The page
            // they left halfway down was being scrolled to the top under them
            // for a filter that had not changed at all.
            var scrolledFor by rememberSaveable { mutableStateOf(filter.name) }
            LaunchedEffect(filter) {
                if (scrolledFor != filter.name) {
                    scrolledFor = filter.name
                    listState.scrollToItem(0)
                }
            }

            Column(Modifier.fillMaxSize()) {
                Header(
                    name = state.displayName,
                    avatarUrl = state.avatarUrl,
                    service = R.string.backend_spotify,
                    serviceIcon = PhosphorIcons.Regular.SpotifyLogo,
                    onSwitchSource = onSwitchSource,
                    collapse = { collapse },
                    // The chip that is lit is the one that was tapped. It used
                    // to follow whichever section the scroll had reached, which
                    // read as a control changing itself: the chips look like a
                    // filter, so a lit one has to mean "this is what you are
                    // looking at because you asked for it".
                    highlighted = filter,
                    backdrop = backdrop,
                    topPadding = contentPadding.calculateTopPadding(),
                    onFilter = { filterName = it.name },
                    chips = chips,
                    showFilters = chips.size > 1,
                    onOpenSettings = onOpenSettings,
                    friends = friends,
                    onOpenFriends = onOpenFriends,
                )

                AnimatedContent(
                    targetState = filter,
                    transitionSpec = {
                        // Short and vertical: the sections do not move sideways
                        // when they are filtered, so neither should the change.
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 14 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "feed",
                ) { current ->
                // "Everything" means Spotify's own home when there is one.
                //
                // The sections below it are this app standing in for a home
                // page it could not read: what the account plays most, what it
                // came back to, new releases. Once the real one arrives they
                // are a second, worse answer to the same question, printed
                // underneath the first, so they stand in only while there is
                // no real one; the chips are the real one's.
                // Not while Spotify's are on their way: shown first and then
                // replaced, the stand-in page was a page that changed under
                // the listener a few seconds after opening.
                val ownFeed = shelves.isEmpty() && !shelvesLoading
                val showOwn = current == Feed.ALL && ownFeed
                val showReleases = showOwn
                val showArtists = showOwn
                val showLibrary = showOwn
                val showForYou = showOwn
                val filter = current

                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        // Fades the first rows out under the header instead of
                        // cutting them off square. DstIn needs a layer of its
                        // own, or the mask would erase the page behind the list
                        // as well.
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            if (collapse > 0.01f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        (FADE_FRACTION * collapse) to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                    state = listState,
                    // The header already covers the status bar, so only the
                    // bottom inset is left for the list.
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                // Why the page is shorter than usual, before the page.
                //
                // Added to the list only while it is actually offline, not
                // added always and left to draw nothing: an item of zero height
                // is still an item, so the first scrolled pixel moved the list
                // past it and the header — which reads its collapse off the
                // first visible item — snapped shut instead of easing.
                if (offline) {
                    item(key = "offline") {
                        dev.lelonio.square.ui.components.OfflineNotice(onRetry = onRetryOnline)
                    }
                }

                if (recent.isNotEmpty()) {
                    item(contentType = "library") { Heading(stringResource(R.string.play_again)) }
                    item(contentType = "library") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            itemsIndexed(recent, key = { _, track -> track.uri }) { index, track ->
                                TrackTile(track) { onPlayRecent(recent, index) }
                            }
                        }
                    }
                } else {
                    item(key = "empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 100.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.play_again),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Ink,
                                )
                                Text(
                                    stringResource(R.string.nothing_here),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkDim,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 40.dp),
                                )
                            }
                        }
                    }
                }

                item(contentType = "tail") { Box(Modifier.height(24.dp)) }
            }
        }
    }
}
}
}

/**
 * Name, picture and filters, always on screen.
 *
 * It shrinks rather than sliding away: the filter chips are a control, and a
 * control that has to be scrolled back to before it can be used may as well not
 * be there. What collapses is only the part that is decoration — the greeting
 * line and the size of the name.
 */
@Composable
private fun Header(
    modifier: Modifier = Modifier,
    name: String,
    avatarUrl: String?,
    /** Which service the page is showing: see the note on the line it draws. */
    @StringRes service: Int,
    /** Its mark, so the source is recognisable before the line is read. */
    serviceIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onSwitchSource: () -> Unit,
    /**
     * How far collapsed, as a lambda rather than a value.
     *
     * Deliberate, and the whole reason this scrolls smoothly: a `Float`
     * parameter is read when the header is composed, so every pixel of scroll
     * invalidated the composable that produced it — the home page, list content
     * lambdas included — several dozen times a second. Read inside `layout` and
     * `graphicsLayer` blocks instead, the same movement costs a measure pass and
     * no recomposition at all.
     */
    collapse: () -> Float,
    /** The chip drawn as active: the one that was tapped. */
    highlighted: Feed,
    backdrop: Backdrop,
    topPadding: Dp,
    onFilter: (Feed) -> Unit,
    chips: List<Feed> = Feed.entries.toList(),
    onOpenSettings: () -> Unit,
    /** Who the account follows and what they play; empty hides the control. */
    friends: List<dev.lelonio.square.data.FriendListen> = emptyList(),
    onOpenFriends: () -> Unit = {},
    /**
     * False on a page with no feed to filter.
     *
     * The chips are the one part of this header that is Spotify's: they pick
     * between sections built out of that account's data, and YouTube's page is
     * whatever shelves the service itself sent.
     */
    showFilters: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            // Seats the header on the page instead of leaving it floating over
            // whatever the list has scrolled underneath it.
            //
            // In the page's own colour rather than in black. Black was right
            // while every page was a tinted wash and a little more darkness at
            // the top read as depth; over a neutral ground it is a grey band
            // across the top of every screen, and on the light side it is a
            // smudge. Fading from the ground colour does the same job — the
            // rows disappear under the header instead of running into it — and
            // leaves no band when there is nothing scrolled under it.
            .background(
                Brush.verticalGradient(
                    0f to dev.lelonio.square.ui.theme.pageGround(),
                    1f to Color.Transparent,
                ),
            )
            .padding(top = topPadding),
    ) {
        if (friends.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FriendFaces(friends, onOpenFriends)
            }
        }
    }
}

@Composable
private fun FilterRow(selected: Feed, chips: List<Feed>, backdrop: Backdrop, onSelect: (Feed) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
    ) {
        items(chips, key = { it.name }) { entry ->
            dev.lelonio.square.ui.components.FilterChip(
                label = stringResource(entry.label),
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/**
 * One release, the full width of the page.
 *
 * The caption sits in a pane of glass over the cover rather than under it, the
 * way the bars over the rest of the app do. Text straight on artwork needs a
 * gradient to survive a light cover, and a gradient large enough to do that
 * ends up dimming the picture it is supposed to be showing.
 */
@Composable
private fun FeedCard(item: SearchItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .softShadow(shape, elevation = 26.dp, spot = 0.55f)
            .clip(shape)
            .pressable(onClick, pressedScale = 0.98f),
    ) {
        // The same cover twice, and that is the point.
        //
        // Spotify serves album art at 640px and no larger, so a cover stretched
        // across a 1080px-wide card is upscaled by nearly two and looks soft —
        // which is exactly the "low quality covers" this replaced. The
        // background is allowed to be soft, so it takes the full width; the copy
        // that has to be sharp is drawn well under its native size.
        //
        // Softened by decoding it tiny and letting the upscale blur it, rather
        // than by `Modifier.blur`. That modifier renders into a layer of its own
        // which is not bound by this box's clip, so it painted a hard black
        // rectangle past the card's rounded corners and up into the header.
        Artwork(
            url = item.artworkUrl,
            title = item.title,
            modifier = Modifier.matchParentSize(),
            corner = 0.dp,
            decodeSize = 24.dp,
        )
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.42f)))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                url = item.artworkUrl,
                title = item.title,
                modifier = Modifier
                    .size(COVER_SIZE)
                    .softShadow(RoundedCornerShape(18.dp), elevation = 24.dp, spot = 0.5f),
                corner = 18.dp,
                decodeSize = COVER_SIZE,
            )
            Text(
                item.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 18.dp),
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** How many artists fit across the page without the names going to three lines. */
private const val ARTIST_COLUMNS = 3

/**
 * One cell of the artist grid: the same round portrait, sized by its column.
 *
 * Separate from [ArtistTile], which is fixed at the carousel's width because a
 * horizontal row has no column to take its width from.
 */
@Composable
private fun ArtistCell(artist: SearchItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = artist.artworkUrl,
            title = artist.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .softShadow(CircleShape, elevation = 14.dp),
            corner = 200.dp,
            decodeSize = 160.dp,
        )
        Text(
            artist.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Round, because that is how every music app has drawn an artist for a decade. */
@Composable
private fun ArtistTile(artist: SearchItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(110.dp)
            .pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            url = artist.artworkUrl,
            title = artist.title,
            modifier = Modifier
                .size(110.dp)
                .softShadow(CircleShape, elevation = 14.dp),
            corner = 55.dp,
            decodeSize = 110.dp,
        )
        Text(
            artist.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun PlaylistTile(playlist: CatalogPlaylist, onClick: () -> Unit) {
    // A portrait among covers: the one shape difference a shelf of people
    // needs, and the reason an artist row reads as people at a glance.
    val corner = if (playlist.isArtist) 76.dp else 20.dp
    Column(
        Modifier
            .width(152.dp)
            .pressable(onClick),
    ) {
        Artwork(
            url = playlist.artworkUrl,
            title = playlist.name,
            modifier = Modifier
                .size(152.dp)
                .softShadow(RoundedCornerShape(corner), elevation = 18.dp),
            corner = corner,
        )
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            // One line where there is something written under it, or the two
            // together push the next shelf off its own baseline.
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

@Composable
private fun TrackTile(track: CatalogTrack, onClick: () -> Unit) {
    Column(
        // No clip on the column: it would cut off the artwork's drop shadow,
        // which spreads wider than the tile itself.
        Modifier
            .width(128.dp)
            .pressable(onClick),
    ) {
        Artwork(
            url = track.artworkUrl,
            title = track.name,
            modifier = Modifier
                .size(128.dp)
                .softShadow(RoundedCornerShape(18.dp), elevation = 14.dp),
            corner = 18.dp,
            decodeSize = 128.dp,
        )
        Text(
            track.name,
            style = MaterialTheme.typography.titleMedium,
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

/** A row of tracks that play where they are tapped. */
/**
 * A section's shape, before the section has arrived.
 *
 * Deliberately still — no shimmer. A sweep across half the page draws the eye
 * to the part of it that has nothing to look at, and these are gone within a
 * second or two anyway. What they are for is the layout: holding the space
 * means the rows that land later land in the place they already occupied.
 */
@Composable
private fun SkeletonSection(
    tileWidth: Dp,
    tileHeight: Dp,
    round: Boolean = false,
    card: Boolean = false,
) {
    val fill = Ink.copy(alpha = 0.07f)
    Column(Modifier.padding(top = 22.dp)) {
        // Stands in for the heading.
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(fill),
        )
        if (card) {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(tileHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(fill),
            )
        } else {
            Row(
                Modifier
                    .padding(start = 20.dp, top = 14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(SKELETON_TILES) {
                    Box(
                        Modifier
                            .width(tileWidth)
                            .height(tileHeight)
                            .clip(if (round) CircleShape else RoundedCornerShape(18.dp))
                            .background(fill),
                    )
                }
            }
        }
    }
}

/** Enough to reach the edge of the screen, which is all a placeholder row is. */
private const val SKELETON_TILES = 3

@Composable
private fun TrackRow(tracks: List<CatalogTrack>, onPlay: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        itemsIndexed(tracks, key = { _, track -> track.uri }) { index, track ->
            TrackTile(track) { onPlay(index) }
        }
    }
}

/** An album or a playlist in a carousel: square art, name, who it is by. */
@Composable
private fun FeedTile(item: SearchItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(152.dp)
            .pressable(onClick),
    ) {
        Artwork(
            url = item.artworkUrl,
            title = item.title,
            modifier = Modifier
                .size(152.dp)
                .softShadow(RoundedCornerShape(20.dp), elevation = 18.dp),
            corner = 20.dp,
        )
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            item.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = InkDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun <T> Carousel(
    items: List<T>,
    key: (T) -> Any,
    item: @Composable (T) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        items(items, key = key) { item(it) }
    }
}

@Composable
private fun Heading(text: String, strapline: String? = null) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 30.dp)) {
        // Above the title, smaller and quieter, the way the service writes it:
        // "MIX", "START RADIO FROM A SONG", an artist's name. It is most of
        // what tells two shelves with the same title apart.
        strapline?.takeIf { it.isNotBlank() }?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.headlineLarge)
    }
}

/**
 * A shelf of songs as a block rather than a row.
 *
 * Four to a column, and the columns scroll a screen at a time — the shape the
 * official app gives its longest shelves. A song here is a line, not a tile:
 * small art, the title, who it is by. Twenty of these as tiles would be a
 * carousel nobody reaches the end of.
 */
@Composable
private fun QuickPicks(tracks: List<CatalogTrack>, onPlay: (Int) -> Unit) {
    val columns = tracks.chunked(QUICK_PICK_ROWS)
    val width = LocalConfiguration.current.screenWidthDp.dp - 44.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        flingBehavior = rememberSnapFlingBehavior(rememberLazyListState()),
        modifier = Modifier.padding(top = 14.dp),
    ) {
        itemsIndexed(columns, key = { _, column -> column.first().uri }) { columnIndex, column ->
            Column(
                Modifier.width(width),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                column.forEachIndexed { rowIndex, track ->
                    QuickPickRow(track) {
                        onPlay(columnIndex * QUICK_PICK_ROWS + rowIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickRow(track: CatalogTrack, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = track.artworkUrl,
            title = track.name,
            modifier = Modifier.size(52.dp),
            corner = 10.dp,
            decodeSize = 52.dp,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                track.name,
                style = MaterialTheme.typography.titleMedium,
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

/** The service's own filters, drawn as the app's own chips. */
@Composable
private fun ChipRow(
    chips: List<dev.lelonio.square.backend.HomeChip>,
    selected: String?,
    backdrop: Backdrop,
    onPick: (dev.lelonio.square.backend.HomeChip?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        items(chips, key = { it.title }) { chip ->
            val isSelected = chip.title == selected
            dev.lelonio.square.ui.components.FilterChip(
                label = chip.title,
                selected = isSelected,
                // Pressing the chip that is already on takes the filter off,
                // which is what the service's own deselect does.
                onClick = { onPick(if (isSelected) null else chip) },
            )
        }
    }
}

/** A glass pill, for the handful of places that need a button at all. */
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

/**
 * The home page when the source is YouTube Music.
 *
 * Deliberately thin, and honestly so. Everything the Spotify home page is made
 * of — the playlists, the top artists, what the account played on other
 * devices — is an account's own data, and this backend is anonymous: there is
 * no account to have any of it. What does exist is what was played here, which
 * is kept on the device and works the same for both sources.
 *
 * So it shows that, and otherwise points at search rather than filling the
 * screen with rows invented to look busy.
 */
@Composable
private fun YouTubeHome(
    contentPadding: PaddingValues,
    home: MainViewModel.YouTubeHomeState,
    recent: List<CatalogTrack>,
    accountName: String,
    backdrop: Backdrop,
    onPlayRecent: (List<CatalogTrack>, Int) -> Unit,
    onPlayTrending: (List<CatalogTrack>, Int) -> Unit,
    onOpenPlaylist: (CatalogPlaylist) -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchSource: () -> Unit,
    onPickChip: (dev.lelonio.square.backend.HomeChip?) -> Unit,
    onLoadMore: () -> Unit,
    signedIn: Boolean,
    onSignIn: () -> Unit,
) {
    val empty = recent.isEmpty()
    if (empty) {
        Box(Modifier.fillMaxSize()) {
            Centered {
                if (home.loading) {
                    CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(R.string.play_again),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink,
                    )
                    Text(
                        stringResource(R.string.youtube_home_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
            }
            YouTubeHeader(
                accountName = accountName,
                topPadding = contentPadding.calculateTopPadding(),
                backdrop = backdrop,
                collapse = { 0f },
                onOpenSettings = onOpenSettings,
                onSwitchSource = onSwitchSource,
            )
        }
        return
    }

    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        YouTubeHeader(
            accountName = accountName,
            topPadding = contentPadding.calculateTopPadding(),
            backdrop = backdrop,
            collapse = { 0f },
            onOpenSettings = onOpenSettings,
            onSwitchSource = onSwitchSource,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            FADE_FRACTION to Color.Black,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            state = listState,
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            item(key = "recent-head") { Heading(stringResource(R.string.play_again)) }
            item(key = "recent") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    itemsIndexed(recent, key = { _, track -> track.uri }) { index, track ->
                        TrackTile(track) { onPlayRecent(recent, index) }
                    }
                }
            }
        }
    }
}

/**
 * One of the other tabs, for a source that lays its own pages out.
 *
 * The rows as the service sent them, drawn the way its home page is: a long
 * shelf of songs as quick picks, a short one as tiles, and everything else as
 * covers to open. New and Radio are both this page; what differs is which rows
 * the source was asked for. See MusicBackend.newRows.
 */
@Composable
fun SourceRowsPage(
    title: String,
    rows: List<dev.lelonio.square.backend.HomeRow>,
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
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
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
                        QuickPicks(row.tracks) { i -> onPlay(row.tracks, i) }
                    } else {
                        TrackRow(row.tracks) { i -> onPlay(row.tracks, i) }
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
                            PlaylistTile(entry) { onOpen(entry) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The YouTube page's header.
 *
 * Far plainer than [Header]: that one carries the feed's filter chips and a
 * collapse driven by the scroll, and neither has anything to act on here —
 * there are no feed sections to filter. What it must keep is the way into the
 * settings, because that is the only one there is; the app has no settings tab.
 */
@Composable
private fun YouTubeHeader(
    accountName: String,
    topPadding: Dp,
    backdrop: Backdrop,
    collapse: () -> Float,
    onOpenSettings: () -> Unit,
    onSwitchSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Header(
        modifier = modifier,
        name = accountName,
        avatarUrl = null,
        service = R.string.backend_youtube,
        serviceIcon = PhosphorIcons.Regular.YoutubeLogo,
        onSwitchSource = onSwitchSource,
        collapse = collapse,
        highlighted = Feed.entries.first(),
        backdrop = backdrop,
        topPadding = topPadding,
        onFilter = {},
        onOpenSettings = onOpenSettings,
        showFilters = false,
    )
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

/** How many shelves' outlines stand in while Spotify's are on their way. */
private const val SHELF_OUTLINES = 4

@StringRes
private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..12 -> R.string.good_morning
    in 13..17 -> R.string.good_afternoon
    else -> R.string.good_evening
}

/** A harder film for the chip that is on; see the note at the call site. */
private val SelectedFilm = Color.White.copy(alpha = 0.26f)

/**
 * How far the list scrolls before the header is fully collapsed, in pixels.
 *
 * Pixels rather than dp because it is compared against a scroll offset, which
 * the list reports in pixels; converting per frame to compare two numbers would
 * be work for nothing.
 */
private const val COLLAPSE_DISTANCE_PX = 140f


/** Comfortably under the 640px Spotify serves, so it is never upscaled. */
private val COVER_SIZE = 210.dp

/** How much of the list's height the fade under the header covers. */
private const val FADE_FRACTION = 0.045f

private const val FEED_SIZE = 6
private const val CAROUSEL_SIZE = 8

/** With the library filter on, the carousel is the whole point of the page. */
private const val LIBRARY_SIZE = 30

/**
 * Up to three friends, overlapping, as one control.
 *
 * Stacked rather than listed: the header has room for a gesture, not for a row
 * of names, and overlapping faces are the one shape everybody already reads as
 * "these people, together". The count is left off deliberately — a badge would
 * turn a glance into a number to keep at zero.
 */
@Composable
private fun FriendFaces(
    friends: List<dev.lelonio.square.data.FriendListen>,
    onClick: () -> Unit,
) {
    val shown = friends.take(3)
    // Measured rather than laid out in a row: each face sits on the one before
    // it, so the box is one face wide plus what the others peek out by.
    val width = FACE + FACE_PEEK * (shown.size - 1)
    Box(
        Modifier
            .padding(start = 8.dp)
            .width(width)
            .height(FACE)
            .pressable(onClick, pressedScale = 0.92f),
    ) {
        shown.forEachIndexed { index, friend ->
            Artwork(
                url = friend.avatarUrl,
                title = friend.userName,
                modifier = Modifier
                    .offset(x = FACE_PEEK * index)
                    .size(FACE)
                    .clip(CircleShape)
                    // The first face on top, so the stack leans the way a hand
                    // of cards does rather than away from the reader.
                    .zIndex((shown.size - index).toFloat()),
                corner = FACE / 2,
                decodeSize = FACE,
            )
        }
    }
}

/** One face, and how much of the one behind it is left showing. */
private val FACE = 30.dp
private val FACE_PEEK = 20.dp

/** A shelf of at least this many songs is drawn as a block; see [QuickPicks]. */
private const val QUICK_PICK_MINIMUM = 8

/** How deep that block goes before it starts a new column. */
private const val QUICK_PICK_ROWS = 4
