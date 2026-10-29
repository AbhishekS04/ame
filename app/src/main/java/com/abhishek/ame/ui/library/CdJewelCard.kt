package com.abhishek.ame.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.abhishek.ame.R
import com.abhishek.ame.data.CatalogPlaylist
import com.abhishek.ame.data.DownloadStore
import com.abhishek.ame.data.LocalLibrary
import com.abhishek.ame.ui.components.DOWNLOADS_COVER
import com.abhishek.ame.ui.components.artSource
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.PushPin
import com.adamglin.phosphoricons.regular.ArrowCircleDown
import com.adamglin.phosphoricons.regular.FolderSimple
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Curated CD Disc Palette for playlists without custom artwork,
 * matching aesthetic physical CD prints (e.g., lime green brat, platinum blond).
 */
private data class CdPalette(
    val bg: Color,
    val textColor: Color,
    val ringColor: Color,
)

private val CD_PALETTES = listOf(
    // 0: Brat Lime Green (inspired by Charli XCX)
    CdPalette(
        bg = Color(0xFF8ACE00),
        textColor = Color(0xFF141708),
        ringColor = Color(0xFF6B9F00).copy(alpha = 0.5f),
    ),
    // 1: Platinum / Silver Minimal (inspired by Frank Ocean Blond)
    CdPalette(
        bg = Color(0xFFD8DAD6),
        textColor = Color(0xFF181916),
        ringColor = Color(0xFFA5A8A2).copy(alpha = 0.55f),
    ),
    // 2: Great Wave Indigo / Midnight Blue
    CdPalette(
        bg = Color(0xFF1A3353),
        textColor = Color(0xFFF1F5F9),
        ringColor = Color(0xFF38BDF8).copy(alpha = 0.45f),
    ),
    // 3: Obsidian Noir / Starry Graphite
    CdPalette(
        bg = Color(0xFF1F2228),
        textColor = Color(0xFFE2E8F0),
        ringColor = Color(0xFF64748B).copy(alpha = 0.45f),
    ),
    // 4: Cadmium Amber Gold
    CdPalette(
        bg = Color(0xFFE5A93C),
        textColor = Color(0xFF261904),
        ringColor = Color(0xFF92400E).copy(alpha = 0.5f),
    ),
    // 5: Crimson Ruby
    CdPalette(
        bg = Color(0xFFDC2626),
        textColor = Color(0xFFFFFFFF),
        ringColor = Color(0xFF7F1D1D).copy(alpha = 0.5f),
    ),
    // 6: Royal Amethyst Violet
    CdPalette(
        bg = Color(0xFF7C3AED),
        textColor = Color(0xFFFFFFFF),
        ringColor = Color(0xFF4C1D95).copy(alpha = 0.5f),
    ),
    // 7: Electric Sky Cyan
    CdPalette(
        bg = Color(0xFF0EA5E9),
        textColor = Color(0xFF082F49),
        ringColor = Color(0xFF0369A1).copy(alpha = 0.5f),
    ),
    // 8: Sakura Pastel Rose
    CdPalette(
        bg = Color(0xFFF472B6),
        textColor = Color(0xFF4A042A),
        ringColor = Color(0xFFBE185D).copy(alpha = 0.5f),
    ),
    // 9: Emerald Mint
    CdPalette(
        bg = Color(0xFF10B981),
        textColor = Color(0xFF022C1A),
        ringColor = Color(0xFF065F46).copy(alpha = 0.5f),
    ),
)

private fun isRealArtwork(url: String?): Boolean =
    !url.isNullOrBlank() &&
    url != LocalLibrary.COVER &&
    url != DOWNLOADS_COVER &&
    !url.startsWith("file://fake")

/**
 * 3-Column CD Jewel Case Card matching inspo-ame/library.png.
 *
 * Renders an authentic physical compact disc jewel case with:
 * - Circular disc slot holding custom artwork (or stylized studio CD print)
 * - Transparent jewel case overlay with clear plastic hinges, corner latches, and reflections
 * - Center clear plastic spindle hub with 4 holes sitting on top
 * - Two-line title text below case
 * - Green pushpin icon beside title when pinned
 * - No redundant name text everywhere, clean and spacious.
 */
@Composable
fun CdJewelCard(
    playlist: CatalogPlaylist,
    pinned: Boolean,
    modifier: Modifier = Modifier,
    isCurrentlyPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(
                onClick = onClick,
                onLongClick = onLongClick,
                pressedScale = 0.95f,
            ),
    ) {
        // CD Jewel Case
        CdJewelCase(playlist = playlist)

        Spacer(Modifier.height(8.dp))

        // Title Row: Pinned icon (if pinned) + Playlist title
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        ) {
            if (pinned) {
                Icon(
                    PhosphorIcons.Fill.PushPin,
                    contentDescription = stringResource(R.string.pinned),
                    tint = Color(0xFF22C55E),
                    modifier = Modifier
                        .padding(top = 2.5.dp)
                        .size(11.dp),
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                ),
                color = if (isCurrentlyPlaying) Color(0xFF22C55E) else Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Pixel-perfect CD Jewel Case Layout:
 * The template cd_case_overlay.webp has dimensions 1212 x 982.
 * The circular disc well has diameter 884px and is centered at (595, 484).
 */
@Composable
fun CdJewelCase(
    playlist: CatalogPlaylist,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isLocal = playlist.uri == LocalLibrary.CONTEXT_URI
    val isDownloads = playlist.uri == DownloadStore.SINGLES
    val hasArtwork = isRealArtwork(playlist.artworkUrl)

    Layout(
        content = {
            // 0: Circular Compact Disc Layer (Base)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF141518)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    hasArtwork -> {
                        val source = remember(playlist.artworkUrl) { artSource(playlist.artworkUrl!!) }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(source)
                                .crossfade(true)
                                .build(),
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    isLocal -> {
                        SpecialCdDisc(
                            title = stringResource(R.string.local_files).lowercase(),
                            icon = PhosphorIcons.Regular.FolderSimple,
                            bg = Color(0xFF14532D),
                            textColor = Color(0xFF6EE7B7),
                            ringColor = Color(0xFF34D399).copy(alpha = 0.5f),
                        )
                    }
                    isDownloads -> {
                        SpecialCdDisc(
                            title = stringResource(R.string.downloaded_tracks).lowercase(),
                            icon = PhosphorIcons.Regular.ArrowCircleDown,
                            bg = Color(0xFF0369A1),
                            textColor = Color(0xFF7DD3FC),
                            ringColor = Color(0xFF38BDF8).copy(alpha = 0.5f),
                        )
                    }
                    else -> {
                        DefaultCdDisc(playlist = playlist)
                    }
                }

                // Prismatic light diffraction reflection & data track rings
                CdSpecularSheen()
            }

            // 1: Crystal-clear Jewel Case Overlay (Front Lid, Spindle Hub, Bevels, Hinges)
            Image(
                painter = painterResource(R.drawable.cd_case_overlay),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1212f / 982f),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = (width * (982f / 1212f)).roundToInt()
        val discSize = (height * (884f / 982f)).roundToInt()

        val discPlaceable = measurables[0].measure(
            Constraints.fixed(discSize, discSize)
        )
        val overlayPlaceable = measurables[1].measure(
            Constraints.fixed(width, height)
        )

        val discX = (width * (595f / 1212f) - discSize / 2f).roundToInt()
        val discY = (height * (484f / 982f) - discSize / 2f).roundToInt()

        layout(width, height) {
            discPlaceable.place(discX, discY)
            overlayPlaceable.place(0, 0)
        }
    }
}

/**
 * Authentic CD light sheen:
 * - Polycarbonate data track concentric grooves
 * - Prismatic light diffraction rainbow sweep reflection
 */
@Composable
private fun CdSpecularSheen() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // 1. Polycarbonate data micro-grooves
        val rings = 6
        for (i in 1..rings) {
            val r = radius * (0.38f + 0.58f * (i.toFloat() / rings))
            drawCircle(
                color = Color.White.copy(alpha = 0.035f),
                radius = r,
                center = center,
                style = Stroke(width = 0.8f),
            )
        }

        // 2. Prismatic rainbow diffraction sheen
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    Color(0x18FF80BF), // soft magenta
                    Color(0x2280D8FF), // vibrant sky cyan
                    Color(0x18B9F6CA), // soft spring green
                    Color.Transparent,
                    Color.Transparent,
                    Color(0x14FFFFFF), // specular white glint
                    Color(0x18FFE57F), // soft amber
                    Color(0x2080D8FF), // cyan
                    Color.Transparent,
                ),
                center = center,
            ),
            radius = radius,
            center = center,
        )
    }
}

/**
 * Dedicated CD Disc for system items (Local Files, Downloaded Tracks).
 */
@Composable
private fun SpecialCdDisc(
    title: String,
    icon: ImageVector,
    bg: Color,
    textColor: Color,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(bg, bg.copy(alpha = 0.85f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(
                color = ringColor,
                radius = radius * 0.94f,
                center = center,
                style = Stroke(width = 1.2f),
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 0.8f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Default stylized CD print when no custom image is available,
 * rendered with clean typography, track lines, and vibrant color.
 */
@Composable
private fun DefaultCdDisc(
    playlist: CatalogPlaylist,
    modifier: Modifier = Modifier,
) {
    val seed = remember(playlist.name, playlist.uri) {
        (playlist.name.hashCode() xor playlist.uri.hashCode()).absoluteValue
    }
    val palette = remember(seed) {
        CD_PALETTES[seed % CD_PALETTES.size]
    }
    val cleanName = remember(playlist.name) {
        playlist.name.trim().lowercase()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(palette.bg, palette.bg.copy(alpha = 0.85f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Concentric track borders
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(
                color = palette.ringColor,
                radius = radius * 0.94f,
                center = center,
                style = Stroke(width = 1.2f),
            )
            drawCircle(
                color = palette.ringColor.copy(alpha = 0.3f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = 0.8f),
            )
        }

        // Stylized playlist title printed on the CD disc body
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = cleanName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = if (cleanName.length > 12) 13.sp else 16.sp,
                    lineHeight = 17.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = palette.textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "compact disc",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.sp,
                    letterSpacing = 1.2.sp,
                ),
                color = palette.textColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
