package com.abhishek.ame.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.PushPin
import com.adamglin.phosphoricons.regular.Rows
import com.adamglin.phosphoricons.regular.SquaresFour
import com.abhishek.ame.R
import com.abhishek.ame.data.CatalogPlaylist
import com.abhishek.ame.ui.components.Artwork
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import com.abhishek.ame.ui.theme.rememberArtworkPalette
import com.abhishek.ame.ui.theme.softShadow
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 3D Vinyl Crate Card — 60fps "md-vinyl-grid-to-3d-album-list-transition".
 *
 * Each card features:
 *  1. An extruded top spine bar facing directly forward toward the viewer with title & artist.
 *  2. An album artwork jacket sloping downward and backward into 3D space (rotationX ~50°).
 *  3. Overlapping vertical stride (~68dp) creating the dense record crate flipping view.
 */
@Composable
fun VinylStackCard(
    playlist: CatalogPlaylist,
    index: Int,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Vertical stride (distance from this card's top to the next card's top). */
    stride: Dp = 68.dp,
    /** Full jacket height before tilt foreshortening. */
    cardHeight: Dp = 196.dp,
    /** 3D backward tilt angle in degrees. */
    tiltAngle: Float = 50f,
) {
    val density = LocalDensity.current.density
    val palette by rememberArtworkPalette(playlist.artworkUrl)

    // Dynamic spine tinting matching the album artwork palette or deterministic rich tones
    val spineColor = remember(palette, playlist.name) {
        val dominant = palette.firstOrNull()
        if (dominant != null) {
            dominant.copy(alpha = 0.95f)
        } else {
            val fallbackTones = listOf(
                Color(0xFF222632), // Slate charcoal
                Color(0xFF14171E), // Deep obsidian
                Color(0xFF2C2236), // Velvet plum
                Color(0xFF382A22), // Warm espresso
                Color(0xFF182434), // Midnight navy
                Color(0xFF202E26), // Forest noir
                Color(0xFFE2E4EB), // Off-white ivory
            )
            val idx = abs(playlist.name.hashCode()) % fallbackTones.size
            fallbackTones[idx]
        }
    }

    val isLightSpine = spineColor.luminance() > 0.45f
    val titleColor = if (isLightSpine) Color(0xFF121418) else Color(0xFFF7F8FA)
    val artistColor = if (isLightSpine) Color(0xFF4B5565) else Color(0xFFB5BAC7)

    // Parse Title and Artist if formatted as "Title • Artist" or "Title - Artist"
    val (titleText, artistText) = remember(playlist.name) {
        when {
            playlist.name.contains(" • ") -> {
                val parts = playlist.name.split(" • ", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
            playlist.name.contains(" - ") -> {
                val parts = playlist.name.split(" - ", limit = 2)
                parts[0].trim() to parts[1].trim()
            }
            else -> playlist.name to ""
        }
    }

    // Spring settle entrance for 3D stack items
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val stagger = (index * 24L).coerceAtMost(240L)
        delay(stagger)
        entered = true
    }
    val animatedTilt by animateFloatAsState(
        targetValue = if (entered) tiltAngle else (tiltAngle - 12f),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
        label = "stackTilt",
    )

    // The container reports `stride` height so the next card stacks tightly over this one.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stride)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Full 3D card overflowing the stride box
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .height(cardHeight)
                .graphicsLayer {
                    rotationX = animatedTilt
                    cameraDistance = 12f * density
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    shadowElevation = 20f * density
                }
                .pressable(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    pressedScale = 0.98f,
                    onLongClick = onLongClick,
                )
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F1218))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            // 1. FRONT SPINE (Top horizontal bevel bar facing user)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(spineColor)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isLightSpine) 0.55f else 0.28f),
                                Color.Transparent,
                            ),
                        ),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                    ),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artistText.isNotBlank()) {
                    Text(
                        text = "  $artistText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.1.sp,
                        ),
                        color = artistColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pinned) {
                    Icon(
                        PhosphorIcons.Fill.PushPin,
                        contentDescription = stringResource(R.string.pinned),
                        tint = if (isLightSpine) Color(0xFF2D3748) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(11.dp),
                    )
                }
            }

            // 2. ALBUM ARTWORK JACKET (Tilted back into depth)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Artwork(
                    url = playlist.artworkUrl,
                    title = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    corner = 0.dp,
                    decodeSize = 400.dp,
                )

                // 3D Depth lighting & shadows:
                // Upper shadow cast by spine + bottom ambient shadow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Black.copy(alpha = 0.38f),
                                    0.12f to Color.Black.copy(alpha = 0.08f),
                                    0.45f to Color.Transparent,
                                    1.00f to Color.Black.copy(alpha = 0.58f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * 3-Column Flat Album Grid Tile — 60fps unfolded state.
 * Staggered 3D entrance wave rotates from 50° to 0° upon grid entry.
 */
@Composable
fun VinylGridTile(
    playlist: CatalogPlaylist,
    index: Int,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val delayMs = ((index / 3) * 32L).coerceAtMost(320L)
        delay(delayMs)
        appeared = true
    }

    val density = LocalDensity.current.density
    val tilt by animateFloatAsState(
        targetValue = if (appeared) 0f else 50f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "gridTileTilt",
    )
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "gridTileScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "gridTileAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                rotationX = tilt
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .softShadow(RoundedCornerShape(12.dp), elevation = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141720))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .pressable(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                pressedScale = 0.95f,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Artwork(
            url = playlist.artworkUrl,
            title = playlist.name,
            modifier = Modifier.fillMaxSize(),
            corner = 12.dp,
            decodeSize = 240.dp,
        )
        if (pinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PhosphorIcons.Fill.PushPin,
                    contentDescription = stringResource(R.string.pinned),
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/**
 * Single Toggle Button: 3D Vinyl Stack (4 horizontal lines) vs 3-Column Grid (4 squares).
 */
@Composable
fun VinylLayoutToggle(
    isGrid: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Ink.copy(alpha = 0.08f))
            .border(1.dp, Ink.copy(alpha = 0.14f), CircleShape)
            .clickable { onToggle(!isGrid) },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = isGrid,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(140)) + androidx.compose.animation.scaleIn(initialScale = 0.8f))
                    .togetherWith(androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.scaleOut(targetScale = 0.8f))
            },
            label = "layoutIconToggle",
        ) { grid ->
            Icon(
                imageVector = if (grid) PhosphorIcons.Regular.SquaresFour else PhosphorIcons.Regular.Rows,
                contentDescription = stringResource(R.string.change_layout),
                tint = Ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141720)
@Composable
private fun VinylStackPreview() {
    val samplePlaylists = listOf(
        CatalogPlaylist(uri = "1", name = "GNX • Kendrick Lamar", artworkUrl = null),
        CatalogPlaylist(uri = "2", name = "Virtual Reality • Jae.T", artworkUrl = null),
        CatalogPlaylist(uri = "3", name = "Like I Do • J.Tajor", artworkUrl = null),
        CatalogPlaylist(uri = "4", name = "Don't Wake Me Up • James Hype", artworkUrl = null),
        CatalogPlaylist(uri = "5", name = "It's a Man's World • Omar Wilson", artworkUrl = null),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        VinylLayoutToggle(isGrid = false, onToggle = {})
        Spacer(Modifier.height(12.dp))
        samplePlaylists.forEachIndexed { index, playlist ->
            VinylStackCard(
                playlist = playlist,
                index = index,
                pinned = index == 0,
                onClick = {},
                onLongClick = null,
            )
        }
    }
}
