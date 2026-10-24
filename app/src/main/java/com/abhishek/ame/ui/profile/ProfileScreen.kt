package com.abhishek.ame.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.abhishek.ame.R
import com.abhishek.ame.data.CatalogPlaylist
import com.abhishek.ame.data.ListeningEvent
import com.abhishek.ame.ui.components.Artwork
import com.abhishek.ame.ui.components.ProfilePicture
import com.abhishek.ame.ui.glass.LiquidButton
import com.abhishek.ame.ui.glass.backdrop.Backdrop
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import com.abhishek.ame.ui.theme.pageGround
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Waveform
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.Camera
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.Eye
import com.adamglin.phosphoricons.regular.EyeSlash
import com.adamglin.phosphoricons.regular.Globe
import com.adamglin.phosphoricons.regular.ImageSquare
import com.adamglin.phosphoricons.regular.Lock
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.ShareNetwork
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.User
import com.adamglin.phosphoricons.regular.UserPlus
import com.adamglin.phosphoricons.regular.X
import com.abhishek.ame.data.social.JamTrack
import java.io.File

/**
 * Dedicated Social Profile Page for Ame.
 * Faithful to modern aesthetic standards: full-bleed atmospheric background banner with
 * smooth progressive fade, centered circular avatar, floating achievement badges,
 * rich stats row, GitHub-style streak heatmap, and public playlists.
 */
@Composable
fun ProfileScreen(
    accountName: String?,
    defaultAvatarUrl: String?,
    customBannerPath: String?,
    customAvatarPath: String?,
    customBannerCloudUrl: String? = null,
    customAvatarCloudUrl: String? = null,
    customDisplayName: String? = null,
    customUsername: String? = null,
    bio: String,
    isPrivate: Boolean = false,
    followersCount: Int = 0,
    followingCount: Int = 0,
    listeningEvents: List<ListeningEvent>,
    publicPlaylists: List<CatalogPlaylist> = emptyList(),
    visitedProfile: com.abhishek.ame.data.social.SocialProfile? = null,
    currentUserId: String? = null,
    activeJam: JamTrack? = null,
    onJoinJam: ((JamTrack) -> Unit)? = null,
    onToggleFollow: (String) -> Unit = {},
    contentPadding: PaddingValues,
    backdrop: Backdrop,
    accentColor: Color,
    onPickBanner: (Uri) -> Unit = {},
    onPickAvatar: (Uri) -> Unit = {},
    onSaveDisplayName: (String) -> Unit = {},
    onSaveUsername: (String) -> Unit = {},
    onCheckUsernameAvailability: (String) -> String? = { null },
    onSaveBio: (String) -> Unit = {},
    onSetPrivate: (Boolean) -> Unit = {},
    onOpenPlaylist: (CatalogPlaylist) -> Unit = {},
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isSelf = visitedProfile != null && (
        (currentUserId != null && visitedProfile.id == currentUserId) ||
        visitedProfile.username.equals(customUsername?.removePrefix("@"), ignoreCase = true) ||
        (customDisplayName != null && visitedProfile.displayName.equals(customDisplayName, ignoreCase = true))
    )
    val isVisiting = visitedProfile != null && !isSelf
    val context = LocalContext.current

    val dailyListening = remember(listeningEvents) {
        StreakEngine.computeDailyListening(listeningEvents)
    }

    val displayName = visitedProfile?.displayName
        ?: customDisplayName?.ifBlank { null }
        ?: accountName?.ifBlank { null }
        ?: "Abhishek Singh"

    val usernameHandle = visitedProfile?.let { "@${it.username}" }
        ?: customUsername?.let { "@$it" }
        ?: remember(accountName) {
            val slug = accountName?.trim()?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") ?: "abhishek"
            "@$slug"
        }

    val effectiveBio = visitedProfile?.bio ?: bio
    val effectiveIsPrivate = visitedProfile?.isPrivate ?: isPrivate
    val effectiveFollowers = visitedProfile?.followersCount ?: followersCount
    val effectiveFollowing = visitedProfile?.followingCount ?: followingCount
    val effectivePlaylists = visitedProfile?.publicPlaylists ?: publicPlaylists
    val isFollowing = visitedProfile?.isFollowing == true

    var isPreviewMode by rememberSaveable { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val isLocked = if (isVisiting) {
        effectiveIsPrivate && !isFollowing
    } else {
        isPreviewMode && effectiveIsPrivate
    }

    // Photo pickers for banner and avatar
    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPickBanner) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPickAvatar) }

    var editedName by remember(displayName, showEditProfileDialog) { mutableStateOf(displayName) }
    var editedUsername by remember(usernameHandle, showEditProfileDialog) {
        mutableStateOf(usernameHandle.removePrefix("@"))
    }
    var editedBio by remember(bio, showEditProfileDialog) { mutableStateOf(bio) }
    var editedPrivate by remember(isPrivate, showEditProfileDialog) { mutableStateOf(isPrivate) }

    val usernameError = remember(editedUsername) {
        if (editedUsername.isNotBlank()) onCheckUsernameAvailability(editedUsername) else null
    }
    val isUsernameAvailable = remember(editedUsername, usernameError) {
        editedUsername.length >= 3 && usernameError == null
    }

    // Comprehensive & Sophisticated Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        PhosphorIcons.Regular.User,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text("Edit Profile", fontWeight = FontWeight.Bold, color = Ink)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Photos Picker Row: Avatar & Cover actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .pressable(onClick = {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    PhosphorIcons.Regular.Camera,
                                    contentDescription = null,
                                    tint = Ink,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Avatar",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Ink,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.75.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .pressable(onClick = {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    PhosphorIcons.Regular.ImageSquare,
                                    contentDescription = null,
                                    tint = Ink,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Cover",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Ink,
                                )
                            }
                        }
                    }

                    // Display Name field
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Username field with instant live availability detection
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editedUsername,
                            onValueChange = { input ->
                                val clean = input.removePrefix("@").lowercase().replace(Regex("[^a-z0-9_]"), "").take(20)
                                editedUsername = clean
                            },
                            label = { Text("Username") },
                            prefix = { Text("@", color = InkDim) },
                            singleLine = true,
                            trailingIcon = {
                                if (editedUsername.length >= 3) {
                                    if (isUsernameAvailable) {
                                        Icon(
                                            PhosphorIcons.Regular.Check,
                                            contentDescription = "Available",
                                            tint = Color(0xFF22C55E),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    } else {
                                        Icon(
                                            PhosphorIcons.Regular.X,
                                            contentDescription = "Unavailable",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            if (editedUsername.isEmpty()) {
                                Text(
                                    text = "3–20 characters, lowercase letters, numbers & _",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = InkDim,
                                )
                            } else if (isUsernameAvailable) {
                                Text(
                                    text = "✓ @$editedUsername is available",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFF22C55E),
                                )
                            } else if (usernameError != null) {
                                Text(
                                    text = "✕ $usernameError",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFFEF4444),
                                )
                            }
                        }
                    }

                    // Music Tagline / Bio field
                    OutlinedTextField(
                        value = editedBio,
                        onValueChange = { editedBio = it },
                        label = { Text("Music tagline / bio") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Private / Public Account Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    if (editedPrivate) PhosphorIcons.Regular.Lock else PhosphorIcons.Regular.Globe,
                                    contentDescription = null,
                                    tint = if (editedPrivate) accentColor else InkDim,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = if (editedPrivate) "Private Account" else "Public Account",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Ink,
                                )
                            }
                            Text(
                                text = if (editedPrivate) {
                                    "Only followers can view streaks & playlists."
                                } else {
                                    "Anyone can view streaks & public playlists."
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = InkDim,
                            )
                        }
                        Switch(
                            checked = editedPrivate,
                            onCheckedChange = { editedPrivate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = InkDim,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                val canSave = editedUsername.isEmpty() || isUsernameAvailable || editedUsername == usernameHandle.removePrefix("@")
                TextButton(
                    onClick = {
                        if (editedName.isNotBlank()) onSaveDisplayName(editedName)
                        if (canSave && editedUsername.isNotBlank()) onSaveUsername(editedUsername)
                        onSaveBio(editedBio)
                        onSetPrivate(editedPrivate)
                        showEditProfileDialog = false
                    },
                    enabled = canSave,
                ) {
                    Text(
                        "Save",
                        color = if (canSave) accentColor else InkDim.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = InkDim)
                }
            },
            containerColor = pageGround(),
        )
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize().background(pageGround())) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = contentPadding.calculateBottomPadding() + 48.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hero Banner & Centered Avatar Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp)
                        .then(
                            if (!isVisiting && !isPreviewMode) {
                                Modifier.pressable(onClick = {
                                    bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                            } else Modifier
                        ),
                ) {
                    // Background Image / Cinematic Gradient
                    if (isVisiting) {
                        if (!visitedProfile?.bannerUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = visitedProfile!!.bannerUrl,
                                contentDescription = "Profile Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // Atmospheric crimson / obsidian cinematic radial gradient matching reference
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF9E1B1B),
                                                Color(0xFF5A0C0C),
                                                Color(0xFF220505),
                                                pageGround(),
                                            ),
                                            center = Offset(700f, 250f),
                                            radius = 850f,
                                        ),
                                    ),
                            )
                        }
                    } else {
                        val bannerModel = remember(isVisiting, visitedProfile?.bannerUrl, customBannerPath, customBannerCloudUrl) {
                            when {
                                isVisiting -> visitedProfile?.bannerUrl
                                customBannerPath != null && File(customBannerPath).exists() -> File(customBannerPath)
                                !customBannerCloudUrl.isNullOrBlank() -> customBannerCloudUrl
                                else -> null
                            }
                        }

                        if (bannerModel != null) {
                            val bannerRequest = remember(bannerModel) {
                                ImageRequest.Builder(context)
                                    .data(bannerModel)
                                    .crossfade(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = bannerRequest,
                                contentDescription = "Profile Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // Atmospheric crimson / obsidian cinematic radial gradient matching reference
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF9E1B1B),
                                                Color(0xFF5A0C0C),
                                                Color(0xFF220505),
                                                pageGround(),
                                            ),
                                            center = Offset(700f, 250f),
                                            radius = 850f,
                                        ),
                                    ),
                            )
                        }
                    }

                    // Flawless Vertical Transition Gradient Fade into pageGround
                    val currentGround = pageGround()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.35f),
                                        0.35f to Color.Transparent,
                                        0.60f to currentGround.copy(alpha = 0.45f),
                                        0.78f to currentGround.copy(alpha = 0.85f),
                                        1.0f to currentGround,
                                    ),
                                ),
                            ),
                    )

                    // Centered Circular Profile Avatar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(102.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                .then(
                                    if (!isVisiting && !isPreviewMode) {
                                        Modifier.pressable(onClick = {
                                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        })
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val avatarModel = remember(isVisiting, visitedProfile?.avatarUrl, customAvatarPath, customAvatarCloudUrl, defaultAvatarUrl) {
                                when {
                                    isVisiting -> visitedProfile?.avatarUrl ?: defaultAvatarUrl
                                    customAvatarPath != null && File(customAvatarPath).exists() -> File(customAvatarPath)
                                    !customAvatarCloudUrl.isNullOrBlank() -> customAvatarCloudUrl
                                    !defaultAvatarUrl.isNullOrBlank() -> defaultAvatarUrl
                                    else -> null
                                }
                            }

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
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            } else {
                                ProfilePicture(
                                    url = null,
                                    name = displayName,
                                    size = 96.dp,
                                    decodeSize = 96.dp,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            }
                        }

                        // Little Camera Badge in bottom right of avatar (owner view only)
                        if (!isVisiting && !isPreviewMode) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(accentColor)
                                    .border(2.dp, pageGround(), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    PhosphorIcons.Regular.Camera,
                                    contentDescription = "Edit Avatar",
                                    tint = pageGround(),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            // User Identity, Stats & Bio Area (Centered)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))

                    // Full Name
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.4).sp,
                        ),
                        color = Ink,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(4.dp))

                    // Username with slightly lower opacity directly under full name
                    Text(
                        text = usernameHandle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        color = InkDim.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )

                    // Private Account Pill indicator for owner
                    if (!isVisiting && !isPreviewMode && effectiveIsPrivate) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Lock,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Private Profile",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = InkDim,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Followers · Following · Playlists
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "$effectiveFollowers Followers",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim.copy(alpha = 0.5f),
                        )
                        Text(
                            text = "$effectiveFollowing Following",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim.copy(alpha = 0.5f),
                        )
                        Text(
                            text = "${effectivePlaylists.size} ${if (effectivePlaylists.size == 1) "Playlist" else "Playlists"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Bio / Tagline
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .then(
                                if (!isVisiting && !isPreviewMode) {
                                    Modifier.pressable(onClick = { showEditProfileDialog = true })
                                } else Modifier
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = effectiveBio.ifBlank { if (!isVisiting && !isPreviewMode) "Add a bio to your profile..." else "" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = Ink.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!isVisiting && !isPreviewMode) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                PhosphorIcons.Regular.PencilSimple,
                                contentDescription = "Edit Bio",
                                tint = InkDim,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }

                    // Live Jam / Listen Along banner (Discord style)
                    if (activeJam != null && activeJam.isPlaying && activeJam.trackTitle.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1DB954).copy(alpha = 0.12f))
                                .border(0.75.dp, Color(0xFF1DB954).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f, fill = false),
                                ) {
                                    Icon(
                                        PhosphorIcons.Fill.Waveform,
                                        contentDescription = null,
                                        tint = Color(0xFF1DB954),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column {
                                        Text(
                                            text = "LISTENING ALONG",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                            ),
                                            color = Color(0xFF1DB954),
                                        )
                                        Text(
                                            text = "${activeJam.trackTitle} • ${activeJam.artistName}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                if (isVisiting && onJoinJam != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF1DB954))
                                            .pressable(onClick = { onJoinJam(activeJam) })
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Join Jam",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.Black,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action Buttons Row: Visitor View vs Owner mode vs Visitor Preview mode
                    if (isVisiting) {
                        // Real Visitor View: Liquid [Follow] / [Following] Button + Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LiquidButton(
                                onClick = { onToggleFollow(visitedProfile!!.id) },
                                backdrop = backdrop,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(if (isFollowing) Color.White.copy(alpha = 0.12f) else accentColor)
                                    .then(
                                        if (isFollowing) Modifier.border(0.75.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(21.dp))
                                        else Modifier
                                    ),
                                contentHeight = 42.dp,
                                contentPadding = 0.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        if (isFollowing) PhosphorIcons.Regular.Check else PhosphorIcons.Regular.UserPlus,
                                        contentDescription = null,
                                        tint = if (isFollowing) Ink else Color.Black,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = if (isFollowing) "Following" else "Follow",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isFollowing) Ink else Color.Black,
                                    )
                                }
                            }

                            // Share Profile Button
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .pressable(onClick = {
                                        val handleClean = usernameHandle.removePrefix("@")
                                        val shareText = "Check out $displayName's music profile on Ame: https://ame.app/user/$handleClean"
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Profile"))
                                    }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    PhosphorIcons.Regular.ShareNetwork,
                                    contentDescription = "Share",
                                    tint = Ink,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else if (!isPreviewMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Edit Profile Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(21.dp))
                                    .pressable(onClick = { showEditProfileDialog = true }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        PhosphorIcons.Regular.User,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = "Edit Profile",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Ink,
                                    )
                                }
                            }

                            // Preview Button (View as Visitor)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(accentColor.copy(alpha = 0.18f))
                                    .border(0.75.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(21.dp))
                                    .pressable(onClick = { isPreviewMode = true }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        PhosphorIcons.Regular.Eye,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = "Preview",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Ink,
                                    )
                                }
                            }

                            // Share Profile Button
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(0.75.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .pressable(onClick = {
                                        val handleClean = usernameHandle.removePrefix("@")
                                        val shareText = "Check out $displayName's music profile on Ame: https://ame.app/user/$handleClean"
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Profile"))
                                    }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    PhosphorIcons.Regular.ShareNetwork,
                                    contentDescription = "Share",
                                    tint = Ink,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else {
                        // Visitor Preview Mode Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Mock Follow Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(accentColor)
                                    .pressable(onClick = { /* simulated follow action */ }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        PhosphorIcons.Regular.UserPlus,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Follow",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.Black,
                                    )
                                }
                            }

                            // Exit Preview Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(0.75.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(21.dp))
                                    .pressable(onClick = { isPreviewMode = false }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        PhosphorIcons.Regular.EyeSlash,
                                        contentDescription = null,
                                        tint = Ink,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Exit Preview",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Ink,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Streak Heatmap Section or Locked Privacy Notice
            if (isLocked) {
                // Private Profile Locked Card for Visitors
                item {
                    Spacer(Modifier.height(32.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Lock,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "This Account is Private",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Ink,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Follow this account to see their listening streaks and public playlists.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = InkDim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                // Streak Heatmap Section (GitHub style)
                item {
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        StreakHeatmap(
                            dailyListening = dailyListening,
                            accentColor = accentColor,
                        )
                    }
                }

                // Public Playlists Underneath
                item {
                    Spacer(Modifier.height(24.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "PUBLIC PLAYLISTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = InkDim,
                        )
                        Spacer(Modifier.height(10.dp))

                        if (effectivePlaylists.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                effectivePlaylists.forEach { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                            .pressable(onClick = { onOpenPlaylist(playlist) })
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Artwork(
                                            url = playlist.artworkUrl,
                                            title = playlist.name,
                                            modifier = Modifier.size(52.dp),
                                            corner = 8.dp,
                                            decodeSize = 52.dp,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = Ink,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = playlist.subtitle ?: "Playlist",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = InkDim,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isVisiting) "No public playlists yet." else "Playlists you make public will show up here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = InkDim,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Frosted Glass Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Close / Back button
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .border(0.75.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentHeight = 40.dp,
                contentPadding = 0.dp,
            ) {
                Icon(
                    PhosphorIcons.Regular.ArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Visitor Mode Exit Pill
            if (!isVisiting && isPreviewMode) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.75.dp, accentColor.copy(alpha = 0.60f), RoundedCornerShape(20.dp))
                        .pressable(onClick = { isPreviewMode = false })
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            PhosphorIcons.Regular.Eye,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Visitor Mode • Exit",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Ink,
                        )
                    }
                }
            }
        }
    }
}
