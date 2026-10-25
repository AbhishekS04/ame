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

    // Set of followed user IDs (people I follow)
    private val _followedUserIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("followed_user_ids", emptySet()) ?: emptySet()
    )
    val followedUserIds: StateFlow<Set<String>> = _followedUserIds.asStateFlow()

    // Set of follower user IDs (people who follow me)
    private val _myFollowerIds = MutableStateFlow<Set<String>>(emptySet())
    val myFollowerIds: StateFlow<Set<String>> = _myFollowerIds.asStateFlow()

    private val profileStore by lazy {
        (appContext as? com.abhishek.ame.AmeApplication)?.profileStore
    }

    /**
     * Synchronizes following and followers sets directly from Supabase follows table.
     */
    suspend fun syncRelations(myUserId: String) = withContext(Dispatchers.IO) {
        if (myUserId.isBlank()) return@withContext

        // 1. Fetch who I am following
        val followingIds = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("follower_id", myUserId) }
            }.decodeList<RemoteFollow>().map { it.followingId }.toSet()
        }.getOrNull()

        if (followingIds != null) {
            _followedUserIds.value = followingIds
            prefs.edit().putStringSet("followed_user_ids", followingIds).apply()
        }

        // 2. Fetch who is following me
        val followerIds = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("following_id", myUserId) }
            }.decodeList<RemoteFollow>().map { it.followerId }.toSet()
        }.getOrNull()

        if (followerIds != null) {
            _myFollowerIds.value = followerIds
        }

        // Update local profile store counters
        profileStore?.updateFollowCounts(
            followers = _myFollowerIds.value.size,
            following = _followedUserIds.value.size,
        )
    }

    suspend fun searchProfiles(query: String): List<SocialProfile> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().removePrefix("@").lowercase()
        val followingSet = _followedUserIds.value

        val hasProfile = profileStore?.hasCreatedProfile?.value == true
        val myId = if (hasProfile) profileStore?.userId else null
        val myHandle = if (hasProfile) profileStore?.customUsername?.value?.trim()?.removePrefix("@")?.lowercase() else null
        val myDisplayName = if (hasProfile) profileStore?.customDisplayName?.value else null
        val myAvatar = if (hasProfile) (profileStore?.avatarCloudUrl?.value ?: profileStore?.avatarPath?.value) else null

        fun formatProfile(profile: SocialProfile): SocialProfile {
            val isSelf = (myId != null && profile.id == myId) ||
                (myHandle != null && profile.username.equals(myHandle, ignoreCase = true))
            val isFollowed = if (isSelf) false else followingSet.contains(profile.id)
            return profile.copy(
                displayName = if (isSelf && !myDisplayName.isNullOrBlank()) myDisplayName else profile.displayName,
                avatarUrl = if (isSelf && !myAvatar.isNullOrBlank()) myAvatar else profile.avatarUrl,
                username = if (isSelf && !myHandle.isNullOrBlank()) myHandle else profile.username,
                isFollowing = isFollowed,
            )
        }

        if (trimmed.isBlank()) {
            // Instant discovery: fetch registered community profiles from Supabase
            val cloudProfiles = runCatching {
                AmeSupabaseClient.client.from("profiles").select {
                    limit(25)
                }.decodeList<SocialProfile>()
            }.getOrNull().orEmpty()

            val mapped = cloudProfiles.map { formatProfile(it) }

            // Include self in discovery list if user created a profile and isn't listed yet
            if (hasProfile && myId != null && mapped.none { it.id == myId || (myHandle != null && it.username.equals(myHandle, ignoreCase = true)) }) {
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

        // Search live Supabase profiles table
        val cloudResults = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter {
                    or {
                        ilike("username", "%$trimmed%")
                        ilike("display_name", "%$trimmed%")
                    }
                }
                limit(30)
            }.decodeList<SocialProfile>()
        }.getOrNull().orEmpty()

        val results = cloudResults.map { formatProfile(it) }

        // If self matches search query and is not yet in results, prepend self profile
        val matchesSelf = hasProfile && ((myHandle?.contains(trimmed) == true) || (myDisplayName?.lowercase()?.contains(trimmed) == true))
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
        val cloudProfile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<SocialProfile>()
        }.getOrNull() ?: return@withContext null

        val cloudPlaylists = runCatching {
            AmeSupabaseClient.client.from("public_playlists").select {
                filter { eq("user_id", cloudProfile.id) }
            }.decodeList<RemotePublicPlaylist>().map {
                CatalogPlaylist(
                    uri = it.playlistUri,
                    name = it.name,
                    subtitle = it.subtitle,
                    artworkUrl = it.artworkUrl,
                )
            }
        }.getOrNull().orEmpty()

        // Fetch real-time follower & following counts directly from follows table
        val realFollowers = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("following_id", cloudProfile.id) }
            }.decodeList<RemoteFollow>().size
        }.getOrDefault(cloudProfile.followersCount)

        val realFollowing = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("follower_id", cloudProfile.id) }
            }.decodeList<RemoteFollow>().size
        }.getOrDefault(cloudProfile.followingCount)

        val hasProfile = profileStore?.hasCreatedProfile?.value == true
        val myId = if (hasProfile) profileStore?.userId else null
        val myHandle = if (hasProfile) profileStore?.customUsername?.value?.trim()?.removePrefix("@")?.lowercase() else null
        val myDisplayName = if (hasProfile) profileStore?.customDisplayName?.value else null
        val myAvatar = if (hasProfile) (profileStore?.avatarCloudUrl?.value ?: profileStore?.avatarPath?.value) else null

        val isSelf = (myId != null && cloudProfile.id == myId) ||
            (myHandle != null && cloudProfile.username.equals(myHandle, ignoreCase = true))

        val isFollowed = if (isSelf) false else _followedUserIds.value.contains(cloudProfile.id)
        cloudProfile.copy(
            displayName = if (isSelf && !myDisplayName.isNullOrBlank()) myDisplayName else cloudProfile.displayName,
            avatarUrl = if (isSelf && !myAvatar.isNullOrBlank()) myAvatar else cloudProfile.avatarUrl,
            username = if (isSelf && !myHandle.isNullOrBlank()) myHandle else cloudProfile.username,
            isFollowing = isFollowed,
            followersCount = realFollowers,
            followingCount = realFollowing,
            publicPlaylists = cloudPlaylists,
        )
    }

    suspend fun getProfileByUsername(username: String): SocialProfile? = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()

        val cloudProfile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter { eq("username", clean) }
            }.decodeSingleOrNull<SocialProfile>()
        }.getOrNull() ?: return@withContext null

        val cloudPlaylists = runCatching {
            AmeSupabaseClient.client.from("public_playlists").select {
                filter { eq("user_id", cloudProfile.id) }
            }.decodeList<RemotePublicPlaylist>().map {
                CatalogPlaylist(
                    uri = it.playlistUri,
                    name = it.name,
                    subtitle = it.subtitle,
                    artworkUrl = it.artworkUrl,
                )
            }
        }.getOrNull().orEmpty()

        val realFollowers = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("following_id", cloudProfile.id) }
            }.decodeList<RemoteFollow>().size
        }.getOrDefault(cloudProfile.followersCount)

        val realFollowing = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("follower_id", cloudProfile.id) }
            }.decodeList<RemoteFollow>().size
        }.getOrDefault(cloudProfile.followingCount)

        val hasProfile = profileStore?.hasCreatedProfile?.value == true
        val myId = if (hasProfile) profileStore?.userId else null
        val myHandle = if (hasProfile) profileStore?.customUsername?.value?.trim()?.removePrefix("@")?.lowercase() else null
        val myDisplayName = if (hasProfile) profileStore?.customDisplayName?.value else null
        val myAvatar = if (hasProfile) (profileStore?.avatarCloudUrl?.value ?: profileStore?.avatarPath?.value) else null

        val isSelf = (myId != null && cloudProfile.id == myId) ||
            (myHandle != null && cloudProfile.username.equals(myHandle, ignoreCase = true))

        val isFollowed = if (isSelf) false else _followedUserIds.value.contains(cloudProfile.id)
        cloudProfile.copy(
            displayName = if (isSelf && !myDisplayName.isNullOrBlank()) myDisplayName else cloudProfile.displayName,
            avatarUrl = if (isSelf && !myAvatar.isNullOrBlank()) myAvatar else cloudProfile.avatarUrl,
            username = if (isSelf && !myHandle.isNullOrBlank()) myHandle else cloudProfile.username,
            isFollowing = isFollowed,
            followersCount = realFollowers,
            followingCount = realFollowing,
            publicPlaylists = cloudPlaylists,
        )
    }

    suspend fun followUser(myUserId: String, targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (myUserId == targetUserId || targetUserId.isBlank()) return@withContext false

        val current = _followedUserIds.value.toMutableSet()
        val added = current.add(targetUserId)
        if (added) {
            _followedUserIds.value = current
            prefs.edit().putStringSet("followed_user_ids", current).apply()

            profileStore?.updateFollowCounts(
                followers = _myFollowerIds.value.size,
                following = current.size,
            )

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
        if (targetUserId.isBlank()) return@withContext false

        val current = _followedUserIds.value.toMutableSet()
        val removed = current.remove(targetUserId)
        if (removed) {
            _followedUserIds.value = current
            prefs.edit().putStringSet("followed_user_ids", current).apply()

            profileStore?.updateFollowCounts(
                followers = _myFollowerIds.value.size,
                following = current.size,
            )

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

    fun isFollowedBy(userId: String): Boolean = _myFollowerIds.value.contains(userId)

    suspend fun getFollowers(userId: String): List<SocialProfile> = withContext(Dispatchers.IO) {
        val followRows = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("following_id", userId) }
            }.decodeList<RemoteFollow>()
        }.getOrNull().orEmpty()

        if (followRows.isEmpty()) return@withContext emptyList()

        val followerIds = followRows.map { it.followerId }.distinct()
        fetchProfilesByIds(followerIds)
    }

    suspend fun getFollowing(userId: String): List<SocialProfile> = withContext(Dispatchers.IO) {
        val followRows = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("follower_id", userId) }
            }.decodeList<RemoteFollow>()
        }.getOrNull().orEmpty()

        if (followRows.isEmpty()) return@withContext emptyList()

        val followingIds = followRows.map { it.followingId }.distinct()
        fetchProfilesByIds(followingIds)
    }

    private suspend fun fetchProfilesByIds(ids: List<String>): List<SocialProfile> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()

        val profiles = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter {
                    isIn("id", ids)
                }
            }.decodeList<SocialProfile>()
        }.getOrNull().orEmpty()

        val profileMap = profiles.associateBy { it.id }
        val myFollowed = _followedUserIds.value
        val myId = profileStore?.userId

        ids.map { id ->
            val p = profileMap[id] ?: SocialProfile(
                id = id,
                username = "listener",
                displayName = "Ame Listener",
            )
            val isSelf = (myId != null && p.id == myId)
            p.copy(
                isFollowing = if (isSelf) false else myFollowed.contains(p.id),
            )
        }
    }
}
