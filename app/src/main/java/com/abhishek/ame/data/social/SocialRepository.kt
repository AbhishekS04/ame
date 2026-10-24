package com.abhishek.ame.data.social

import android.content.Context
import com.abhishek.ame.data.CatalogPlaylist
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository coordinating Ame social profiles, search discovery, and follow relations.
 */
class SocialRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("ame_social_prefs", Context.MODE_PRIVATE)

    // Set of followed user IDs
    private val _followedUserIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("followed_user_ids", emptySet()) ?: emptySet()
    )
    val followedUserIds: StateFlow<Set<String>> = _followedUserIds.asStateFlow()

    // Curated community music accounts (never hardcoding personal user accounts)
    private val communityProfiles = listOf(
        SocialProfile(
            id = "user_ame_official",
            username = "ame",
            displayName = "Ame Official",
            avatarUrl = null,
            bio = "Official Ame Music Community & Radio",
            isPrivate = false,
            followersCount = 89000,
            followingCount = 12,
            streakDays = 365,
            publicPlaylists = listOf(
                CatalogPlaylist(
                    uri = "ytmusic:playlist:RDCLAK5uy_kmPRjFv6SS2S6HP5N5x8_12",
                    name = "Ame Best of 2026",
                    subtitle = "Ame Community Picks",
                ),
            ),
        ),
        SocialProfile(
            id = "user_curator_aurora",
            username = "aurora_beats",
            displayName = "Aurora Vibe",
            avatarUrl = null,
            bio = "Ambient / Synthwave nocturnal listener",
            isPrivate = true,
            followersCount = 420,
            followingCount = 310,
            streakDays = 9,
            publicPlaylists = listOf(
                CatalogPlaylist(
                    uri = "ytmusic:playlist:RDCLAK5uy_l4n7_aurora",
                    name = "Nocturnal Dreams",
                    subtitle = "Exclusive Playlist",
                ),
            ),
        ),
        SocialProfile(
            id = "user_curator_zen",
            username = "zenith",
            displayName = "Zenith Audio",
            avatarUrl = null,
            bio = "High-fidelity lossless audiophile",
            isPrivate = false,
            followersCount = 812,
            followingCount = 440,
            streakDays = 18,
            publicPlaylists = listOf(
                CatalogPlaylist(
                    uri = "ytmusic:playlist:RDCLAK5uy_k1n9_zenith",
                    name = "Acoustic Sessions & Unplugged",
                    subtitle = "Zenith Audio Curation",
                ),
            ),
        ),
    )

    private val profileStore by lazy {
        (appContext as? com.abhishek.ame.AmeApplication)?.profileStore
    }

    suspend fun searchProfiles(query: String): List<SocialProfile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().removePrefix("@").lowercase()
        val followingSet = _followedUserIds.value

        val myId = profileStore?.userId
        val myHandle = profileStore?.customUsername?.value?.trim()?.removePrefix("@")?.lowercase()
        val myDisplayName = profileStore?.customDisplayName?.value
        val myAvatar = profileStore?.avatarCloudUrl?.value ?: profileStore?.avatarPath?.value

        fun formatProfile(profile: SocialProfile): SocialProfile {
            val isSelf = (myId != null && profile.id == myId) ||
                (myHandle != null && profile.username.equals(myHandle, ignoreCase = true))
            val isFollowed = if (isSelf) false else followingSet.contains(profile.id)
            return profile.copy(
                displayName = if (isSelf && !myDisplayName.isNullOrBlank()) myDisplayName else profile.displayName,
                avatarUrl = if (isSelf && !myAvatar.isNullOrBlank()) myAvatar else profile.avatarUrl,
                username = if (isSelf && !myHandle.isNullOrBlank()) myHandle else profile.username,
                isFollowing = isFollowed,
                followersCount = profile.followersCount + if (isFollowed) 1 else 0,
            )
        }

        if (trimmed.isBlank()) {
            // Instant discovery: show active and community profiles when user taps Profiles chip
            val cloudProfiles = runCatching {
                AmeSupabaseClient.client.from("profiles").select {
                    limit(20)
                }.decodeList<SocialProfile>()
            }.getOrNull()

            val source = if (!cloudProfiles.isNullOrEmpty()) cloudProfiles else communityProfiles
            val mapped = source.map { formatProfile(it) }

            // Ensure self profile appears in discovery if not already listed
            if (myId != null && mapped.none { it.id == myId || (myHandle != null && it.username.equals(myHandle, ignoreCase = true)) }) {
                val selfProfile = SocialProfile(
                    id = myId,
                    username = myHandle ?: "user",
                    displayName = myDisplayName ?: "User",
                    avatarUrl = myAvatar,
                    bio = profileStore?.bio?.value.orEmpty(),
                    isPrivate = profileStore?.isPrivate?.value ?: false,
                    followersCount = profileStore?.followersCount?.value ?: 0,
                    followingCount = profileStore?.followingCount?.value ?: 0,
                    isFollowing = false,
                )
                return@withContext listOf(selfProfile) + mapped
            }
            return@withContext mapped
        }

        // 1. First, search live Supabase profiles table
        val cloudResults = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter {
                    or {
                        ilike("username", "%$trimmed%")
                        ilike("display_name", "%$trimmed%")
                    }
                }
                limit(25)
            }.decodeList<SocialProfile>()
        }.getOrNull().orEmpty()

        val results = if (cloudResults.isNotEmpty()) {
            cloudResults.map { formatProfile(it) }
        } else {
            communityProfiles.filter { profile ->
                profile.username.lowercase().contains(trimmed) ||
                    profile.displayName.lowercase().contains(trimmed)
            }.map { formatProfile(it) }
        }

        // If self matches search query and is not yet in results, prepend self profile with actual PFP
        val matchesSelf = (myHandle?.contains(trimmed) == true) || (myDisplayName?.lowercase()?.contains(trimmed) == true)
        if (matchesSelf && results.none { it.id == myId || (myHandle != null && it.username.equals(myHandle, ignoreCase = true)) }) {
            val selfProfile = SocialProfile(
                id = myId ?: "self",
                username = myHandle ?: "user",
                displayName = myDisplayName ?: "User",
                avatarUrl = myAvatar,
                bio = profileStore?.bio?.value.orEmpty(),
                isPrivate = profileStore?.isPrivate?.value ?: false,
                followersCount = profileStore?.followersCount?.value ?: 0,
                followingCount = profileStore?.followingCount?.value ?: 0,
                isFollowing = false,
            )
            return@withContext listOf(selfProfile) + results
        }

        results
    }

    suspend fun getProfile(userId: String): SocialProfile? = withContext(Dispatchers.IO) {
        // 1. Fetch profile from Supabase
        val cloudProfile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<SocialProfile>()
        }.getOrNull()

        val baseProfile = cloudProfile ?: communityProfiles.find { it.id == userId } ?: return@withContext null

        // 2. Fetch public playlists from Supabase public_playlists table
        val cloudPlaylists = runCatching {
            AmeSupabaseClient.client.from("public_playlists").select {
                filter { eq("user_id", baseProfile.id) }
            }.decodeList<RemotePublicPlaylist>().map {
                CatalogPlaylist(
                    uri = it.playlistUri,
                    name = it.name,
                    subtitle = it.subtitle,
                    artworkUrl = it.artworkUrl,
                )
            }
        }.getOrNull() ?: baseProfile.publicPlaylists

        val myId = profileStore?.userId
        val myHandle = profileStore?.customUsername?.value?.trim()?.removePrefix("@")?.lowercase()
        val myDisplayName = profileStore?.customDisplayName?.value
        val myAvatar = profileStore?.avatarCloudUrl?.value ?: profileStore?.avatarPath?.value

        val isSelf = (myId != null && baseProfile.id == myId) ||
            (myHandle != null && baseProfile.username.equals(myHandle, ignoreCase = true))

        val isFollowed = if (isSelf) false else _followedUserIds.value.contains(baseProfile.id)
        baseProfile.copy(
            displayName = if (isSelf && !myDisplayName.isNullOrBlank()) myDisplayName else baseProfile.displayName,
            avatarUrl = if (isSelf && !myAvatar.isNullOrBlank()) myAvatar else baseProfile.avatarUrl,
            username = if (isSelf && !myHandle.isNullOrBlank()) myHandle else baseProfile.username,
            isFollowing = isFollowed,
            followersCount = baseProfile.followersCount + if (isFollowed) 1 else 0,
            publicPlaylists = cloudPlaylists,
        )
    }

    suspend fun getProfileByUsername(username: String): SocialProfile? = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()

        // 1. Fetch by username from Supabase
        val cloudProfile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter { eq("username", clean) }
            }.decodeSingleOrNull<SocialProfile>()
        }.getOrNull()

        val baseProfile = cloudProfile ?: communityProfiles.find { it.username.lowercase() == clean } ?: return@withContext null

        // 2. Fetch public playlists from Supabase public_playlists table
        val cloudPlaylists = runCatching {
            AmeSupabaseClient.client.from("public_playlists").select {
                filter { eq("user_id", baseProfile.id) }
            }.decodeList<RemotePublicPlaylist>().map {
                CatalogPlaylist(
                    uri = it.playlistUri,
                    name = it.name,
                    subtitle = it.subtitle,
                    artworkUrl = it.artworkUrl,
                )
            }
        }.getOrNull() ?: baseProfile.publicPlaylists

        val myId = profileStore?.userId
        val myHandle = profileStore?.customUsername?.value?.trim()?.removePrefix("@")?.lowercase()
        val myDisplayName = profileStore?.customDisplayName?.value
        val myAvatar = profileStore?.avatarCloudUrl?.value ?: profileStore?.avatarPath?.value

        val isSelf = (myId != null && baseProfile.id == myId) ||
            (myHandle != null && baseProfile.username.equals(myHandle, ignoreCase = true))

        val isFollowed = if (isSelf) false else _followedUserIds.value.contains(baseProfile.id)
        baseProfile.copy(
            displayName = if (isSelf && !myDisplayName.isNullOrBlank()) myDisplayName else baseProfile.displayName,
            avatarUrl = if (isSelf && !myAvatar.isNullOrBlank()) myAvatar else baseProfile.avatarUrl,
            username = if (isSelf && !myHandle.isNullOrBlank()) myHandle else baseProfile.username,
            isFollowing = isFollowed,
            followersCount = baseProfile.followersCount + if (isFollowed) 1 else 0,
            publicPlaylists = cloudPlaylists,
        )
    }

    suspend fun followUser(myUserId: String, targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _followedUserIds.value.toMutableSet()
        val added = current.add(targetUserId)
        if (added) {
            _followedUserIds.value = current
            prefs.edit().putStringSet("followed_user_ids", current).apply()

            // Asynchronously sync follow to Supabase
            runCatching {
                AmeSupabaseClient.client.from("follows").insert(
                    RemoteFollow(followerId = myUserId, followingId = targetUserId)
                )
            }.onFailure {
                android.util.Log.e("SocialRepository", "Failed to sync follow to Supabase", it)
            }
        }
        added
    }

    suspend fun unfollowUser(myUserId: String, targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _followedUserIds.value.toMutableSet()
        val removed = current.remove(targetUserId)
        if (removed) {
            _followedUserIds.value = current
            prefs.edit().putStringSet("followed_user_ids", current).apply()

            // Asynchronously sync unfollow to Supabase
            runCatching {
                AmeSupabaseClient.client.from("follows").delete {
                    filter {
                        eq("follower_id", myUserId)
                        eq("following_id", targetUserId)
                    }
                }
            }.onFailure {
                android.util.Log.e("SocialRepository", "Failed to sync unfollow to Supabase", it)
            }
        }
        removed
    }

    fun isFollowing(userId: String): Boolean = _followedUserIds.value.contains(userId)
}

