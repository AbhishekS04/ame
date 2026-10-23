package dev.lelonio.square.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Stores custom banner/cover images for playlists chosen by the user.
 *
 * Persisted in local app storage and SharedPreferences, keeping custom
 * images for playlists across runs without altering server state.
 */
class CustomBannerStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val bannersDir = File(appContext.filesDir, "custom_banners").apply { mkdirs() }

    private val _banners = MutableStateFlow<Map<String, String>>(load())
    val banners: StateFlow<Map<String, String>> = _banners.asStateFlow()

    fun getBanner(uri: String): String? = _banners.value[uri]

    suspend fun setBanner(uri: String, stream: InputStream): String = withContext(Dispatchers.IO) {
        val hash = hash(uri)
        val file = File(bannersDir, "$hash.jpg")
        file.outputStream().use { out ->
            stream.copyTo(out)
        }
        val path = file.absolutePath
        val updated = _banners.value + (uri to path)
        _banners.value = updated
        prefs.edit().putString(uri, path).apply()
        path
    }

    suspend fun removeBanner(uri: String): Boolean = withContext(Dispatchers.IO) {
        val path = _banners.value[uri] ?: return@withContext false
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        val updated = _banners.value - uri
        _banners.value = updated
        prefs.edit().remove(uri).apply()
        true
    }

    private fun load(): Map<String, String> {
        val all = prefs.all
        val result = mutableMapOf<String, String>()
        for ((key, value) in all) {
            if (value is String && File(value).exists()) {
                result[key] = value
            }
        }
        return result
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val FILE_NAME = "square_custom_banners"
    }
}
