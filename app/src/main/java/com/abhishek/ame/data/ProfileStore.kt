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
import java.security.MessageDigest
import java.util.UUID

/**
 * Robust SHA-256 password hashing with application-level pepper and per-user handle salting.
 */
object PasswordHasher {
    private const val PEPPER = "ame_music_salt_v1_"

    fun hashPassword(username: String, password: String): String {
        val input = "$PEPPER${username.lowercase()}_$password"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

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

    var userId: String = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_USER_ID, it).apply()
    }
        private set

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

    private val _bio = MutableStateFlow(prefs.getString(KEY_BIO, "") ?: "")
    val bio: StateFlow<String> = _bio.asStateFlow()

    private val _isPrivate = MutableStateFlow(prefs.getBoolean(KEY_IS_PRIVATE, false))
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _followersCount = MutableStateFlow(prefs.getInt(KEY_FOLLOWERS, 0))
    val followersCount: StateFlow<Int> = _followersCount.asStateFlow()

    private val _followingCount = MutableStateFlow(prefs.getInt(KEY_FOLLOWING, 0))
    val followingCount: StateFlow<Int> = _followingCount.asStateFlow()

    private val _publicPlaylists = MutableStateFlow<List<CatalogPlaylist>>(loadPublicPlaylists())
    val publicPlaylists: StateFlow<List<CatalogPlaylist>> = _publicPlaylists.asStateFlow()

    private val _streakDays = MutableStateFlow(prefs.getInt(KEY_STREAK_DAYS, 0))
    val streakDays: StateFlow<Int> = _streakDays.asStateFlow()

    private val _listeningActivity = MutableStateFlow(loadListeningActivity())
    val listeningActivity: StateFlow<Map<String, Int>> = _listeningActivity.asStateFlow()

    private fun loadListeningActivity(): Map<String, Int> {
        val raw = prefs.getString(KEY_LISTENING_ACTIVITY, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, Int>>(raw)
        }.getOrDefault(emptyMap())
    }

    private val _hasCreatedProfile = MutableStateFlow(
        !_customDisplayName.value.isNullOrBlank() && !_customUsername.value.isNullOrBlank()
    )
    val hasCreatedProfile: StateFlow<Boolean> = _hasCreatedProfile.asStateFlow()

    private fun updateHasCreatedProfile() {
        _hasCreatedProfile.value = !_customDisplayName.value.isNullOrBlank() && !_customUsername.value.isNullOrBlank()
    }

    private val storeScope = CoroutineScope(Dispatchers.IO)

    init {
        // Restore existing profile from Supabase on startup / after data wipe if a known stable ID was previously saved
        storeScope.launch {
            val stableId = prefs.getString(KEY_STABLE_USER_ID, null)
            if (_customDisplayName.value == null && _customUsername.value == null && stableId != null) {
                restoreProfileFromCloud(stableId)
            }
            if (_hasCreatedProfile.value) {
                syncToSupabase()
            }
        }
    }

    suspend fun restoreProfileFromCloud(stableId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val searchId = stableId ?: prefs.getString(KEY_STABLE_USER_ID, null) ?: return@withContext false
        val profile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter {
                    eq("id", searchId)
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

        _streakDays.value = profile.streakDays
        if (profile.listeningActivity.isNotEmpty()) {
            _listeningActivity.value = profile.listeningActivity
            prefs.edit().putString(KEY_LISTENING_ACTIVITY, json.encodeToString(profile.listeningActivity)).apply()
        }

        userId = profile.id
        prefs.edit()
            .putString(KEY_USER_ID, profile.id)
            .putString(KEY_STABLE_USER_ID, profile.id)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_BIO, profile.bio)
            .putBoolean(KEY_IS_PRIVATE, profile.isPrivate)
            .putInt(KEY_FOLLOWERS, profile.followersCount)
            .putInt(KEY_FOLLOWING, profile.followingCount)
            .putInt(KEY_STREAK_DAYS, profile.streakDays)
            .putString(KEY_AVATAR_CLOUD_URL, profile.avatarUrl)
            .putString(KEY_BANNER_CLOUD_URL, profile.bannerUrl)
            .apply()

        // Cache cloud avatar to local disk immediately if not yet on device to eliminate all UI fluctuation/flashing
        val localAvatar = File(profileDir, "current_avatar.webp")
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
        val localBanner = File(profileDir, "current_banner.webp")
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

        updateHasCreatedProfile()
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

    fun updateFollowCounts(followers: Int, following: Int) {
        _followersCount.value = followers
        _followingCount.value = following
        prefs.edit()
            .putInt(KEY_FOLLOWERS, followers)
            .putInt(KEY_FOLLOWING, following)
            .apply()
    }

    private fun compressBitmapToWebp(bitmap: android.graphics.Bitmap, quality: Int = 82): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, quality, byteStream)
        return byteStream.toByteArray()
    }

    suspend fun syncToSupabase(
        streakDays: Int? = null,
        listeningActivity: Map<String, Int>? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val name = _customDisplayName.value?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext false
        val handle = _customUsername.value?.trim()?.removePrefix("@")?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext false

        // Auto-heal missing cloud avatar if local file exists on disk
        if (_avatarCloudUrl.value.isNullOrBlank()) {
            val localAvatar = listOfNotNull(
                _avatarPath.value?.let { File(it) },
                File(profileDir, "current_avatar.webp"),
                File(profileDir, "current_avatar.jpg"),
            ).firstOrNull { it.exists() && it.length() > 0 }

            if (localAvatar != null) {
                runCatching {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(localAvatar.absolutePath)
                    if (bitmap != null) {
                        val avatarDim = minOf(bitmap.width, bitmap.height)
                        val xOffset = (bitmap.width - avatarDim) / 2
                        val yOffset = (bitmap.height - avatarDim) / 2
                        val squareBitmap = android.graphics.Bitmap.createBitmap(bitmap, xOffset, yOffset, avatarDim, avatarDim)
                        val scaled = if (avatarDim > 512) {
                            android.graphics.Bitmap.createScaledBitmap(squareBitmap, 512, 512, true).also {
                                if (it != squareBitmap) squareBitmap.recycle()
                            }
                        } else {
                            squareBitmap
                        }
                        val webpBytes = compressBitmapToWebp(scaled, 82)
                        if (scaled != bitmap) scaled.recycle()
                        bitmap.recycle()

                        val webpFile = File(profileDir, "current_avatar.webp")
                        webpFile.writeBytes(webpBytes)
                        _avatarPath.value = webpFile.absolutePath
                        prefs.edit().putString(KEY_AVATAR, webpFile.absolutePath).apply()

                        val bucket = AmeSupabaseClient.client.storage.from("avatars")
                        val fileName = "${userId}.webp"
                        bucket.upload(path = fileName, data = webpBytes) { upsert = true }
                        val cloudUrl = "${bucket.publicUrl(fileName)}?t=${System.currentTimeMillis()}"
                        _avatarCloudUrl.value = cloudUrl
                        prefs.edit().putString(KEY_AVATAR_CLOUD_URL, cloudUrl).apply()
                    }
                }.onFailure {
                    android.util.Log.e("ProfileStore", "Auto-heal avatar upload failed", it)
                }
            }
        }

        // Auto-heal missing cloud banner if local file exists on disk
        if (_bannerCloudUrl.value.isNullOrBlank()) {
            val localBanner = listOfNotNull(
                _bannerPath.value?.let { File(it) },
                File(profileDir, "current_banner.webp"),
                File(profileDir, "current_banner.jpg"),
            ).firstOrNull { it.exists() && it.length() > 0 }

            if (localBanner != null) {
                runCatching {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(localBanner.absolutePath)
                    if (bitmap != null) {
                        val maxDim = 1200
                        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                            val (w, h) = if (ratio > 1f) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                            android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
                        } else {
                            bitmap
                        }
                        val webpBytes = compressBitmapToWebp(scaled, 82)
                        if (scaled != bitmap) scaled.recycle()
                        bitmap.recycle()

                        val webpFile = File(profileDir, "current_banner.webp")
                        webpFile.writeBytes(webpBytes)
                        _bannerPath.value = webpFile.absolutePath
                        prefs.edit().putString(KEY_BANNER, webpFile.absolutePath).apply()

                        val bucket = AmeSupabaseClient.client.storage.from("banners")
                        val fileName = "${userId}.webp"
                        bucket.upload(path = fileName, data = webpBytes) { upsert = true }
                        val cloudUrl = "${bucket.publicUrl(fileName)}?t=${System.currentTimeMillis()}"
                        _bannerCloudUrl.value = cloudUrl
                        prefs.edit().putString(KEY_BANNER_CLOUD_URL, cloudUrl).apply()
                    }
                }.onFailure {
                    android.util.Log.e("ProfileStore", "Auto-heal banner upload failed", it)
                }
            }
        }

        // Fetch real-time follower & following counts directly from follows table so we never overwrite with stale counts
        val realFollowers = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("following_id", userId) }
            }.decodeList<com.abhishek.ame.data.social.RemoteFollow>().size
        }.getOrNull() ?: _followersCount.value

        val realFollowing = runCatching {
            AmeSupabaseClient.client.from("follows").select {
                filter { eq("follower_id", userId) }
            }.decodeList<com.abhishek.ame.data.social.RemoteFollow>().size
        }.getOrNull() ?: _followingCount.value

        updateFollowCounts(realFollowers, realFollowing)

        val streak = streakDays ?: _streakDays.value
        if (streakDays != null) {
            _streakDays.value = streakDays
            prefs.edit().putInt(KEY_STREAK_DAYS, streakDays).apply()
        }

        val activity = listeningActivity ?: _listeningActivity.value
        if (listeningActivity != null) {
            _listeningActivity.value = listeningActivity
            prefs.edit().putString(KEY_LISTENING_ACTIVITY, json.encodeToString(listeningActivity)).apply()
        }

        val profile = SocialProfile(
            id = userId,
            username = handle,
            displayName = name,
            avatarUrl = _avatarCloudUrl.value,
            bannerUrl = _bannerCloudUrl.value,
            bio = _bio.value,
            isPrivate = _isPrivate.value,
            followersCount = realFollowers,
            followingCount = realFollowing,
            streakDays = streak,
            listeningActivity = activity,
        )
        runCatching {
            AmeSupabaseClient.client.from("profiles").upsert(profile) {
                onConflict = "id"
            }
            true
        }.onFailure {
            android.util.Log.e("ProfileStore", "Failed to sync profile to Supabase", it)
        }.getOrDefault(false)
    }

    private suspend fun uploadAvatarInternal(uri: android.net.Uri): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Pair(null, null)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Pair(null, null)

            // Center-crop to a 1:1 square to eliminate all image distortion
            val avatarDim = minOf(bitmap.width, bitmap.height)
            val xOffset = (bitmap.width - avatarDim) / 2
            val yOffset = (bitmap.height - avatarDim) / 2
            val squareBitmap = android.graphics.Bitmap.createBitmap(bitmap, xOffset, yOffset, avatarDim, avatarDim)

            val maxDim = 512
            val scaled = if (avatarDim > maxDim) {
                android.graphics.Bitmap.createScaledBitmap(squareBitmap, maxDim, maxDim, true).also {
                    if (it != squareBitmap) squareBitmap.recycle()
                }
            } else {
                squareBitmap
            }

            val webpBytes = compressBitmapToWebp(scaled, 82)
            val file = File(profileDir, "current_avatar.webp")
            file.writeBytes(webpBytes)

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            val path = file.absolutePath
            prefs.edit().putString(KEY_AVATAR, path).apply()

            var cloudUrl: String? = null
            runCatching {
                val bucket = AmeSupabaseClient.client.storage.from("avatars")
                val fileName = "${userId}.webp"
                bucket.upload(path = fileName, data = webpBytes) {
                    upsert = true
                }
                cloudUrl = "${bucket.publicUrl(fileName)}?t=${System.currentTimeMillis()}"
                prefs.edit().putString(KEY_AVATAR_CLOUD_URL, cloudUrl).apply()
            }.onFailure {
                android.util.Log.e("ProfileStore", "Failed to upload avatar to Supabase", it)
            }

            Pair(path, cloudUrl)
        } catch (e: Throwable) {
            android.util.Log.e("ProfileStore", "Failed to save avatar", e)
            Pair(null, null)
        }
    }

    suspend fun createProfile(
        displayName: String,
        username: String,
        password: String,
        bio: String = "",
        isPrivate: Boolean = false,
        avatarUri: android.net.Uri? = null,
    ): Result<SocialProfile> = withContext(Dispatchers.IO) {
        val cleanName = displayName.trim()
        if (cleanName.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Display name must be at least 2 characters"))
        }

        val cleanHandle = username.trim().removePrefix("@").lowercase()
        val availError = checkUsernameAvailabilityRemote(cleanHandle)
        if (availError != null) {
            return@withContext Result.failure(IllegalStateException(availError))
        }

        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        val passHash = PasswordHasher.hashPassword(cleanHandle, password)

        // Upload avatar if selected during profile creation
        var localAvatarPath: String? = null
        var cloudAvatarUrl: String? = null
        if (avatarUri != null) {
            val uploaded = uploadAvatarInternal(avatarUri)
            localAvatarPath = uploaded.first
            cloudAvatarUrl = uploaded.second
        }

        val profile = SocialProfile(
            id = userId,
            username = cleanHandle,
            displayName = cleanName,
            avatarUrl = cloudAvatarUrl,
            bannerUrl = _bannerCloudUrl.value,
            bio = bio.trim(),
            isPrivate = isPrivate,
            followersCount = 0,
            followingCount = 0,
            streakDays = 0,
            passwordHash = passHash,
        )

        val upsertResult = runCatching {
            AmeSupabaseClient.client.from("profiles").upsert(profile) {
                onConflict = "id"
            }
        }

        if (upsertResult.isFailure) {
            val err = upsertResult.exceptionOrNull()
            android.util.Log.e("ProfileStore", "Failed to create profile in Supabase", err)
            return@withContext Result.failure(err ?: Exception("Database error"))
        }

        // Persist locally
        _customDisplayName.value = cleanName
        _customUsername.value = cleanHandle
        _bio.value = bio.trim()
        _isPrivate.value = isPrivate
        if (localAvatarPath != null) _avatarPath.value = localAvatarPath
        if (cloudAvatarUrl != null) _avatarCloudUrl.value = cloudAvatarUrl
        updateHasCreatedProfile()

        prefs.edit()
            .putString(KEY_STABLE_USER_ID, userId)
            .putString(KEY_DISPLAY_NAME, cleanName)
            .putString(KEY_USERNAME, cleanHandle)
            .putString(KEY_BIO, bio.trim())
            .putBoolean(KEY_IS_PRIVATE, isPrivate)
            .putString(KEY_PASSWORD_HASH, passHash)
            .apply()

        Result.success(profile)
    }

    suspend fun loginProfile(
        username: String,
        password: String,
    ): Result<SocialProfile> = withContext(Dispatchers.IO) {
        val cleanHandle = username.trim().removePrefix("@").lowercase()
        if (cleanHandle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your username"))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password"))
        }

        val profile = runCatching {
            AmeSupabaseClient.client.from("profiles").select {
                filter {
                    eq("username", cleanHandle)
                }
                limit(1)
            }.decodeSingleOrNull<SocialProfile>()
        }.getOrNull()

        if (profile == null) {
            return@withContext Result.failure(IllegalStateException("No account found with username @$cleanHandle"))
        }

        val storedHash = profile.passwordHash
        if (storedHash.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Security Lock: Account @$cleanHandle has no password configured. Please set a password in Supabase or register a new handle.")
            )
        }

        val expectedHash = PasswordHasher.hashPassword(cleanHandle, password)
        if (storedHash != expectedHash) {
            return@withContext Result.failure(IllegalStateException("Incorrect password. Access denied."))
        }

        // Restore active profile state
        if (_customDisplayName.value != profile.displayName) _customDisplayName.value = profile.displayName
        if (_customUsername.value != profile.username) _customUsername.value = profile.username
        if (_bio.value != profile.bio) _bio.value = profile.bio
        if (_isPrivate.value != profile.isPrivate) _isPrivate.value = profile.isPrivate
        if (_followersCount.value != profile.followersCount) _followersCount.value = profile.followersCount
        if (_followingCount.value != profile.followingCount) _followingCount.value = profile.followingCount
        if (_avatarCloudUrl.value != profile.avatarUrl) _avatarCloudUrl.value = profile.avatarUrl
        if (_bannerCloudUrl.value != profile.bannerUrl) _bannerCloudUrl.value = profile.bannerUrl

        _streakDays.value = profile.streakDays
        if (profile.listeningActivity.isNotEmpty()) {
            _listeningActivity.value = profile.listeningActivity
            prefs.edit().putString(KEY_LISTENING_ACTIVITY, json.encodeToString(profile.listeningActivity)).apply()
        }

        userId = profile.id
        prefs.edit()
            .putString(KEY_USER_ID, profile.id)
            .putString(KEY_STABLE_USER_ID, profile.id)
            .putString(KEY_DISPLAY_NAME, profile.displayName)
            .putString(KEY_USERNAME, profile.username)
            .putString(KEY_BIO, profile.bio)
            .putBoolean(KEY_IS_PRIVATE, profile.isPrivate)
            .putInt(KEY_FOLLOWERS, profile.followersCount)
            .putInt(KEY_FOLLOWING, profile.followingCount)
            .putInt(KEY_STREAK_DAYS, profile.streakDays)
            .putString(KEY_AVATAR_CLOUD_URL, profile.avatarUrl)
            .putString(KEY_BANNER_CLOUD_URL, profile.bannerUrl)
            .putString(KEY_PASSWORD_HASH, expectedHash)
            .apply()

        // Cache cloud avatar & banner to local disk
        val localAvatar = File(profileDir, "current_avatar.webp")
        if (!profile.avatarUrl.isNullOrBlank()) {
            storeScope.launch(Dispatchers.IO) {
                runCatching {
                    val bytes = java.net.URL(profile.avatarUrl).readBytes()
                    localAvatar.writeBytes(bytes)
                    _avatarPath.value = localAvatar.absolutePath
                    prefs.edit().putString(KEY_AVATAR, localAvatar.absolutePath).apply()
                }
            }
        }

        val localBanner = File(profileDir, "current_banner.webp")
        if (!profile.bannerUrl.isNullOrBlank()) {
            storeScope.launch(Dispatchers.IO) {
                runCatching {
                    val bytes = java.net.URL(profile.bannerUrl).readBytes()
                    localBanner.writeBytes(bytes)
                    _bannerPath.value = localBanner.absolutePath
                    prefs.edit().putString(KEY_BANNER, localBanner.absolutePath).apply()
                }
            }
        }

        // Restore public playlists
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

        updateHasCreatedProfile()
        Result.success(profile)
    }

    fun logoutProfile() {
        prefs.edit()
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_USERNAME)
            .remove(KEY_BIO)
            .remove(KEY_IS_PRIVATE)
            .remove(KEY_FOLLOWERS)
            .remove(KEY_FOLLOWING)
            .remove(KEY_AVATAR)
            .remove(KEY_BANNER)
            .remove(KEY_AVATAR_CLOUD_URL)
            .remove(KEY_BANNER_CLOUD_URL)
            .remove(KEY_PASSWORD_HASH)
            .remove(KEY_STABLE_USER_ID)
            .apply()

        runCatching { File(profileDir, "current_avatar.webp").delete() }
        runCatching { File(profileDir, "current_banner.webp").delete() }
        runCatching { publicPlaylistsFile.delete() }

        _customDisplayName.value = null
        _customUsername.value = null
        _avatarPath.value = null
        _bannerPath.value = null
        _avatarCloudUrl.value = null
        _bannerCloudUrl.value = null
        _bio.value = ""
        _isPrivate.value = false
        _followersCount.value = 0
        _followingCount.value = 0
        _publicPlaylists.value = emptyList()

        val newGuestId = UUID.randomUUID().toString()
        userId = newGuestId
        prefs.edit().putString(KEY_USER_ID, newGuestId).apply()

        updateHasCreatedProfile()
    }

    suspend fun setBanner(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null

            val maxDim = 1200
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (w, h) = if (ratio > 1f) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }

            val webpBytes = compressBitmapToWebp(scaled, 82)
            val file = File(profileDir, "current_banner.webp")
            file.writeBytes(webpBytes)

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            val path = file.absolutePath
            _bannerPath.value = path
            prefs.edit().putString(KEY_BANNER, path).apply()

            // Upload directly to Supabase Storage banners bucket with cache buster
            runCatching {
                val bucket = AmeSupabaseClient.client.storage.from("banners")
                val fileName = "${userId}.webp"
                bucket.upload(path = fileName, data = webpBytes) {
                    upsert = true
                }
                val cloudUrl = "${bucket.publicUrl(fileName)}?t=${System.currentTimeMillis()}"
                _bannerCloudUrl.value = cloudUrl
                prefs.edit().putString(KEY_BANNER_CLOUD_URL, cloudUrl).apply()
            }.onFailure {
                android.util.Log.e("ProfileStore", "Failed to upload banner to Supabase", it)
            }

            if (_hasCreatedProfile.value) {
                syncToSupabase()
            }
            path
        } catch (e: Throwable) {
            android.util.Log.e("ProfileStore", "Failed to save banner", e)
            null
        }
    }

    suspend fun setAvatar(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        val (path, cloudUrl) = uploadAvatarInternal(uri)
        if (path != null) {
            _avatarPath.value = path
        }
        if (cloudUrl != null) {
            _avatarCloudUrl.value = cloudUrl
        }
        if (_hasCreatedProfile.value) {
            syncToSupabase()
        }
        path
    }

    fun setDisplayName(name: String) {
        val trimmed = name.trim()
        _customDisplayName.value = trimmed.ifBlank { null }
        if (trimmed.isBlank()) {
            prefs.edit().remove(KEY_DISPLAY_NAME).apply()
        } else {
            prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
        }
        updateHasCreatedProfile()
        if (_hasCreatedProfile.value) {
            storeScope.launch { syncToSupabase() }
        }
    }

    fun setUsername(handle: String) {
        val clean = handle.trim().removePrefix("@").lowercase().replace(Regex("[^a-z0-9_]"), "")
        _customUsername.value = clean.ifBlank { null }
        if (clean.isBlank()) {
            prefs.edit().remove(KEY_USERNAME).apply()
        } else {
            prefs.edit().putString(KEY_USERNAME, clean).apply()
        }
        updateHasCreatedProfile()
        if (_hasCreatedProfile.value) {
            storeScope.launch { syncToSupabase() }
        }
    }

    fun checkUsernameAvailability(handle: String): String? {
        val clean = handle.trim().removePrefix("@").lowercase()
        if (clean.isEmpty()) return "Username cannot be empty"
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
        }.getOrElse {
            android.util.Log.e("ProfileStore", "Failed to check username availability remote", it)
            null
        }
    }

    fun setBio(newBio: String) {
        val trimmed = newBio.trim()
        _bio.value = trimmed
        prefs.edit().putString(KEY_BIO, trimmed).apply()
        if (_hasCreatedProfile.value) {
            storeScope.launch { syncToSupabase() }
        }
    }

    fun setPrivate(isPrivate: Boolean) {
        _isPrivate.value = isPrivate
        prefs.edit().putBoolean(KEY_IS_PRIVATE, isPrivate).apply()
        if (_hasCreatedProfile.value) {
            storeScope.launch { syncToSupabase() }
        }
    }

    suspend fun clearBanner() = withContext(Dispatchers.IO) {
        _bannerPath.value?.let { runCatching { File(it).delete() } }
        _bannerPath.value = null
        _bannerCloudUrl.value = null
        prefs.edit().remove(KEY_BANNER).remove(KEY_BANNER_CLOUD_URL).apply()
        if (_hasCreatedProfile.value) {
            syncToSupabase()
        }
    }

    suspend fun clearAvatar() = withContext(Dispatchers.IO) {
        _avatarPath.value?.let { runCatching { File(it).delete() } }
        _avatarPath.value = null
        _avatarCloudUrl.value = null
        prefs.edit().remove(KEY_AVATAR).remove(KEY_AVATAR_CLOUD_URL).apply()
        if (_hasCreatedProfile.value) {
            syncToSupabase()
        }
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
        private const val KEY_STREAK_DAYS = "custom_streak_days"
        private const val KEY_LISTENING_ACTIVITY = "custom_listening_activity"
        private const val KEY_PASSWORD_HASH = "custom_password_hash"

        private val RESERVED_USERNAMES = setOf(
            "admin", "administrator", "ame", "ameapp", "root", "support",
            "official", "verified", "moderator", "system", "help", "bot",
            "music", "null", "undefined", "staff", "abhishek_official"
        )
    }
}

