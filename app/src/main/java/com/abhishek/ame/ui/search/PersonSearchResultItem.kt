package com.abhishek.ame.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.abhishek.ame.data.social.JamTrack
import com.abhishek.ame.data.social.SocialProfile
import com.abhishek.ame.ui.components.ProfilePicture
import com.abhishek.ame.ui.glass.pressable
import com.abhishek.ame.ui.theme.Ink
import com.abhishek.ame.ui.theme.InkDim
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Waveform
import java.io.File

/**
 * Row displaying a user profile search result with actual avatar, display name,
 * @username handle, follower counter, and live Listen Along Jam status.
 * Per user design specifications, no follow button is displayed in the search list;
 * following is managed directly from the user's dedicated Profile page.
 */
@Composable
fun PersonSearchResultRow(
    profile: SocialProfile,
    accentColor: Color,
    onClick: () -> Unit,
    jam: JamTrack? = null,
    onJoinJam: ((JamTrack) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val avatarUrl = profile.avatarUrl
    val localFile = remember(avatarUrl) {
        if (avatarUrl != null && (avatarUrl.startsWith("/") || avatarUrl.startsWith("file:"))) {
            File(avatarUrl.removePrefix("file://"))
        } else null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick, shape = RoundedCornerShape(16.dp), pressedScale = 0.98f)
            .padding(start = 24.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (localFile != null && localFile.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(localFile)
                    .crossfade(false)
                    .build(),
                contentDescription = profile.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            )
        } else {
            ProfilePicture(
                url = avatarUrl,
                name = profile.displayName,
                size = 48.dp,
                decodeSize = 48.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.streakDays > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "🔥 ${profile.streakDays}d",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = accentColor,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "@${profile.username.removePrefix("@")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkDim.copy(alpha = 0.4f),
                )
                Text(
                    text = "${profile.followersCount} followers",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = InkDim.copy(alpha = 0.8f),
                )
            }

            if (jam != null && jam.isPlaying && jam.trackTitle.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        PhosphorIcons.Fill.Waveform,
                        contentDescription = null,
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "Listening to ${jam.trackTitle}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF1DB954),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Live Jam Join Action if currently listening to music
        if (jam != null && jam.isPlaying && onJoinJam != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1DB954))
                    .pressable(onClick = { onJoinJam(jam) })
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Join",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = Color.Black,
                )
            }
        }
    }
}
