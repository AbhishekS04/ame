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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.adamglin.phosphoricons.regular.Key
import com.adamglin.phosphoricons.regular.SignOut
import com.adamglin.phosphoricons.regular.EyeSlash
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import com.abhishek.ame.ui.components.ConfirmSheet
import com.abhishek.ame.ui.glass.LocalGlassEffectConfig
import com.abhishek.ame.ui.glass.liquidGlass
import com.abhishek.ame.ui.glass.shapes.ContinuousCapsule
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    isProfileLoading: Boolean = false,
    currentUserId: String? = null,
    hasCreatedProfile: Boolean = false,
    activeJam: JamTrack? = null,
    onJoinJam: ((JamTrack) -> Unit)? = null,
    onToggleFollow: (String) -> Unit = {},
    myFollowerIds: Set<String> = emptySet(),
    followedUserIds: Set<String> = emptySet(),
    onOpenUserProfile: (com.abhishek.ame.data.social.SocialProfile) -> Unit = {},
    fetchFollowers: suspend (String) -> List<com.abhishek.ame.data.social.SocialProfile> = { emptyList() },
    fetchFollowing: suspend (String) -> List<com.abhishek.ame.data.social.SocialProfile> = { emptyList() },
    contentPadding: PaddingValues,
    backdrop: Backdrop,
    accentColor: Color,
    onPickBanner: (Uri) -> Unit = {},
    onPickAvatar: (Uri) -> Unit = {},
    onSaveDisplayName: (String) -> Unit = {},
    onSaveUsername: (String) -> Unit = {},
    onCheckUsernameAvailability: (String) -> String? = { null },
    onCheckUsernameRemote: suspend (String) -> String? = { null },
    onCreateProfile: suspend (String, String, String, String, Boolean, Uri?) -> Result<com.abhishek.ame.data.social.SocialProfile> = { _, _, _, _, _, _ ->
        Result.failure(Exception("Not implemented"))
    },
    onLoginProfile: suspend (String, String) -> Result<com.abhishek.ame.data.social.SocialProfile> = { _, _ ->
        Result.failure(Exception("Not implemented"))
    },
    onLogoutProfile: () -> Unit = {},
    onSaveBio: (String) -> Unit = {},
    onSetPrivate: (Boolean) -> Unit = {},
    onOpenPlaylist: (CatalogPlaylist) -> Unit = {},
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (isProfileLoading) {
        ProfileLoadingSkeleton(
            contentPadding = contentPadding,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val isVisiting = visitedProfile != null && (currentUserId == null || visitedProfile.id != currentUserId)
    val isSelf = !isVisiting
    val userHasProfile = hasCreatedProfile || (!customUsername.isNullOrBlank() && !customDisplayName.isNullOrBlank())

    if (isSelf && !userHasProfile) {
        CreateOrLoginProfileView(
            initialDisplayName = accountName.orEmpty(),
            accentColor = accentColor,
            backdrop = backdrop,
            contentPadding = contentPadding,
            onBack = onBack,
            onCheckUsernameRemote = onCheckUsernameRemote,
            onCheckUsernameLocal = onCheckUsernameAvailability,
            onCreateProfile = onCreateProfile,
            onLoginProfile = onLoginProfile,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current

    val dailyListening = remember(isVisiting, visitedProfile, listeningEvents) {
        if (isVisiting) {
            visitedProfile?.let {
                StreakEngine.parseFromCloud(it.listeningActivity)
            } ?: emptyMap()
        } else {
            StreakEngine.computeDailyListening(listeningEvents)
        }
    }

    var showFollowListSheet by remember { mutableStateOf(false) }
    var followListTab by remember { mutableStateOf(FollowTab.FOLLOWERS) }

    val displayName = if (isVisiting) {
        visitedProfile?.displayName?.ifBlank { null } ?: "Listener"
    } else {
        customDisplayName?.ifBlank { null }
            ?: accountName?.ifBlank { null }
            ?: "Ame Listener"
    }

    val usernameHandle = if (isVisiting) {
        visitedProfile?.username?.let { "@$it" } ?: "@listener"
    } else {
        customUsername?.let { "@$it" } ?: "@listener"
    }

    val effectiveBio = visitedProfile?.bio ?: bio
    val effectiveIsPrivate = visitedProfile?.isPrivate ?: isPrivate
    val effectiveFollowers = visitedProfile?.followersCount ?: followersCount
    val effectiveFollowing = visitedProfile?.followingCount ?: followingCount
    val effectivePlaylists = visitedProfile?.publicPlaylists ?: publicPlaylists
    val isFollowing = visitedProfile?.isFollowing == true

    var isPreviewMode by rememberSaveable { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

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

    var isCheckingEditUsername by remember { mutableStateOf(false) }
    var editUsernameRemoteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editedUsername, showEditProfileDialog) {
        if (!showEditProfileDialog) return@LaunchedEffect
        val currentHandle = (customUsername ?: "").removePrefix("@")
        if (editedUsername.equals(currentHandle, ignoreCase = true)) {
            editUsernameRemoteError = null
            isCheckingEditUsername = false
            return@LaunchedEffect
        }
        val local = onCheckUsernameAvailability(editedUsername)
        if (local != null) {
            editUsernameRemoteError = local
            isCheckingEditUsername = false
            return@LaunchedEffect
        }
        if (editedUsername.length >= 3) {
            isCheckingEditUsername = true
            delay(350)
            editUsernameRemoteError = onCheckUsernameRemote(editedUsername)
            isCheckingEditUsername = false
        } else {
            editUsernameRemoteError = null
            isCheckingEditUsername = false
        }
    }

    val isEditUsernameAvailable = remember(editedUsername, editUsernameRemoteError, isCheckingEditUsername) {
        val currentHandle = (customUsername ?: "").removePrefix("@")
        editedUsername.equals(currentHandle, ignoreCase = true) ||
            (editedUsername.length >= 3 && editUsernameRemoteError == null && !isCheckingEditUsername)
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
                                if (isCheckingEditUsername) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = accentColor,
                                    )
                                } else if (editedUsername.length >= 3) {
                                    if (isEditUsernameAvailable) {
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
                            } else if (isCheckingEditUsername) {
                                Text(
                                    text = "Checking availability...",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = InkDim,
                                )
                            } else if (isEditUsernameAvailable) {
                                Text(
                                    text = "✓ @$editedUsername is available",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFF22C55E),
                                )
                            } else if (editUsernameRemoteError != null) {
                                Text(
                                    text = "✕ $editUsernameRemoteError",
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

                    // Log Out Action Row
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .border(0.75.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .pressable(onClick = {
                                showEditProfileDialog = false
                                showLogoutConfirmDialog = true
                            })
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                PhosphorIcons.Regular.SignOut,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Log Out of Ame",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFEF4444),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val canSave = editedName.isNotBlank() && isEditUsernameAvailable && !isCheckingEditUsername
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

    ConfirmSheet(
        visible = showLogoutConfirmDialog,
        title = "Log Out of Ame?",
        message = "You will be logged out of @${usernameHandle.removePrefix("@")}. You can log back in anytime with your username and password.",
        confirmLabel = "Log Out",
        cancelLabel = "Cancel",
        destructive = true,
        backdrop = backdrop,
        onConfirm = {
            showLogoutConfirmDialog = false
            onLogoutProfile()
        },
        onDismiss = {
            showLogoutConfirmDialog = false
        },
    )

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
                        .height(390.dp),
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
                                .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val avatarModel = remember(isVisiting, visitedProfile?.avatarUrl, customAvatarPath, customAvatarCloudUrl, defaultAvatarUrl) {
                                if (isVisiting) {
                                    visitedProfile?.avatarUrl
                                } else {
                                    when {
                                        customAvatarPath != null && File(customAvatarPath).exists() -> File(customAvatarPath)
                                        !customAvatarCloudUrl.isNullOrBlank() -> customAvatarCloudUrl
                                        !defaultAvatarUrl.isNullOrBlank() -> defaultAvatarUrl
                                        else -> null
                                    }
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
                    val canViewNetwork = !isVisiting || !effectiveIsPrivate || isFollowing

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .pressable(onClick = {
                                    if (canViewNetwork) {
                                        followListTab = FollowTab.FOLLOWERS
                                        showFollowListSheet = true
                                    } else {
                                        android.widget.Toast.makeText(context, "This account is private", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                })
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "$effectiveFollowers Followers",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink,
                            )
                        }

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim.copy(alpha = 0.5f),
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .pressable(onClick = {
                                    if (canViewNetwork) {
                                        followListTab = FollowTab.FOLLOWING
                                        showFollowListSheet = true
                                    } else {
                                        android.widget.Toast.makeText(context, "This account is private", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                })
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "$effectiveFollowing Following",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Ink,
                            )
                        }

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim.copy(alpha = 0.5f),
                        )

                        Text(
                            text = "${effectivePlaylists.size} ${if (effectivePlaylists.size == 1) "Playlist" else "Playlists"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
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

            // Quick Logout Button for Profile Owner
            if (!isVisiting && !isPreviewMode) {
                LiquidButton(
                    onClick = { showLogoutConfirmDialog = true },
                    backdrop = backdrop,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                        .border(0.75.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentHeight = 40.dp,
                    contentPadding = 0.dp,
                ) {
                    Icon(
                        PhosphorIcons.Regular.SignOut,
                        contentDescription = "Log Out",
                        tint = Ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (showFollowListSheet) {
            val targetId = visitedProfile?.id ?: currentUserId.orEmpty()
            val targetHandle = visitedProfile?.username ?: (customUsername ?: "user")
            FollowersListSheet(
                targetUserId = targetId,
                targetUsername = targetHandle,
                initialTab = followListTab,
                myUserId = currentUserId,
                followedUserIds = followedUserIds,
                myFollowerIds = myFollowerIds,
                accentColor = accentColor,
                onToggleFollow = onToggleFollow,
                onOpenProfile = onOpenUserProfile,
                fetchFollowers = fetchFollowers,
                fetchFollowing = fetchFollowing,
                onDismiss = { showFollowListSheet = false },
            )
        }
    }
}

private enum class AuthMode {
    SIGN_UP,
    LOG_IN,
}

/**
 * Minimalist, Liquid Glass onboarding and authentication view for Ame listeners.
 * Built with authentic liquid glass inputs, high-contrast neutral tabs, and strict credential verification.
 */
@Composable
private fun CreateOrLoginProfileView(
    initialDisplayName: String,
    accentColor: Color,
    backdrop: Backdrop,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onCheckUsernameRemote: suspend (String) -> String?,
    onCheckUsernameLocal: (String) -> String?,
    onCreateProfile: suspend (String, String, String, String, Boolean, Uri?) -> Result<com.abhishek.ame.data.social.SocialProfile>,
    onLoginProfile: suspend (String, String) -> Result<com.abhishek.ame.data.social.SocialProfile>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var authMode by rememberSaveable { mutableStateOf(AuthMode.SIGN_UP) }

    // --- Sign Up Form State ---
    val cleanInitialName = remember(initialDisplayName) {
        val trimmed = initialDisplayName.trim()
        if (trimmed.equals("Ame Listener", ignoreCase = true) ||
            trimmed.equals("Listener", ignoreCase = true) ||
            trimmed.equals("Unknown", ignoreCase = true)
        ) "" else trimmed
    }
    var displayNameInput by remember { mutableStateOf(cleanInitialName) }
    var signUpUsernameInput by remember { mutableStateOf("") }
    var signUpPasswordInput by remember { mutableStateOf("") }
    var signUpPasswordVisible by remember { mutableStateOf(false) }
    var bioInput by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    var isCheckingUsername by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var isUsernameAvailable by remember { mutableStateOf(false) }
    var isSubmittingSignUp by remember { mutableStateOf(false) }
    var signUpErrorMessage by remember { mutableStateOf<String?>(null) }

    // --- Log In Form State ---
    var loginUsernameInput by remember { mutableStateOf("") }
    var loginPasswordInput by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var isSubmittingLogin by remember { mutableStateOf(false) }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
        }
    }

    LaunchedEffect(signUpUsernameInput) {
        if (signUpUsernameInput.isBlank()) {
            usernameError = null
            isUsernameAvailable = false
            isCheckingUsername = false
            return@LaunchedEffect
        }
        val local = onCheckUsernameLocal(signUpUsernameInput)
        if (local != null) {
            usernameError = local
            isUsernameAvailable = false
            isCheckingUsername = false
            return@LaunchedEffect
        }
        isCheckingUsername = true
        usernameError = null
        isUsernameAvailable = false
        delay(350)
        val remote = onCheckUsernameRemote(signUpUsernameInput)
        if (remote != null) {
            usernameError = remote
            isUsernameAvailable = false
        } else {
            usernameError = null
            isUsernameAvailable = true
        }
        isCheckingUsername = false
    }

    val canCreate = displayNameInput.trim().length >= 2 &&
        isUsernameAvailable &&
        !isCheckingUsername &&
        signUpPasswordInput.length >= 6 &&
        !isSubmittingSignUp

    val canLogin = loginUsernameInput.trim().isNotBlank() &&
        loginPasswordInput.isNotBlank() &&
        !isSubmittingLogin

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageGround()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 56.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Bar with Liquid Back Button & Minimal Typography
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidButton(
                    onClick = onBack,
                    backdrop = backdrop,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(0.75.dp, Color.White.copy(alpha = 0.2f), CircleShape),
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

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (authMode == AuthMode.SIGN_UP) "Join Ame" else "Welcome Back",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = Ink,
                    )
                    Text(
                        text = if (authMode == AuthMode.SIGN_UP) "Set up your unique sound identity" else "Sign in with your username and password",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                    )
                }
            }

            // High-Contrast Neutral Liquid Glass Tab Pill (No Green!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .liquidGlass(
                        config = LocalGlassEffectConfig.current,
                        shape = ContinuousCapsule(),
                        ownBackdrop = backdrop,
                        highlightAlpha = 0.25f,
                    )
                    .border(0.75.dp, Color.White.copy(alpha = 0.16f), ContinuousCapsule())
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ContinuousCapsule())
                        .background(if (authMode == AuthMode.SIGN_UP) Ink else Color.Transparent)
                        .pressable(onClick = {
                            authMode = AuthMode.SIGN_UP
                            signUpErrorMessage = null
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (authMode == AuthMode.SIGN_UP) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = if (authMode == AuthMode.SIGN_UP) pageGround() else InkDim,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ContinuousCapsule())
                        .background(if (authMode == AuthMode.LOG_IN) Ink else Color.Transparent)
                        .pressable(onClick = {
                            authMode = AuthMode.LOG_IN
                            loginErrorMessage = null
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Log In",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (authMode == AuthMode.LOG_IN) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = if (authMode == AuthMode.LOG_IN) pageGround() else InkDim,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (authMode == AuthMode.SIGN_UP) {
                // --- CREATE ACCOUNT MODE ---

                // Circular Liquid Glass Avatar Picker
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .liquidGlass(
                            config = LocalGlassEffectConfig.current,
                            shape = CircleShape,
                            ownBackdrop = backdrop,
                            highlightAlpha = 0.35f,
                        )
                        .border(
                            1.dp,
                            if (selectedAvatarUri != null) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.16f),
                            CircleShape,
                        )
                        .pressable(onClick = {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedAvatarUri != null) {
                        AsyncImage(
                            model = selectedAvatarUri,
                            contentDescription = "Selected Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Camera,
                                contentDescription = "Add Avatar",
                                tint = Ink,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Add Photo",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = InkDim,
                            )
                        }
                    }
                }

                if (selectedAvatarUri != null) {
                    Text(
                        text = "Remove photo",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFFEF4444),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .pressable(onClick = { selectedAvatarUri = null }),
                    )
                    Spacer(Modifier.height(14.dp))
                } else {
                    Spacer(Modifier.height(20.dp))
                }

                // Minimal Liquid Glass Error Banner
                if (signUpErrorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .liquidGlass(
                                config = LocalGlassEffectConfig.current,
                                shape = RoundedCornerShape(14.dp),
                                ownBackdrop = backdrop,
                                highlightAlpha = 0.2f,
                            )
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .border(0.75.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            PhosphorIcons.Regular.X,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = signUpErrorMessage!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Minimal Liquid Glass Input Fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Display Name Field
                    GlassInputField(
                        label = "DISPLAY NAME",
                        value = displayNameInput,
                        onValueChange = { if (it.length <= 30) displayNameInput = it },
                        placeholder = "e.g. Alex Rivera or SoundVoyager",
                        backdrop = backdrop,
                        leadingIcon = {
                            Icon(
                                PhosphorIcons.Regular.User,
                                contentDescription = null,
                                tint = if (displayNameInput.isNotBlank()) Ink else InkDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )

                    // Username Field
                    GlassInputField(
                        label = "USERNAME",
                        value = signUpUsernameInput,
                        onValueChange = { input ->
                            val clean = input.removePrefix("@").lowercase().replace(Regex("[^a-z0-9_]"), "").take(20)
                            signUpUsernameInput = clean
                        },
                        placeholder = "handle (e.g. nocturnal_vibes)",
                        backdrop = backdrop,
                        prefix = {
                            Text("@", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = InkDim)
                        },
                        trailingIcon = {
                            if (isCheckingUsername) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Ink,
                                )
                            } else if (isUsernameAvailable) {
                                Icon(
                                    PhosphorIcons.Regular.Check,
                                    contentDescription = "Available",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp),
                                )
                            } else if (usernameError != null && signUpUsernameInput.isNotEmpty()) {
                                Icon(
                                    PhosphorIcons.Regular.X,
                                    contentDescription = "Unavailable",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        statusContent = {
                            if (isUsernameAvailable) {
                                Text(
                                    text = "✓ @$signUpUsernameInput is available",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFF10B981),
                                )
                            } else if (usernameError != null && signUpUsernameInput.isNotEmpty()) {
                                Text(
                                    text = "✕ $usernameError",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFFEF4444),
                                )
                            }
                        },
                    )

                    // Password Field
                    GlassInputField(
                        label = "PASSWORD",
                        value = signUpPasswordInput,
                        onValueChange = { signUpPasswordInput = it },
                        placeholder = "At least 6 characters",
                        backdrop = backdrop,
                        visualTransformation = if (signUpPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        leadingIcon = {
                            Icon(
                                PhosphorIcons.Regular.Key,
                                contentDescription = null,
                                tint = if (signUpPasswordInput.isNotBlank()) Ink else InkDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Box(
                                modifier = Modifier.pressable(onClick = { signUpPasswordVisible = !signUpPasswordVisible }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (signUpPasswordVisible) PhosphorIcons.Regular.EyeSlash else PhosphorIcons.Regular.Eye,
                                    contentDescription = if (signUpPasswordVisible) "Hide password" else "Show password",
                                    tint = InkDim,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        statusContent = {
                            if (signUpPasswordInput.isNotEmpty() && signUpPasswordInput.length < 6) {
                                Text(
                                    text = "✕ Minimum 6 characters required",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Color(0xFFEF4444),
                                )
                            }
                        },
                    )

                    // Bio Field (Optional)
                    GlassInputField(
                        label = "BIO (OPTIONAL)",
                        value = bioInput,
                        onValueChange = { if (it.length <= 150) bioInput = it },
                        placeholder = "What genres, artists, or vibe define your sound?",
                        backdrop = backdrop,
                        singleLine = false,
                        maxLines = 3,
                        counterText = "${bioInput.length}/150",
                    )

                    // Private Account Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(
                                config = LocalGlassEffectConfig.current,
                                shape = RoundedCornerShape(16.dp),
                                ownBackdrop = backdrop,
                                highlightAlpha = 0.22f,
                            )
                            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                if (isPrivate) PhosphorIcons.Regular.Lock else PhosphorIcons.Regular.Globe,
                                contentDescription = null,
                                tint = Ink,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (isPrivate) "Private Profile" else "Public Profile",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                                color = Ink,
                            )
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = pageGround(),
                                checkedTrackColor = Ink,
                                uncheckedThumbColor = InkDim,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Primary Liquid Button: Create Profile
                LiquidButton(
                    onClick = {
                        scope.launch {
                            isSubmittingSignUp = true
                            signUpErrorMessage = null
                            val res = onCreateProfile(
                                displayNameInput.trim(),
                                signUpUsernameInput.trim(),
                                signUpPasswordInput,
                                bioInput.trim(),
                                isPrivate,
                                selectedAvatarUri,
                            )
                            if (res.isFailure) {
                                signUpErrorMessage = res.exceptionOrNull()?.message ?: "Failed to create profile"
                                isSubmittingSignUp = false
                            }
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .then(
                            if (canCreate) {
                                Modifier
                                    .background(Ink, ContinuousCapsule())
                                    .border(0.75.dp, Color.White.copy(alpha = 0.25f), ContinuousCapsule())
                            } else {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.08f), ContinuousCapsule())
                                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), ContinuousCapsule())
                            }
                        ),
                    contentHeight = 50.dp,
                    contentPadding = 0.dp,
                    isInteractive = canCreate && !isSubmittingSignUp,
                ) {
                    if (isSubmittingSignUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = if (canCreate) pageGround() else InkDim,
                        )
                    } else {
                        Text(
                            text = "Create Profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = if (canCreate) pageGround() else InkDim.copy(alpha = 0.45f),
                        )
                    }
                }

                // Switch to Log In mode
                Row(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .pressable(onClick = {
                            authMode = AuthMode.LOG_IN
                            loginErrorMessage = null
                        }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Already have an account?",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                    )
                    Text(
                        text = "Log In",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                    )
                }
            } else {
                // --- LOG IN MODE ---

                // Minimal Liquid Glass Error Banner
                if (loginErrorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .liquidGlass(
                                config = LocalGlassEffectConfig.current,
                                shape = RoundedCornerShape(14.dp),
                                ownBackdrop = backdrop,
                                highlightAlpha = 0.2f,
                            )
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .border(0.75.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            PhosphorIcons.Regular.X,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = loginErrorMessage!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Minimal Liquid Glass Input Fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Username Field
                    GlassInputField(
                        label = "USERNAME",
                        value = loginUsernameInput,
                        onValueChange = { input ->
                            loginUsernameInput = input.removePrefix("@").lowercase().replace(Regex("[^a-z0-9_]"), "")
                        },
                        placeholder = "your_username",
                        backdrop = backdrop,
                        prefix = {
                            Text("@", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = InkDim)
                        },
                        leadingIcon = {
                            Icon(
                                PhosphorIcons.Regular.User,
                                contentDescription = null,
                                tint = if (loginUsernameInput.isNotBlank()) Ink else InkDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )

                    // Password Field
                    GlassInputField(
                        label = "PASSWORD",
                        value = loginPasswordInput,
                        onValueChange = { loginPasswordInput = it },
                        placeholder = "••••••••",
                        backdrop = backdrop,
                        visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        leadingIcon = {
                            Icon(
                                PhosphorIcons.Regular.Key,
                                contentDescription = null,
                                tint = if (loginPasswordInput.isNotBlank()) Ink else InkDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = {
                            Box(
                                modifier = Modifier.pressable(onClick = { loginPasswordVisible = !loginPasswordVisible }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (loginPasswordVisible) PhosphorIcons.Regular.EyeSlash else PhosphorIcons.Regular.Eye,
                                    contentDescription = if (loginPasswordVisible) "Hide password" else "Show password",
                                    tint = InkDim,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Primary Liquid Button: Log In
                LiquidButton(
                    onClick = {
                        scope.launch {
                            isSubmittingLogin = true
                            loginErrorMessage = null
                            val res = onLoginProfile(loginUsernameInput.trim(), loginPasswordInput)
                            if (res.isFailure) {
                                loginErrorMessage = res.exceptionOrNull()?.message ?: "Login failed. Please check your credentials."
                                isSubmittingLogin = false
                            }
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .then(
                            if (canLogin) {
                                Modifier
                                    .background(Ink, ContinuousCapsule())
                                    .border(0.75.dp, Color.White.copy(alpha = 0.25f), ContinuousCapsule())
                            } else {
                                Modifier
                                    .background(Color.White.copy(alpha = 0.08f), ContinuousCapsule())
                                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), ContinuousCapsule())
                            }
                        ),
                    contentHeight = 50.dp,
                    contentPadding = 0.dp,
                    isInteractive = canLogin && !isSubmittingLogin,
                ) {
                    if (isSubmittingLogin) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = if (canLogin) pageGround() else InkDim,
                        )
                    } else {
                        Text(
                            text = "Log In",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = if (canLogin) pageGround() else InkDim.copy(alpha = 0.45f),
                        )
                    }
                }

                // Switch to Create Account mode
                Row(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .pressable(onClick = {
                            authMode = AuthMode.SIGN_UP
                            signUpErrorMessage = null
                        }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Don't have an account?",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                    )
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                    )
                }
            }
        }
    }
}

/**
 * Reusable, ultra-minimal Liquid Glass Input Field tailored to Ame's aesthetic standards.
 */
@Composable
private fun GlassInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    prefix: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    counterText: String? = null,
    statusContent: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                ),
                color = InkDim,
            )
            if (counterText != null) {
                Text(
                    text = counterText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = InkDim.copy(alpha = 0.6f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    config = LocalGlassEffectConfig.current,
                    shape = RoundedCornerShape(16.dp),
                    ownBackdrop = backdrop,
                    highlightAlpha = 0.22f,
                )
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .padding(horizontal = 14.dp, vertical = if (singleLine) 12.dp else 10.dp),
        ) {
            Row(
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(top = if (singleLine) 0.dp else 2.dp)) {
                        leadingIcon()
                    }
                }
                if (prefix != null) {
                    Box(modifier = Modifier.padding(top = if (singleLine) 0.dp else 2.dp)) {
                        prefix()
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = InkDim.copy(alpha = 0.45f),
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(Ink),
                        singleLine = singleLine,
                        maxLines = maxLines,
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (trailingIcon != null) {
                    Box(modifier = Modifier.padding(top = if (singleLine) 0.dp else 2.dp)) {
                        trailingIcon()
                    }
                }
            }
        }
        if (statusContent != null) {
            Box(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                statusContent()
            }
        }
    }
}

/**
 * Premium Shimmer Loading Skeleton displayed while a social profile is being resolved.
 * Prevents any screen flickering or accidental leakage of the logged-in user's profile identity.
 */
@Composable
private fun ProfileLoadingSkeleton(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shimmerAlpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageGround()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Bar with back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.Regular.ArrowLeft,
                        contentDescription = "Back",
                        tint = Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // Shimmer Avatar Circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = shimmerAlpha * 0.15f)),
            )

            Spacer(Modifier.height(16.dp))

            // Shimmer Display Name
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = shimmerAlpha * 0.15f)),
            )

            Spacer(Modifier.height(8.dp))

            // Shimmer Username
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = shimmerAlpha * 0.10f)),
            )

            Spacer(Modifier.height(28.dp))

            // Shimmer Stats Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = shimmerAlpha * 0.08f)),
            )
        }
    }
}
