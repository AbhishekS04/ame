package com.abhishek.ame.data

import android.content.Context
import com.abhishek.ame.data.social.AmeSupabaseClient
import com.abhishek.ame.data.social.RemotePublicPlaylist
import com.abhishek.ame.data.social.SocialProfile
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Manages custom user profile customizations (custom profile photo, custom background banner, bio)
 * and user's public playlists featured on their profile.
 * Persisted locally on-device in app storage and synchronized with Supabase cloud.
 */
class ProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val profileDir = File(appContext.filesDir, "custom_profile").apply { mkdirs() }
    private val publicPlaylistsFile = File(appContext.filesDir, "ame_public_playlists.json")

    val userId: String = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_USER_ID, it).apply()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _customDisplayName = MutableStateFlow(prefs.getString(KEY_DISPLAY_NAME, null))
    val customDisplayName: StateFlow<String?> = _customDisplayName.asStateFlow()

    private val _customUsername = MutableStateFlow(prefs.getString(KEY_USERNAME, null))
    val customUsername: StateFlow<String?> = _customUsername.asStateFlow()

    private val _bannerPath = MutableStateFlow(prefs.getString(KEY_BANNER, null)?.takeIf { File(it).exists() })
    val bannerPath: StateFlow<String?> = _bannerPath.asStateFlow()

    private val _avatarPath = MutableStateFlow(prefs.getString(KEY_AVATAR, null)?.takeIf { File(it).exists() })
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    private val _bannerCloudUrl = MutableStateFlow(prefs.getString(KEY_BANNER_CLOUD_URL, null))
    val bannerCloudUrl: StateFlow<String?> = _bannerCloudUrl.asStateFlow()

    private val _avatarCloudUrl = MutableStateFlow(prefs.getString(KEY_AVATAR_CLOUD_URL, null))
    val avatarCloudUrl: StateFlow<String?> = _avatarCloudUrl.asStateFlow()

    private val _bio = MutableStateFlow(prefs.getString(KEY_BIO, "audiophile / music curator · passionate about sound & art") ?: "")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _isPrivate = MutableStateFlow(prefs.getBoolean(KEY_IS_PRIVATE, false))
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _followersCount = MutableStateFlow(prefs.getInt(KEY_FOLLOWERS, 0))
    val followersCount: StateFlow<Int> = _followersCount.asStateFlow()

    private val _followingCount = MutableStateFlow(prefs.getInt(KEY_FOLLOWING, 0))
    val followingCount: StateFlow<Int> = _followingCount.asStateFlow()

    private val _publicPlaylists = MutableStateFlow<List<CatalogPlaylist>>(loadPublicPlaylists())
    val publicPlaylists: StateFlow<List<CatalogPlaylist>> = _publicPlaylists.asStateFlow()

    private val storeScope = CoroutineScope(Dispatchers.IO)

    init {
        // Restore existing profile from Supabase on startup / after data wipe, or sync state
        storeScope.launch {
            if (_customDisplayName.value == null && _avatarPath.value == null) {
                restoreProfileFromCloud()
            }
            syncToSupabase()
        }
    }

    suspend fun restoreProfileFromCloud(identifier: String? = null): Boolean = withContext(Dispatchers.IO) {
        val searchId = identifier ?: prefs.getString(KEY_STABLE_USER_ID, null) ?: userId
        val profile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter {
                    or {
                        eq("id", searchId)
                        if (identifier != null) {
                            eq("username", identifier.removePrefix("@").lowercase())
                            eq("display_name", identifier)
                        }
                    }
                }
                limit(1)
            }.decodeSingleOrNull<SocialProfile>()
        }.getOrNull() ?: return@withContext false

        if (_customDisplayName.value != profile.displayName) _customDisplayName.value = profile.displayName
        if (_customUsername.value != profile.username) _customUsername.value = profile.username
        if (_bio.value != profile.bio) _bio.value = profile.bio
        if (_isPrivate.value != profile.isPrivate) _isPrivate.value = profile.isPrivate
        if (_followersCount.value != profile.followersCount) _followersCount.value = profile.followersCount
        if (_followingCount.value != profile.followingCount) _followingCount.value = profile.followingCount
        if (_avatarCloudUrl.value != profile.avatarUrl) _avatarCloudUrl.value = profile.avatarUrl
        if (_bannerCloudUrl.value != profile.bannerUrl) _bannerCloudUrl.value = profile.bannerUrl

        prefs.edit()
            .putString(KEY_STABLE_USER_ID, profile.id)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_BIO, profile.bio)
            .putBoolean(KEY_IS_PRIVATE, profile.isPrivate)
            .putInt(KEY_FOLLOWERS, profile.followersCount)
            .putInt(KEY_FOLLOWING, profile.followingCount)
            .putString(KEY_AVATAR_CLOUD_URL, profile.avatarUrl)
            .putString(KEY_BANNER_CLOUD_URL, profile.bannerUrl)
            .apply()

        // Cache cloud avatar to local disk immediately if not yet on device to eliminate all UI fluctuation/flashing
        val localAvatar = File(profileDir, "current_avatar.jpg")
        if (!profile.avatarUrl.isNullOrBlank() && (!localAvatar.exists() || _avatarPath.value == null)) {
            storeScope.launch(Dispatchers.IO) {
                runCatching {
                    val bytes = java.net.URL(profile.avatarUrl).readBytes()
                    localAvatar.writeBytes(bytes)
                    _avatarPath.value = localAvatar.absolutePath
                    prefs.edit().putString(KEY_AVATAR, localAvatar.absolutePath).apply()
                }
            }
        }

        // Cache cloud banner to local disk immediately if not yet on device
        val localBanner = File(profileDir, "current_banner.jpg")
        if (!profile.bannerUrl.isNullOrBlank() && (!localBanner.exists() || _bannerPath.value == null)) {
            storeScope.launch(Dispatchers.IO) {
                runCatching {
                    val bytes = java.net.URL(profile.bannerUrl).readBytes()
                    localBanner.writeBytes(bytes)
                    _bannerPath.value = localBanner.absolutePath
                    prefs.edit().putString(KEY_BANNER, localBanner.absolutePath).apply()
                }
            }
        }

        // Restore public playlists from Supabase
        val remotePlaylists = runCatching {
            AmeSupabaseClient.client.from("public_playlists").select {
                filter { eq("user_id", profile.id) }
            }.decodeList<RemotePublicPlaylist>().map {
                CatalogPlaylist(
                    uri = it.playlistUri,
                    name = it.name,
                    subtitle = it.subtitle,
                    artworkUrl = it.artworkUrl,
                )
            }
        }.getOrNull()

        if (!remotePlaylists.isNullOrEmpty()) {
            _publicPlaylists.value = remotePlaylists
            persistPublicPlaylists(remotePlaylists)
        }

        true
    }

    fun isPublic(uri: String): Boolean = _publicPlaylists.value.any { it.uri == uri }

    fun togglePublic(playlist: CatalogPlaylist): Boolean {
        val current = _publicPlaylists.value
        val exists = current.any { it.uri == playlist.uri }
        val updated = if (exists) {
            current.filterNot { it.uri == playlist.uri }
        } else {
            listOf(playlist) + current
        }
        _publicPlaylists.value = updated
        persistPublicPlaylists(updated)

        // Asynchronously update Supabase public_playlists table
        storeScope.launch {
            runCatching {
                if (!exists) {
                    AmeSupabaseClient.client.from("public_playlists").insert(
                        RemotePublicPlaylist(
                            userId = userId,
                            playlistUri = playlist.uri,
                            name = playlist.name,
                            subtitle = playlist.subtitle,
                            artworkUrl = playlist.artworkUrl,
                        )
                    )
                } else {
                    AmeSupabaseClient.client.from("public_playlists").delete {
                        filter {
                            eq("user_id", userId)
                            eq("playlist_uri", playlist.uri)
                        }
                    }
                }
            }.onFailure {
                android.util.Log.e("ProfileStore", "Failed to sync public playlist to Supabase", it)
            }
        }

        return !exists // true if newly made public, false if private
    }

    private fun loadPublicPlaylists(): List<CatalogPlaylist> {
        if (!publicPlaylistsFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CatalogPlaylist>>(publicPlaylistsFile.readText())
        }.getOrDefault(emptyList())
    }

    private fun persistPublicPlaylists(playlists: List<CatalogPlaylist>) {
        runCatching {
            publicPlaylistsFile.writeText(json.encodeToString(playlists))
        }
    }

    suspend fun syncToSupabase(streakDays: Int = 0) = withContext(Dispatchers.IO) {
        val name = _customDisplayName.value ?: "Abhishek Singh"
        val handle = _customUsername.value ?: "abhishek"
        val profile = SocialProfile(
            id = userId,
            username = handle,
            displayName = name,
            avatarUrl = _avatarCloudUrl.value,
            bannerUrl = _bannerCloudUrl.value,
            bio = _bio.value,
            isPrivate = _isPrivate.value,
            followersCount = _followersCount.value,
            followingCount = _followingCount.value,
            streakDays = streakDays,
        )
        runCatching {
            AmeSupabaseClient.client.from("profiles").upsert(profile) {
                onConflict = "id"
            }
        }.onFailure {
            android.util.Log.e("ProfileStore", "Failed to sync profile to Supabase", it)
        }
    }

    suspend fun setBanner(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null

            val maxDim = 1920
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (w, h) = if (ratio > 1f) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }

            val file = File(profileDir, "current_banner.jpg")
            val byteStream = ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, byteStream)
            val compressedBytes = byteStream.toByteArray()
            file.writeBytes(compressedBytes)

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            val path = file.absolutePath
            _bannerPath.value = path
            prefs.edit().putString(KEY_BANNER, path).apply()

            // Upload directly to Supabase Storage banners bucket with cache buster
            runCatching {
                val bucket = AmeSupabaseClient.client.storage.from("banners")
                val fileName = "${userId}.jpg"
                bucket.upload(path = fileName, data = compressedBytes) {
                    upsert = true
                }
                val cloudUrl = "${bucket.publicUrl(fileName)}?t=${System.currentTimeMillis()}"
                _bannerCloudUrl.value = cloudUrl
                prefs.edit().putString(KEY_BANNER_CLOUD_URL, cloudUrl).apply()
            }.onFailure {
                android.util.Log.e("ProfileStore", "Failed to upload banner to Supabase", it)
            }

            syncToSupabase()
            path
        } catch (e: Throwable) {
            android.util.Log.e("ProfileStore", "Failed to save banner", e)
            null
        }
    }

    suspend fun setAvatar(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null

            // 1. Center-crop to a pristine 1:1 square to eliminate all image stretching/warping
            val avatarDim = minOf(bitmap.width, bitmap.height)
            val xOffset = (bitmap.width - avatarDim) / 2
            val yOffset = (bitmap.height - avatarDim) / 2
            val squareBitmap = android.graphics.Bitmap.createBitmap(bitmap, xOffset, yOffset, avatarDim, avatarDim)

            // 2. Scale cleanly to max 512x512 for featherlight WebP/JPEG uploads (~25 KB)
            val maxDim = 512
            val scaled = if (avatarDim > maxDim) {
                android.graphics.Bitmap.createScaledBitmap(squareBitmap, maxDim, maxDim, true).also {
                    if (it != squareBitmap) squareBitmap.recycle()
                }
            } else {
                squareBitmap
            }

            val file = File(profileDir, "current_avatar.jpg")
            val byteStream = ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, byteStream)
            val compressedBytes = byteStream.toByteArray()
            file.writeBytes(compressedBytes)

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            val path = file.absolutePath
            _avatarPath.value = path
            prefs.edit().putString(KEY_AVATAR, path).apply()

            // Upload directly to Supabase Storage avatars bucket with cache buster
            runCatching {
                val bucket = AmeSupabaseClient.client.storage.from("avatars")
                val fileName = "${userId}.jpg"
                bucket.upload(path = fileName, data = compressedBytes) {
                    upsert = true
                }
                val cloudUrl = "${bucket.publicUrl(fileName)}?t=${System.currentTimeMillis()}"
                _avatarCloudUrl.value = cloudUrl
                prefs.edit().putString(KEY_AVATAR_CLOUD_URL, cloudUrl).apply()
            }.onFailure {
                android.util.Log.e("ProfileStore", "Failed to upload avatar to Supabase", it)
            }

            syncToSupabase()
            path
        } catch (e: Throwable) {
            android.util.Log.e("ProfileStore", "Failed to save avatar", e)
            null
        }
    }

    fun setDisplayName(name: String) {
        val trimmed = name.trim()
        _customDisplayName.value = trimmed.ifBlank { null }
        if (trimmed.isBlank()) {
            prefs.edit().remove(KEY_DISPLAY_NAME).apply()
        } else {
            prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
        }
        storeScope.launch { syncToSupabase() }
    }

    fun setUsername(handle: String) {
        val clean = handle.trim().removePrefix("@").lowercase().replace(Regex("[^a-z0-9_]"), "")
        _customUsername.value = clean.ifBlank { null }
        if (clean.isBlank()) {
            prefs.edit().remove(KEY_USERNAME).apply()
        } else {
            prefs.edit().putString(KEY_USERNAME, clean).apply()
        }
        storeScope.launch { syncToSupabase() }
    }

    fun checkUsernameAvailability(handle: String): String? {
        val clean = handle.trim().removePrefix("@").lowercase()
        if (clean.isEmpty()) return null
        if (clean.length < 3) return "Minimum 3 characters"
        if (clean.length > 20) return "Maximum 20 characters"
        if (!clean.matches(Regex("^[a-z0-9_]+$"))) return "Only letters, numbers, and underscores"
        if (clean in RESERVED_USERNAMES) return "@$clean is reserved"
        return null // null indicates locally available!
    }

    suspend fun checkUsernameAvailabilityRemote(handle: String): String? = withContext(Dispatchers.IO) {
        val localError = checkUsernameAvailability(handle)
        if (localError != null) return@withContext localError

        val clean = handle.trim().removePrefix("@").lowercase()
        runCatching {
            val taken = AmeSupabaseClient.client.from("profiles").select {
                filter {
                    eq("username", clean)
                    neq("id", userId)
                }
                limit(1)
            }.decodeList<SocialProfile>()
            if (taken.isNotEmpty()) "@$clean is already taken" else null
        }.getOrNull()
    }

    fun setBio(newBio: String) {
        _bio.value = newBio
        prefs.edit().putString(KEY_BIO, newBio).apply()
        storeScope.launch { syncToSupabase() }
    }

    fun setPrivate(isPrivate: Boolean) {
        _isPrivate.value = isPrivate
        prefs.edit().putBoolean(KEY_IS_PRIVATE, isPrivate).apply()
        storeScope.launch { syncToSupabase() }
    }

    suspend fun clearBanner() = withContext(Dispatchers.IO) {
        _bannerPath.value?.let { runCatching { File(it).delete() } }
        _bannerPath.value = null
        _bannerCloudUrl.value = null
        prefs.edit().remove(KEY_BANNER).remove(KEY_BANNER_CLOUD_URL).apply()
        syncToSupabase()
    }

    suspend fun clearAvatar() = withContext(Dispatchers.IO) {
        _avatarPath.value?.let { runCatching { File(it).delete() } }
        _avatarPath.value = null
        _avatarCloudUrl.value = null
        prefs.edit().remove(KEY_AVATAR).remove(KEY_AVATAR_CLOUD_URL).apply()
        syncToSupabase()
    }

    companion object {
        private const val FILE_NAME = "ame_profile_custom"
        private const val KEY_USER_ID = "supabase_user_id"
        private const val KEY_STABLE_USER_ID = "supabase_stable_user_id"
        private const val KEY_DISPLAY_NAME = "custom_display_name"
        private const val KEY_USERNAME = "custom_username"
        private const val KEY_BANNER = "custom_banner_path"
        private const val KEY_AVATAR = "custom_avatar_path"
        private const val KEY_BANNER_CLOUD_URL = "custom_banner_cloud_url"
        private const val KEY_AVATAR_CLOUD_URL = "custom_avatar_cloud_url"
        private const val KEY_BIO = "custom_bio"
        private const val KEY_IS_PRIVATE = "custom_is_private"
        private const val KEY_FOLLOWERS = "custom_followers"
        private const val KEY_FOLLOWING = "custom_following"

        private val RESERVED_USERNAMES = setOf(
            "admin", "administrator", "ame", "ameapp", "root", "support",
            "official", "verified", "moderator", "system", "help", "bot",
            "music", "null", "undefined", "staff", "abhishek_official"
        )
    }
}

