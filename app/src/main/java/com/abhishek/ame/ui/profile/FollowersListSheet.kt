package com.abhishek.ame.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.abhishek.ame.data.social.SocialProfile
import com.abhishek.ame.ui.components.ProfilePicture
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import com.abhishek.ame.ui.theme.pageGround
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.adamglin.phosphoricons.regular.UserPlus
import com.adamglin.phosphoricons.regular.Users
import com.adamglin.phosphoricons.regular.X

enum class FollowTab {
    FOLLOWERS,
    FOLLOWING,
}

/**
 * Bottom Sheet displaying a user's Followers or Following network.
 * Accurately surfaces mutual followbacks ("Follow Back"), followers status ("Follows you"),
 * and live search filtering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersListSheet(
    targetUserId: String,
    targetUsername: String,
    initialTab: FollowTab,
    myUserId: String?,
    followedUserIds: Set<String>,
    myFollowerIds: Set<String>,
    accentColor: Color,
    onToggleFollow: (String) -> Unit,
    onOpenProfile: (SocialProfile) -> Unit,
    fetchFollowers: suspend (String) -> List<SocialProfile>,
    fetchFollowing: suspend (String) -> List<SocialProfile>,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }

    var followersList by remember { mutableStateOf<List<SocialProfile>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<SocialProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch lists on open and when tab changes
    LaunchedEffect(targetUserId) {
        isLoading = true
        followersList = fetchFollowers(targetUserId)
        followingList = fetchFollowing(targetUserId)
        isLoading = false
    }

    val activeList = if (selectedTab == FollowTab.FOLLOWERS) followersList else followingList
    val filteredList = remember(activeList, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            activeList
        } else {
            activeList.filter {
                it.displayName.lowercase().contains(query) ||
                    it.username.lowercase().contains(query.removePrefix("@"))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pageGround(),
        contentColor = Ink,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .navigationBarsPadding()
                .padding(top = 16.dp),
        ) {
            // Sheet Header with User Handle & Close Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "@$targetUsername",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                    )
                    Text(
                        text = if (selectedTab == FollowTab.FOLLOWERS) {
                            "${followersList.size} ${if (followersList.size == 1) "follower" else "followers"}"
                        } else {
                            "${followingList.size} following"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = InkDim,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .pressable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        PhosphorIcons.Regular.X,
                        contentDescription = "Close",
                        tint = Ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Segmented Pill Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(4.dp),
            ) {
                // Followers Tab Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == FollowTab.FOLLOWERS) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { selectedTab = FollowTab.FOLLOWERS }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Followers (${followersList.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selectedTab == FollowTab.FOLLOWERS) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (selectedTab == FollowTab.FOLLOWERS) Ink else InkDim,
                    )
                }

                // Following Tab Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == FollowTab.FOLLOWING) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { selectedTab = FollowTab.FOLLOWING }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Following (${followingList.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selectedTab == FollowTab.FOLLOWING) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (selectedTab == FollowTab.FOLLOWING) Ink else InkDim,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search Bar Filter
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (selectedTab == FollowTab.FOLLOWERS) "Search followers..." else "Search following...",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            PhosphorIcons.Regular.MagnifyingGlass,
                            contentDescription = null,
                            tint = InkDim,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                PhosphorIcons.Regular.X,
                                contentDescription = "Clear",
                                tint = InkDim,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" },
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.30f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                        focusedTextColor = Ink,
                        unfocusedTextColor = Ink,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // User List / Loading / Empty State
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        color = accentColor,
                    )
                }
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 220.dp)
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                PhosphorIcons.Regular.Users,
                                contentDescription = null,
                                tint = InkDim,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "No results for \"$searchQuery\""
                            } else if (selectedTab == FollowTab.FOLLOWERS) {
                                "No followers yet"
                            } else {
                                "Not following anyone yet"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Check spelling or search by @username"
                            } else if (selectedTab == FollowTab.FOLLOWERS) {
                                "When someone follows this profile, they will appear here."
                            } else {
                                "Follow other listeners to discover new music together."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 460.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(filteredList, key = { it.id }) { user ->
                        val isSelf = myUserId != null && user.id == myUserId
                        val isCurrentlyFollowed = followedUserIds.contains(user.id)
                        val theyFollowMe = myFollowerIds.contains(user.id)

                        FollowUserRow(
                            user = user,
                            isSelf = isSelf,
                            isFollowing = isCurrentlyFollowed,
                            theyFollowMe = theyFollowMe,
                            accentColor = accentColor,
                            onToggleFollow = { onToggleFollow(user.id) },
                            onOpenProfile = {
                                onDismiss()
                                onOpenProfile(user)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowUserRow(
    user: SocialProfile,
    isSelf: Boolean,
    isFollowing: Boolean,
    theyFollowMe: Boolean,
    accentColor: Color,
    onToggleFollow: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var localIsFollowing by remember(isFollowing) { mutableStateOf(isFollowing) }

    val handleToggle = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        localIsFollowing = !localIsFollowing
        onToggleFollow()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // User Info (Avatar + Names)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            // Circular Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (!user.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = user.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                } else {
                    ProfilePicture(
                        url = null,
                        name = user.displayName,
                        size = 46.dp,
                        decodeSize = 46.dp,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "@${user.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // "Follows you" badge chip
                    if (theyFollowMe && !isSelf) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Follows you",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = InkDim,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Action Button: (You) | Following | Follow Back | Follow
        if (isSelf) {
            Text(
                text = "You",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = InkDim.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = 8.dp),
            )
        } else if (localIsFollowing) {
            // Already Following button (subtle glass outline)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                    .pressable(onClick = handleToggle)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        PhosphorIcons.Regular.Check,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Following",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                    )
                }
            }
        } else if (theyFollowMe) {
            // Mutual Opportunity: "Follow Back" button (prominent contrast)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(accentColor)
                    .pressable(onClick = handleToggle)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        PhosphorIcons.Regular.UserPlus,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Follow Back",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black,
                    )
                }
            }
        } else {
            // Standard "Follow" button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .pressable(onClick = handleToggle)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        PhosphorIcons.Regular.UserPlus,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Follow",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Ink,
                    )
                }
            }
        }
    }
}
