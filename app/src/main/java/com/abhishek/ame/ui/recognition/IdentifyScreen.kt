package com.abhishek.ame.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.abhishek.ame.R
import com.abhishek.ame.data.CatalogTrack
import com.abhishek.ame.recognition.RecognitionState
import com.abhishek.ame.recognition.RecognizedTrack
import com.abhishek.ame.recognition.ShazamRecognizer
import com.abhishek.ame.ui.components.Artwork
import com.abhishek.ame.ui.glass.backdrop.Backdrop
import com.abhishek.ame.ui.glass.backdrop.drawBackdrop
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.ArrowsClockwise
import com.adamglin.phosphoricons.bold.Trash
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.fill.Heart
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.fill.Microphone
import com.adamglin.phosphoricons.fill.Play
import com.adamglin.phosphoricons.fill.Waveform
import android.content.Intent
import android.speech.RecognizerIntent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IdentifyScreen(
    recognizer: ShazamRecognizer,
    contentPadding: PaddingValues,
    backdrop: Backdrop,
    onPlayTrack: (RecognizedTrack) -> Unit,
    isLiked: (String, String) -> Boolean,
    onToggleLike: (String, String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            recognizer.startListening()
        }
    }

    val state by recognizer.state.collectAsState()
    val amplitude by recognizer.amplitudeRms.collectAsState()
    val history by recognizer.history.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Header
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Identify Music",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Instant 100% free acoustic recognition & lossless playback",
                    fontSize = 13.sp,
                    color = InkDim,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        // Main Identification Central Section
        item(key = "recognition_core") {
            if (!hasMicPermission) {
                PermissionRequestCard(
                    backdrop = backdrop,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            } else {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(300)) + androidx.compose.animation.scaleIn(tween(300), initialScale = 0.92f))
                            .togetherWith(fadeOut(tween(250)) + androidx.compose.animation.scaleOut(tween(250), targetScale = 0.95f))
                    },
                    label = "identificationState",
                ) { targetState ->
                    when (targetState) {
                        is RecognitionState.Idle,
                        is RecognitionState.Listening,
                        is RecognitionState.Processing -> {
                            RadarListeningOrb(
                                state = targetState,
                                amplitude = amplitude,
                                backdrop = backdrop,
                                onStartListening = { recognizer.startListening() },
                                onCancel = { recognizer.cancel() },
                            )
                        }
                        is RecognitionState.Matched -> {
                            MatchResultCard(
                                track = targetState.track,
                                backdrop = backdrop,
                                isLiked = isLiked(targetState.track.title, targetState.track.artist),
                                onToggleLike = { onToggleLike(targetState.track.title, targetState.track.artist) },
                                onPlay = { onPlayTrack(targetState.track) },
                                onIdentifyAgain = { recognizer.reset(); recognizer.startListening() },
                            )
                        }
                        is RecognitionState.NoMatch -> {
                            NoMatchCard(
                                reason = targetState.reason,
                                backdrop = backdrop,
                                onRetry = { recognizer.reset(); recognizer.startListening() },
                            )
                        }
                        is RecognitionState.Error -> {
                            ErrorCard(
                                message = targetState.message,
                                backdrop = backdrop,
                                onRetry = { recognizer.reset(); recognizer.startListening() },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }

        // On-Device Recognition History Shelf
        if (history.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Discoveries",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    IconButton(
                        onClick = { recognizer.clearHistory() },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            PhosphorIcons.Bold.Trash,
                            contentDescription = "Clear History",
                            tint = InkDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            items(history, key = { "${it.timestamp}_${it.title}" }) { track ->
                HistoryTrackRow(
                    track = track,
                    onPlay = { onPlayTrack(track) },
                    onRemove = { recognizer.removeHistoryItem(track) },
                )
            }
        }
    }
}

@Composable
private fun RadarListeningOrb(
    state: RecognitionState,
    amplitude: Float,
    backdrop: Backdrop,
    onStartListening: () -> Unit,
    onCancel: () -> Unit,
) {
    val isListening = state is RecognitionState.Listening
    val isProcessing = state is RecognitionState.Processing

    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleBreathing",
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "processingSpin",
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isListening) 1f + amplitude * 0.16f else if (isProcessing) 1.05f else idlePulse,
        animationSpec = spring(stiffness = 350f, dampingRatio = 0.75f),
        label = "orbScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) {
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Animated concentric radar ripples during listening
            if (isListening) {
                for (i in 1..3) {
                    val ripplePhase by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2400, delayMillis = i * 650, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "ripple_$i",
                    )
                    val ringRadius = 80.dp + (45.dp * ripplePhase)
                    val ringAlpha = (1f - ripplePhase) * (0.4f + amplitude * 0.5f)
                    Box(
                        modifier = Modifier
                            .size(ringRadius * 2)
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFF8B5CF6).copy(alpha = ringAlpha.coerceIn(0f, 1f)),
                                shape = CircleShape,
                            )
                    )
                }
            }

            // Outer processing halo
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .rotate(spinAngle)
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF8B5CF6).copy(alpha = 0.9f),
                                    Color(0xFF06B6D4).copy(alpha = 0.8f),
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent,
                                )
                            ),
                            shape = CircleShape,
                        )
                )
            }

            // Central Liquid Glass Orb (160dp)
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(animatedScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isListening) {
                                listOf(
                                    Color(0xFF8B5CF6).copy(alpha = 0.45f + amplitude * 0.35f),
                                    Color(0xFF6D28D9).copy(alpha = 0.30f),
                                    Color.White.copy(alpha = 0.08f),
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Black.copy(alpha = 0.12f),
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.12f),
                            )
                        ),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isListening || isProcessing) onCancel() else onStartListening()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = state,
                    animationSpec = tween(250),
                    label = "orbIcon",
                ) { s ->
                    when (s) {
                        is RecognitionState.Listening -> {
                            Icon(
                                PhosphorIcons.Fill.Waveform,
                                contentDescription = "Listening",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(62.dp)
                                    .scale(1f + amplitude * 0.25f),
                            )
                        }
                        is RecognitionState.Processing -> {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(52.dp),
                            )
                        }
                        else -> {
                            Icon(
                                PhosphorIcons.Fill.Waveform,
                                contentDescription = "Tap to identify",
                                tint = Ink,
                                modifier = Modifier.size(62.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // State indicator text
        Text(
            text = when (state) {
                is RecognitionState.Listening -> "Listening for music..."
                is RecognitionState.Processing -> "Identifying audio signature..."
                else -> "Tap to Identify"
            },
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            letterSpacing = (-0.3).sp,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = when (state) {
                is RecognitionState.Listening -> "Keep the phone steady near the audio source"
                is RecognitionState.Processing -> "Matching against Shazam's 100M+ song database"
                else -> "Identify songs playing in the room, on radio, or anywhere"
            },
            fontSize = 13.sp,
            color = InkDim,
            textAlign = TextAlign.Center,
        )

        if (state is RecognitionState.Idle) {
            val context = LocalContext.current
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable {
                        val intent = Intent("com.google.android.googlequicksearchbox.action.SOUND_SEARCH").apply {
                            setPackage("com.google.android.googlequicksearchbox")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val fallback = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra("android.speech.extra.SEARCH_MUSIC", true)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { runCatching { context.startActivity(fallback) } }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    PhosphorIcons.Fill.Microphone,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Humming a song? Tap for Google Sound Search",
                    fontSize = 12.sp,
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MatchResultCard(
    track: RecognizedTrack,
    backdrop: Backdrop,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onPlay: () -> Unit,
    onIdentifyAgain: () -> Unit,
) {
    var liked by remember(track.title, track.artist) { mutableStateOf(isLiked) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // High-Resolution Album Art Card
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(22.dp),
                )
                .background(Color.Black.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = track.coverUrl,
                title = track.title,
                modifier = Modifier.fillMaxSize(),
                corner = 22.dp,
                decodeSize = 200.dp,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = track.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = (-0.5).sp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = track.artist,
            fontSize = 17.sp,
            color = InkDim,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(14.dp))

        // State validation badge: "In Liked Songs" vs "Add to Library"
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (liked) Color(0xFFF43F5E).copy(alpha = 0.14f)
                    else Color.White.copy(alpha = 0.08f)
                )
                .border(
                    width = 0.75.dp,
                    color = if (liked) Color(0xFFF43F5E).copy(alpha = 0.35f)
                            else Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable {
                    liked = !liked
                    onToggleLike()
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (liked) PhosphorIcons.Fill.Heart else PhosphorIcons.Regular.Heart,
                contentDescription = null,
                tint = if (liked) Color(0xFFF43F5E) else InkDim,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (liked) "Saved in Library" else "Save to Library",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (liked) Color(0xFFF43F5E) else InkDim,
            )
        }

        Spacer(Modifier.height(22.dp))

        // Action Row: "Play in Lossless" + Heart Favorite Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // High-Impact Liquid Glass Action: "Play in Lossless"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.White,
                                Color(0xFFE2E8F0),
                            )
                        )
                    )
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        PhosphorIcons.Fill.Play,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Play in Lossless",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    )
                }
            }

            // Quick Heart Like Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (liked) Color(0xFFF43F5E).copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.10f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (liked) Color(0xFFF43F5E).copy(alpha = 0.40f)
                                else Color.White.copy(alpha = 0.20f),
                        shape = CircleShape,
                    )
                    .clickable {
                        liked = !liked
                        onToggleLike()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (liked) PhosphorIcons.Fill.Heart else PhosphorIcons.Regular.Heart,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) Color(0xFFF43F5E) else Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // "Identify Another Song" Secondary Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(24.dp),
                )
                .clickable(onClick = onIdentifyAgain),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Identify Another",
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NoMatchCard(
    reason: String,
    backdrop: Backdrop,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Fill.Waveform,
                contentDescription = null,
                tint = InkDim,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "No Match Found",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "We couldn't recognize this track. Try holding your phone closer to the speakers and tap Try Again.",
            fontSize = 14.sp,
            color = InkDim,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .height(46.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(23.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    PhosphorIcons.Bold.ArrowsClockwise,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Try Again",
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    backdrop: Backdrop,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Recognition Error",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = InkDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Retry", color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionRequestCard(
    backdrop: Backdrop,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PhosphorIcons.Fill.Microphone,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(46.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Microphone Access Required",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Ame needs microphone access to listen to ambient music and generate real-time acoustic fingerprints. Audio is never stored or sent off-device.",
            fontSize = 14.sp,
            color = InkDim,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color.White)
                .clickable(onClick = onRequestPermission),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Allow Microphone",
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HistoryTrackRow(
    track: RecognizedTrack,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val dateStr = remember(track.timestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(track.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = track.coverUrl,
            title = track.title,
            modifier = Modifier.size(50.dp),
            corner = 12.dp,
            decodeSize = 50.dp,
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    fontSize = 13.sp,
                    color = InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " • $dateStr",
                    fontSize = 11.sp,
                    color = InkDim.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }

        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                PhosphorIcons.Fill.Play,
                contentDescription = "Play",
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
