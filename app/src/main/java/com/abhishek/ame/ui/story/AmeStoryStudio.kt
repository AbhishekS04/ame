package com.abhishek.ame.ui.story

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.abhishek.ame.R
import com.abhishek.ame.ui.glass.backdrop.Backdrop
import com.abhishek.ame.ui.player.GlassInk
import com.abhishek.ame.ui.player.GlassInkDim
import com.abhishek.ame.ui.player.RoundGlassButton
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.pageGround
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.DownloadSimple
import com.adamglin.phosphoricons.regular.InstagramLogo
import com.adamglin.phosphoricons.regular.MusicNotes
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.X
import kotlinx.coroutines.launch

/**
 * Interactive Snapchat & Instagram Story Studio modal for Ame.
 * Provides live ambient preview, lens frame styles, real artwork color resolution,
 * seamless 9:16 background gradients, synchronized video stories with sound, and 1-tap social dispatch.
 */
@Composable
fun AmeStoryStudio(
    visible: Boolean,
    info: StoryTrackInfo,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedStyle by remember { mutableStateOf(StoryFrameStyle.GLASS_CARD) }
    var selectedFormat by remember { mutableStateOf(StoryFormat.STORY_9_16) }
    var selectedPalette by remember { mutableStateOf(StoryColorPalette.AURA) }

    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // Live sampled colors from track artwork
    var topColorInt by remember { mutableStateOf(android.graphics.Color.rgb(64, 30, 42)) }
    var bottomColorInt by remember { mutableStateOf(android.graphics.Color.rgb(20, 16, 26)) }

    LaunchedEffect(info.artworkUrl, selectedPalette) {
        val (top, bottom) = StoryBitmapGenerator.resolveTrackColors(context, info, selectedPalette)
        topColorInt = top
        bottomColorInt = bottom
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isGenerating,
            dismissOnClickOutside = !isGenerating,
        ),
    ) {
        // Fullscreen immersive dark scrim - isolates the studio and hides underlying player completely
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF50A0B0E)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. Top Header Row (Snapchat Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundGlassButton(
                        backdrop = backdrop,
                        size = 40.dp,
                        onClick = { if (!isGenerating) onDismiss() },
                    ) {
                        Icon(
                            PhosphorIcons.Regular.X,
                            contentDescription = stringResource(R.string.close),
                            tint = GlassInk,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_app_mark),
                                contentDescription = null,
                                tint = GlassInk,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.story_studio),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GlassInk,
                            )
                        }
                        Text(
                            text = "Snapchat & Instagram ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassInkDim,
                        )
                    }

                    // Ratio quick-toggle chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .clickable {
                                selectedFormat = when (selectedFormat) {
                                    StoryFormat.STORY_9_16 -> StoryFormat.SQUARE_1_1
                                    StoryFormat.SQUARE_1_1 -> StoryFormat.PORTRAIT_4_5
                                    StoryFormat.PORTRAIT_4_5 -> StoryFormat.STORY_9_16
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = when (selectedFormat) {
                                StoryFormat.STORY_9_16 -> "9:16"
                                StoryFormat.SQUARE_1_1 -> "1:1"
                                StoryFormat.PORTRAIT_4_5 -> "4:5"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = GlassInk,
                        )
                    }
                }

                // 2. Interactive Canvas Live Preview (Full Ambient Backdrop)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.06f))
                            ),
                            RoundedCornerShape(28.dp)
                        )
                        .shadow(24.dp, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    LiveStoryCanvasPreview(
                        info = info,
                        style = selectedStyle,
                        topColor = Color(topColorInt),
                        bottomColor = Color(bottomColorInt),
                    )
                }

                Spacer(Modifier.height(18.dp))

                // 3. Snapchat Lens / Frame Style Selector
                Text(
                    text = "LENS & FRAME STYLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GlassInkDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StyleChip(
                        label = stringResource(R.string.style_glass_card),
                        selected = selectedStyle == StoryFrameStyle.GLASS_CARD,
                        onClick = { selectedStyle = StoryFrameStyle.GLASS_CARD },
                    )
                    StyleChip(
                        label = stringResource(R.string.style_vinyl_flex),
                        selected = selectedStyle == StoryFrameStyle.VINYL_FLEX,
                        onClick = { selectedStyle = StoryFrameStyle.VINYL_FLEX },
                    )
                    StyleChip(
                        label = stringResource(R.string.style_glass_pill),
                        selected = selectedStyle == StoryFrameStyle.GLASS_PILL,
                        onClick = { selectedStyle = StoryFrameStyle.GLASS_PILL },
                    )
                    StyleChip(
                        label = stringResource(R.string.style_hero_poster),
                        selected = selectedStyle == StoryFrameStyle.HERO_POSTER,
                        onClick = { selectedStyle = StoryFrameStyle.HERO_POSTER },
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 4. Color Palette Selector
                Text(
                    text = "AMBIENT PALETTE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GlassInkDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StyleChip(
                        label = stringResource(R.string.color_aura),
                        selected = selectedPalette == StoryColorPalette.AURA,
                        onClick = { selectedPalette = StoryColorPalette.AURA },
                    )
                    StyleChip(
                        label = stringResource(R.string.color_truedark),
                        selected = selectedPalette == StoryColorPalette.TRUE_DARK,
                        onClick = { selectedPalette = StoryColorPalette.TRUE_DARK },
                    )
                    StyleChip(
                        label = stringResource(R.string.color_midnight),
                        selected = selectedPalette == StoryColorPalette.MIDNIGHT,
                        onClick = { selectedPalette = StoryColorPalette.MIDNIGHT },
                    )
                    StyleChip(
                        label = stringResource(R.string.color_sunset),
                        selected = selectedPalette == StoryColorPalette.SUNSET,
                        onClick = { selectedPalette = StoryColorPalette.SUNSET },
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 5. Social Dispatch Buttons
                // Row 1: Direct Socials (Snapchat & Instagram)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Snapchat Button (Iconic Yellow)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFFFFC00))
                            .clickable(enabled = !isGenerating) {
                                scope.launch {
                                    isGenerating = true
                                    statusText = "Rendering Snapchat story…"
                                    val poster = StoryBitmapGenerator.renderStoryImage(
                                        context = context,
                                        info = info,
                                        style = selectedStyle,
                                        format = selectedFormat,
                                        paletteScheme = selectedPalette,
                                        stickerOnly = false,
                                    )
                                    val file = StoryBitmapGenerator.saveStoryBitmapToFile(
                                        context, poster, "ame_snap_${System.currentTimeMillis()}.png"
                                    )
                                    val shared = StoryShareHelper.shareToSnapchat(
                                        context = context,
                                        file = file,
                                        isVideo = false,
                                        title = "${info.title} • ${info.artist}",
                                    )
                                    if (!shared && !StoryShareHelper.isSnapchatInstalled(context)) {
                                        Toast.makeText(context, context.getString(R.string.snapchat_not_installed), Toast.LENGTH_SHORT).show()
                                    }
                                    isGenerating = false
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                PhosphorIcons.Regular.ShareNetwork,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.share_to_snapchat),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                            )
                        }
                    }

                    // Instagram Story Button (Vibrant Gradient)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF833AB4),
                                        Color(0xFFFD1D1D),
                                        Color(0xFFF77737),
                                    )
                                )
                            )
                            .clickable(enabled = !isGenerating) {
                                scope.launch {
                                    isGenerating = true
                                    statusText = context.getString(R.string.generating_story)

                                    // Render transparent sticker with pure drop shadow
                                    val sticker = StoryBitmapGenerator.renderStoryImage(
                                        context = context,
                                        info = info,
                                        style = selectedStyle,
                                        format = selectedFormat,
                                        paletteScheme = selectedPalette,
                                        stickerOnly = true,
                                    )
                                    val file = StoryBitmapGenerator.saveStoryBitmapToFile(
                                        context, sticker, "ame_story_sticker.png"
                                    )

                                    // Resolve rich hex colors directly from artwork
                                    val (topColor, bottomColor) = StoryBitmapGenerator.resolveTrackColors(
                                        context, info, selectedPalette
                                    )
                                    val topHex = String.format("#%06X", (0xFFFFFF and topColor))
                                    val bottomHex = String.format("#%06X", (0xFFFFFF and bottomColor))

                                    StoryShareHelper.shareToInstagramStory(
                                        context = context,
                                        stickerFile = file,
                                        topColorHex = topHex,
                                        bottomColorHex = bottomHex,
                                        contentUrl = "https://ame.app",
                                    )
                                    isGenerating = false
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                PhosphorIcons.Regular.InstagramLogo,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.share_to_instagram),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Row 2: Secondary Options (Story with Sound & Save/Share Poster)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Option A: Story with Sound (15s Video Clip with Real Music)
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Ink)
                            .clickable(enabled = !isGenerating) {
                                scope.launch {
                                    isGenerating = true
                                    statusText = context.getString(R.string.generating_video)
                                    val poster = StoryBitmapGenerator.renderStoryImage(
                                        context = context,
                                        info = info,
                                        style = selectedStyle,
                                        format = StoryFormat.STORY_9_16,
                                        paletteScheme = selectedPalette,
                                        stickerOnly = false,
                                    )
                                    val videoFile = StoryVideoGenerator.generateStoryVideo(
                                        context = context,
                                        frameBitmap = poster,
                                        info = info,
                                    )
                                    StoryShareHelper.shareStoryVideo(
                                        context = context,
                                        videoFile = videoFile,
                                        title = "${info.title} • ${info.artist}",
                                    )
                                    isGenerating = false
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                PhosphorIcons.Regular.MusicNotes,
                                contentDescription = null,
                                tint = pageGround(),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.share_video_audio),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = pageGround(),
                            )
                        }
                    }

                    // Save / Share Image Poster
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                            .clickable(enabled = !isGenerating) {
                                scope.launch {
                                    isGenerating = true
                                    statusText = "Saving poster…"
                                    val poster = StoryBitmapGenerator.renderStoryImage(
                                        context = context,
                                        info = info,
                                        style = selectedStyle,
                                        format = selectedFormat,
                                        paletteScheme = selectedPalette,
                                        stickerOnly = false,
                                    )
                                    val file = StoryBitmapGenerator.saveStoryBitmapToFile(
                                        context, poster, "ame_poster_${System.currentTimeMillis()}.png"
                                    )
                                    StoryShareHelper.shareImage(
                                        context = context,
                                        imageFile = file,
                                        title = "${info.title} • ${info.artist}",
                                    )
                                    Toast.makeText(context, "Poster saved to cache", Toast.LENGTH_SHORT).show()
                                    isGenerating = false
                                    onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                PhosphorIcons.Regular.DownloadSimple,
                                contentDescription = null,
                                tint = GlassInk,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Save / Share",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = GlassInk,
                            )
                        }
                    }
                }

                // Loading Status Indicator
                AnimatedVisibility(
                    visible = isGenerating,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = GlassInk,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassInk,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live Compose preview replicating the exact story output dynamically on device.
 */
@Composable
private fun LiveStoryCanvasPreview(
    info: StoryTrackInfo,
    style: StoryFrameStyle,
    topColor: Color,
    bottomColor: Color,
) {
    val bgBrush = Brush.verticalGradient(
        listOf(topColor, bottomColor)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            StoryFrameStyle.GLASS_CARD -> {
                // The centerpiece card
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF121217).copy(alpha = 0.90f))
                        .border(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.08f))
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(14.dp),
                ) {
                    Column {
                        AsyncImage(
                            model = info.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            info.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            info.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_app_mark),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "ame",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            if (info.isLossless) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_apple_lossless),
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(width = 14.dp, height = 9.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Lossless",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            StoryFrameStyle.VINYL_FLEX -> {
                // Vinyl sliding out from sleeve
                Box(
                    modifier = Modifier.fillMaxWidth(0.90f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Vinyl Disc (Right half)
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141416))
                            .border(1.5.dp, Color(0xFF2C2C30), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Grooves
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF28282C), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF323238), CircleShape)
                        )
                        // Center label
                        AsyncImage(
                            model = info.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape),
                        )
                    }

                    // Album Sleeve (Left)
                    Column {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                        ) {
                            AsyncImage(
                                model = info.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121217).copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Column {
                                Text(
                                    info.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    info.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.70f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            StoryFrameStyle.GLASS_PILL -> {
                // Ultra-minimal capsule pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF14151B).copy(alpha = 0.90f))
                        .border(1.2.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(32.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = info.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                info.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                info.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.70f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_app_mark),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            StoryFrameStyle.HERO_POSTER -> {
                // Full cinematic poster
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                ) {
                    AsyncImage(
                        model = info.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Bottom gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.45f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                )
                            ),
                    )
                    // Caption
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                    ) {
                        Text(
                            info.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            info.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_app_mark),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "ame",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter chip for styles, formats, and palettes with high-contrast active state.
 */
@Composable
private fun StyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) Ink else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(180),
        label = "chipBg",
    )
    val textColor = if (selected) pageGround() else GlassInk

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
        )
    }
}
