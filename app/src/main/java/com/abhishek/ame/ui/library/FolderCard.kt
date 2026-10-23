package com.abhishek.ame.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.PushPin
import com.abhishek.ame.AmeApplication
import com.abhishek.ame.data.CatalogPlaylist
import com.abhishek.ame.data.DownloadStore
import com.abhishek.ame.data.LocalLibrary
import com.abhishek.ame.ui.components.artSource
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.softShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/**
 * Exact SVG Flap Path from the rare-ui/folder-component:
 * BASE_WIDTH = 321, BASE_HEIGHT = 241
 *
 * M0 25C0 11.1929 11.1929 0 25 0H136.084C143.044 0 149.689 2.90139 154.42 8.00608
 * L178.08 33.5343C182.811 38.639 189.456 41.5404 196.416 41.5404H296
 * C309.807 41.5404 321 52.7333 321 66.5404V216C321 229.807 309.807 241 296 241
 * H25C11.1929 241 0 229.807 0 216V25Z
 */
class RareFlapShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val sx = size.width / 321f
        val sy = size.height / 241f

        val path = Path().apply {
            moveTo(0f * sx, 25f * sy)
            cubicTo(
                0f * sx, 11.1929f * sy,
                11.1929f * sx, 0f * sy,
                25f * sx, 0f * sy,
            )
            lineTo(136.084f * sx, 0f * sy)
            cubicTo(
                143.044f * sx, 0f * sy,
                149.689f * sx, 2.90139f * sy,
                154.42f * sx, 8.00608f * sy,
            )
            lineTo(178.08f * sx, 33.5343f * sy)
            cubicTo(
                182.811f * sx, 38.639f * sy,
                189.456f * sx, 41.5404f * sy,
                196.416f * sx, 41.5404f * sy,
            )
            lineTo(296f * sx, 41.5404f * sy)
            cubicTo(
                309.807f * sx, 41.5404f * sy,
                321f * sx, 52.7333f * sy,
                321f * sx, 66.5404f * sy,
            )
            lineTo(321f * sx, 216f * sy)
            cubicTo(
                321f * sx, 229.807f * sy,
                309.807f * sx, 241f * sy,
                296f * sx, 241f * sy,
            )
            lineTo(25f * sx, 241f * sy)
            cubicTo(
                11.1929f * sx, 241f * sy,
                0f * sx, 229.807f * sy,
                0f * sx, 216f * sy,
            )
            lineTo(0f * sx, 25f * sy)
            close()
        }
        return Outline.Generic(path)
    }
}

val SharedFlapShape = RareFlapShape()

/**
 * Visual styling theme for the folder, based on the rare-ui themes (black, white, blue)
 * and extended to vibrant pastel themes matching the user's reference designs.
 */
data class RareFolderTheme(
    val backFill: Color,
    val flapFill: Color,
    val flapFillOpacity: Float,
    val flapStroke: Color,
    val cardFill: Color,
    val cardStroke: Color,
    val cardLineFill: Color,
    val glow: Color,
)

private val Themes = listOf(
    // 0: Rose Pink
    RareFolderTheme(
        backFill = Color(0xFFF472B6),
        flapFill = Color(0xFFF472B6),
        flapFillOpacity = 0.45f,
        flapStroke = Color(0xFFFBCFE8),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFFF472B6),
    ),
    // 1: Warm Amber / Peach
    RareFolderTheme(
        backFill = Color(0xFFFB923C),
        flapFill = Color(0xFFFB923C),
        flapFillOpacity = 0.45f,
        flapStroke = Color(0xFFFED7AA),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFFFB923C),
    ),
    // 2: Fresh Mint / Sage
    RareFolderTheme(
        backFill = Color(0xFF4ADE80),
        flapFill = Color(0xFF4ADE80),
        flapFillOpacity = 0.42f,
        flapStroke = Color(0xFFBBF7D0),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFF4ADE80),
    ),
    // 3: Blue (Direct from rare-ui theme: #50B1FD, flap #3a9ae8, stroke #7ec8ff)
    RareFolderTheme(
        backFill = Color(0xFF50B1FD),
        flapFill = Color(0xFF3A9AE8),
        flapFillOpacity = 0.48f,
        flapStroke = Color(0xFF7EC8FF),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFF38BDF8),
    ),
    // 4: Soft Lavender
    RareFolderTheme(
        backFill = Color(0xFFA78BFA),
        flapFill = Color(0xFFA78BFA),
        flapFillOpacity = 0.42f,
        flapStroke = Color(0xFFDDD6FE),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFFA78BFA),
    ),
    // 5: Coral Sunset
    RareFolderTheme(
        backFill = Color(0xFFFB7185),
        flapFill = Color(0xFFFB7185),
        flapFillOpacity = 0.42f,
        flapStroke = Color(0xFFFECDD3),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFFFB7185),
    ),
    // 6: Deep Violet / Music
    RareFolderTheme(
        backFill = Color(0xFFC084FC),
        flapFill = Color(0xFFC084FC),
        flapFillOpacity = 0.45f,
        flapStroke = Color(0xFFE9D5FF),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFFC084FC),
    ),
    // 7: Electric Blue
    RareFolderTheme(
        backFill = Color(0xFF60A5FA),
        flapFill = Color(0xFF60A5FA),
        flapFillOpacity = 0.45f,
        flapStroke = Color(0xFFBFDBFE),
        cardFill = Color(0xFFF1F1F1),
        cardStroke = Color(0xFFE0E0E0),
        cardLineFill = Color(0xFFD4D4D4),
        glow = Color(0xFF60A5FA),
    ),
)

fun resolveRareTheme(playlist: CatalogPlaylist): RareFolderTheme {
    return when {
        playlist.uri == LocalLibrary.CONTEXT_URI -> RareFolderTheme(
            backFill = Color(0xFF34D399),
            flapFill = Color(0xFF10B981),
            flapFillOpacity = 0.45f,
            flapStroke = Color(0xFFA7F3D0),
            cardFill = Color(0xFFF1F1F1),
            cardStroke = Color(0xFFE0E0E0),
            cardLineFill = Color(0xFFD4D4D4),
            glow = Color(0xFF34D399),
        )
        playlist.uri == DownloadStore.SINGLES -> RareFolderTheme(
            backFill = Color(0xFF50B1FD),
            flapFill = Color(0xFF3A9AE8),
            flapFillOpacity = 0.48f,
            flapStroke = Color(0xFF7EC8FF),
            cardFill = Color(0xFFF1F1F1),
            cardStroke = Color(0xFFE0E0E0),
            cardLineFill = Color(0xFFD4D4D4),
            glow = Color(0xFF38BDF8),
        )
        playlist.uri.contains("collection", ignoreCase = true) ||
            playlist.name.contains("liked", ignoreCase = true) -> RareFolderTheme(
            backFill = Color(0xFFF43F5E),
            flapFill = Color(0xFFE11D48),
            flapFillOpacity = 0.45f,
            flapStroke = Color(0xFFFECDD3),
            cardFill = Color(0xFFF1F1F1),
            cardStroke = Color(0xFFE0E0E0),
            cardLineFill = Color(0xFFD4D4D4),
            glow = Color(0xFFF43F5E),
        )
        else -> {
            val seed = (playlist.uri.hashCode() xor playlist.name.hashCode()).absoluteValue
            Themes[seed % Themes.size]
        }
    }
}

/**
 * An accurate Jetpack Compose conversion of the rare-ui folder component,
 * featuring 3 styled cards in a frosted glass pocket, 3D tilted front flap,
 * and clean typography with snappy, instant touch response.
 */
@Composable
fun FolderTile(
    playlist: CatalogPlaylist,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val theme = remember(playlist.uri, playlist.name) { resolveRareTheme(playlist) }
    val context = LocalContext.current
    val app = context.applicationContext as? AmeApplication

    val songArts by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = playlist.uri,
        key2 = playlist.artworkUrl,
    ) {
        val application = app ?: return@produceState
        if (playlist.uri == LocalLibrary.CONTEXT_URI) {
            val localArts = withContext(Dispatchers.IO) {
                if (LocalLibrary.granted(context)) {
                    LocalLibrary.tracks(context).mapNotNull { it.artworkUrl }.distinct().take(3)
                } else emptyList()
            }
            if (localArts.isNotEmpty()) {
                value = localArts
                return@produceState
            }
        } else if (playlist.uri == DownloadStore.SINGLES) {
            val dlArts = application.downloads.files.value.keys
                .mapNotNull { uri -> application.downloads.trackOf(uri)?.artworkUrl }
                .distinct().take(3)
            if (dlArts.isNotEmpty()) {
                value = dlArts
                return@produceState
            }
        }

        val cached = withContext(Dispatchers.IO) {
            application.contextCache.read(playlist.uri)?.tracks?.mapNotNull { it.artworkUrl }?.distinct()?.take(3)
        }
        if (!cached.isNullOrEmpty()) {
            value = cached
            return@produceState
        }

        if (playlist.uri.startsWith("youtube:") || playlist.uri.startsWith("spotify:")) {
            val remoteArts = withContext(Dispatchers.IO) {
                runCatching {
                    application.activeBackend.tracksOf(playlist.uri).mapNotNull { it.artworkUrl }.distinct().take(3)
                }.getOrNull()
            }
            if (!remoteArts.isNullOrEmpty()) {
                value = remoteArts
            }
        }
    }

    val centerArt = if (isRealArt(playlist.artworkUrl)) playlist.artworkUrl else songArts.getOrNull(0)
    val leftArt = songArts.getOrNull(1) ?: (if (isRealArt(playlist.artworkUrl)) playlist.artworkUrl else null) ?: songArts.getOrNull(0)
    val rightArt = songArts.getOrNull(2) ?: (if (isRealArt(playlist.artworkUrl)) playlist.artworkUrl else null) ?: songArts.getOrNull(0)

    Column(
        modifier = modifier.pressable(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            pressedScale = 0.96f,
            onLongClick = onLongClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Folder 321x270 aspect ratio container (1.1888f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(321f / 270f),
            contentAlignment = Alignment.Center,
        ) {
            // 1. Ambient Colored Halo / Glow
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 1f),
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            theme.glow.copy(alpha = 0.50f),
                            theme.glow.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.60f),
                        radius = size.width * 0.85f,
                    ),
                )
            }

            // 2. Backplate: width 321, height 270, borderRadius 25
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                theme.backFill.copy(alpha = 0.90f),
                                theme.backFill.copy(alpha = 0.70f),
                                Color(0xFF14171E).copy(alpha = 0.75f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.60f),
                                theme.backFill.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.15f),
                            ),
                        ),
                        shape = RoundedCornerShape(22.dp),
                    ),
            )

            // 3. The Three Cards Stacked Inside
            // Card 3 (Left): x = -22, y = -12, rotate = -5
            RareCardView(
                id = 3,
                theme = theme,
                playlist = playlist,
                artUrl = leftArt,
                rotation = -5f,
                modifier = Modifier
                    .fillMaxWidth(164f / 321f)
                    .fillMaxHeight(214f / 270f)
                    .align(Alignment.Center)
                    .offset(x = (-22).dp, y = (-12).dp),
            )

            // Card 2 (Center): x = 2, y = -11, rotate = 2
            RareCardView(
                id = 2,
                theme = theme,
                playlist = playlist,
                artUrl = centerArt,
                rotation = 2f,
                modifier = Modifier
                    .fillMaxWidth(164f / 321f)
                    .fillMaxHeight(214f / 270f)
                    .align(Alignment.Center)
                    .offset(x = 2.dp, y = (-11).dp),
            )

            // Card 1 (Right): x = 22, y = -5, rotate = 10
            RareCardView(
                id = 1,
                theme = theme,
                playlist = playlist,
                artUrl = rightArt,
                rotation = 10f,
                modifier = Modifier
                    .fillMaxWidth(164f / 321f)
                    .fillMaxHeight(214f / 270f)
                    .align(Alignment.Center)
                    .offset(x = 22.dp, y = (-5).dp),
            )

            // 4. Front Flap (321 x 241, positioned at bottom with 3D tilt)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(241f / 270f)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        rotationX = -12f
                        cameraDistance = 16f * density
                    }
                    .clip(SharedFlapShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.50f),
                                Color.White.copy(alpha = 0.28f),
                                theme.flapFill.copy(alpha = theme.flapFillOpacity),
                                Color(0xFF161922).copy(alpha = 0.65f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.3.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.85f),
                                theme.flapStroke,
                                Color.White.copy(alpha = 0.15f),
                            ),
                        ),
                        shape = SharedFlapShape,
                    ),
            ) {
                // Diagonal frosted glass reflection
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.8f, size.height * 0.8f),
                        ),
                    )
                }
            }

            // 5. Pinned Mark on the Folder (if pinned)
            if (pinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14171E).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.Fill.PushPin,
                        contentDescription = "Pinned",
                        tint = Ink,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        // Title beneath the folder - clean, single text label without duplicate pill
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}

private fun isRealArt(url: String?): Boolean =
    !url.isNullOrBlank() &&
    url != LocalLibrary.COVER &&
    url != com.abhishek.ame.ui.components.DOWNLOADS_COVER

/**
 * Modern sleeve card featuring real song artworks peeked from the folder.
 * If no song artwork is available, displays a sleek vinyl sleeve placeholder
 * instead of paper document lines.
 */
@Composable
private fun RareCardView(
    id: Int,
    theme: RareFolderTheme,
    playlist: CatalogPlaylist,
    artUrl: String?,
    rotation: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val url = artUrl
    val hasArtwork = isRealArt(url)

    val request = remember(url, hasArtwork, context) {
        if (hasArtwork && url != null) {
            val source = artSource(url)
            ImageRequest.Builder(context)
                .data(source)
                .scale(Scale.FILL)
                .size(260, 260)
                .crossfade(true)
                .build()
        } else null
    }

    Box(
        modifier = modifier
            .rotate(rotation)
            .softShadow(RoundedCornerShape(12.dp), elevation = 6.dp, spot = 0.35f)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.cardFill)
            .border(1.dp, theme.cardStroke, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            // Real song artwork on this card!
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.5.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else if (playlist.uri == LocalLibrary.CONTEXT_URI) {
            // Local files stylized cover
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF10B981), Color(0xFF047857)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                )
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                for (i in 1..4) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = size.minDimension * (0.12f * i),
                        center = center,
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        } else if (playlist.uri == DownloadStore.SINGLES) {
            // Downloads stylized cover
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    ),
                )
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = size.minDimension * 0.35f,
                    center = center,
                    style = Stroke(width = 2.5f),
                )
            }
        } else {
            // Aesthetic vinyl sleeve placeholder for empty/new playlist (no paper lines!)
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(
                            theme.flapFill.copy(alpha = 0.85f),
                            Color(0xFF1A1D26),
                        ),
                    ),
                )
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                for (i in 1..3) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = size.minDimension * (0.14f * i),
                        center = center,
                        style = Stroke(width = 1.2f),
                    )
                }
                drawCircle(
                    color = theme.glow.copy(alpha = 0.6f),
                    radius = size.minDimension * 0.08f,
                    center = center,
                )
            }
        }
    }
}
