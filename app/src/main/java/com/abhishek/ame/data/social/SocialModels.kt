package com.abhishek.ame.data.social

import com.abhishek.ame.data.CatalogPlaylist
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Model representing an Ame social user profile stored in Supabase.
 */
@Serializable
data class SocialProfile(
    val id: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("banner_url")
    val bannerUrl: String? = null,
    val bio: String = "",
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    @SerialName("followers_count")
    val followersCount: Int = 0,
    @SerialName("following_count")
    val followingCount: Int = 0,
    @SerialName("streak_days")
    val streakDays: Int = 0,
    val isFollowing: Boolean = false,
    val publicPlaylists: List<CatalogPlaylist> = emptyList(),
)

@Serializable
data class RemoteFollow(
    @SerialName("follower_id")
    val followerId: String,
    @SerialName("following_id")
    val followingId: String,
)

@Serializable
data class RemotePublicPlaylist(
    @SerialName("user_id")
    val userId: String,
    @SerialName("playlist_uri")
    val playlistUri: String,
    val name: String,
    val subtitle: String? = null,
    @SerialName("artwork_url")
    val artworkUrl: String? = null,
)


