package com.abhishek.ame.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.abhishek.ame.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Updates the app from its own GitHub releases.
 *
 * Square cannot go on the Play Store — it re-implements a protocol whose terms
 * forbid it — so there is nothing to deliver an update but the app itself. The
 * releases page is already the distribution channel; this reads it.
 *
 * Nothing here has to verify what it downloads. Android refuses an update
 * signed with a different key than the installed copy, so an APK that is not
 * the one built with the project's keystore simply fails to install. A hash
 * check on top would look reassuring and add nothing the platform does not
 * already enforce.
 */
class Updater(context: Context) {

    private val app = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /** The last whole percent handed to the notification; see [download]. */
    private var lastReported = -1

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    data class Release(
        val version: String,
        val url: String,
        val bytes: Long,
        val notes: String,
    )

    private val _channelReleases = MutableStateFlow<Map<com.abhishek.ame.data.PreferencesStore.UpdateChannel, Release>>(emptyMap())
    val channelReleases: StateFlow<Map<com.abhishek.ame.data.PreferencesStore.UpdateChannel, Release>> = _channelReleases.asStateFlow()

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(
            val version: String,
            val url: String,
            val bytes: Long,
            /**
             * What the release says about itself, as written on the releases
             * page.
             *
             * An update is a thing being asked for rather than announced, and
             * "there is a new version" is not enough to answer with. Empty when
             * the release carries no text, which is a release worth showing
             * anyway.
             */
            val notes: String = "",
        ) : State
        /** 0f..1f, or null while the server sends no length to measure against. */
        data class Downloading(val progress: Float?) : State
        /** Handed to the system installer; the dialog is Android's, not ours. */
        data object Installing : State
        data class Failed(val reason: String) : State
    }

    /**
     * Asks GitHub what the latest release is.
     *
     * Unauthenticated, which allows sixty requests an hour per address — far
     * more than a button can spend. It is still a request the user did not ask
     * for, which is why nothing here runs on its own: it is called when the
     * button is pressed and at no other time.
     */
    suspend fun check(channel: com.abhishek.ame.data.PreferencesStore.UpdateChannel = com.abhishek.ame.data.PreferencesStore.UpdateChannel.STABLE) {
        _state.value = State.Checking
        val result = runCatching { withContext(Dispatchers.IO) { fetchRelease(channel) } }
            .getOrElse {
                android.util.Log.w(TAG, "check failed: $it")
                _state.value = State.Failed(it.message ?: "network")
                return
            }

        if (result != null) {
            _channelReleases.value += (channel to result)
        }

        val isInstalledBeta = BuildConfig.VERSION_NAME.contains("-")
        val shouldOffer = when (channel) {
            com.abhishek.ame.data.PreferencesStore.UpdateChannel.STABLE -> {
                if (isInstalledBeta) {
                    result != null && result.version != BuildConfig.VERSION_NAME
                } else {
                    result != null && isNewer(result.version, BuildConfig.VERSION_NAME)
                }
            }
            com.abhishek.ame.data.PreferencesStore.UpdateChannel.BETA -> {
                if (!isInstalledBeta) {
                    result != null && (isNewer(result.version, BuildConfig.VERSION_NAME) ||
                        result.version.substringBefore('-') == BuildConfig.VERSION_NAME)
                } else {
                    result != null && isNewer(result.version, BuildConfig.VERSION_NAME)
                }
            }
        }

        if (result == null || !shouldOffer) {
            _state.value = State.UpToDate
            return
        }
        _state.value =
            State.Available(result.version, result.url, result.bytes, result.notes)
    }

    /**
     * Checks, and installs whatever it finds, as one action.
     *
     * Split in two internally but never in the interface: "there is an update"
     * is not a decision the user has anything to decide with — they pressed a
     * row that says update, and being asked again is a step, not a safeguard.
     * The safeguard is Android's own dialog, which no app can skip.
     *
     * @return the update it could not install without permission, so the caller
     *   can ask for it and come back here rather than starting over.
     */
    suspend fun checkAndInstall(channel: com.abhishek.ame.data.PreferencesStore.UpdateChannel = com.abhishek.ame.data.PreferencesStore.UpdateChannel.STABLE): State.Available? {
        check(channel)
        val update = _state.value as? State.Available ?: return null
        if (!canInstall()) {
            _state.value = State.Failed(REASON_PERMISSION)
            return update
        }
        install(update)
        return null
    }

    private fun fetchRelease(channel: com.abhishek.ame.data.PreferencesStore.UpdateChannel): Release? {
        val url = if (channel == com.abhishek.ame.data.PreferencesStore.UpdateChannel.STABLE) {
            "https://api.github.com/repos/$REPO/releases/latest"
        } else {
            "https://api.github.com/repos/$REPO/releases?per_page=5"
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            val body = if (channel == com.abhishek.ame.data.PreferencesStore.UpdateChannel.STABLE) {
                json.parseToJsonElement(text) as? JsonObject
            } else {
                val array = json.parseToJsonElement(text) as? JsonArray
                val releases = array?.filterIsInstance<JsonObject>().orEmpty()
                releases.firstOrNull { it["prerelease"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true }
                    ?: releases.firstOrNull()
            } ?: return null

            val tag = body["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null
            val apks = (body["assets"] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                .orEmpty()
            fun named(part: String) = apks.firstOrNull {
                it["name"]?.jsonPrimitive?.content?.contains(part) == true
            }
            val asset = android.os.Build.SUPPORTED_ABIS.firstNotNullOfOrNull(::named)
                ?: named("universal")
                ?: apks.firstOrNull()
                ?: return null
            val downloadUrl = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
            val size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val notes = body["body"]?.jsonPrimitive?.content.orEmpty().trim()
            return Release(tag, downloadUrl, size, notes)
        }
    }

    /**
     * Downloads the APK and hands it to the system installer.
     *
     * The confirmation dialog is Android's and cannot be skipped: only a system
     * app installs silently. That is the right outcome — an app that could
     * replace itself unattended is one the user has to trust rather more than
     * this one asks to be trusted.
     */
    fun install(update: State.Available) {
        if (!canInstall()) {
            _state.value = State.Failed(REASON_PERMISSION)
            return
        }
        // Handed to a service rather than done here.
        //
        // This used to run in whatever scope the screen gave it, so leaving the
        // app — or merely opening the player — cancelled the download halfway
        // with nothing to show for it. See UpdateService.
        _state.value = State.Downloading(null)
        UpdateService.start(app, update)
    }

    /**
     * The download itself, called by the service that is allowed to finish it.
     *
     * @param onProgress told as often as the bytes arrive, so the notification
     *   can say how far along it is; null while the server sends no length.
     */
    suspend fun fetchAndInstall(
        url: String,
        version: String,
        bytes: Long,
        onProgress: (Float?) -> Unit,
    ) {
        val update = State.Available(version, url, bytes)
        _state.value = State.Downloading(null)
        onProgress(null)

        val apk = runCatching {
            withContext(Dispatchers.IO) { download(update, onProgress) }
        }.getOrElse {
            android.util.Log.w(TAG, "download failed: $it")
            _state.value = State.Failed(it.message ?: "download")
            return
        }

        runCatching { withContext(Dispatchers.IO) { commit(apk) } }
            .onSuccess { _state.value = State.Installing }
            .onFailure {
                android.util.Log.w(TAG, "install failed: $it")
                apk.delete()
                _state.value = State.Failed(it.message ?: "install")
            }
    }

    private fun download(update: State.Available, onProgress: (Float?) -> Unit): File {
        // cacheDir, not filesDir: once the installer has read it the file is
        // dead weight, and twenty megabytes is worth letting the system reclaim
        // if the install never happens.
        val target = File(app.cacheDir, "update.apk")
        target.delete()
        lastReported = -1

        val request = Request.Builder().url(update.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            val total = body.contentLength().takeIf { it > 0 } ?: update.bytes
            var written = 0L

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = written.toFloat() / total
                            _state.value = State.Downloading(progress)
                            // Rounded to a percent before it is reported: a
                            // notification redrawn on every sixty-fourth
                            // kilobyte is three hundred redraws for a number
                            // that changed a hundred times.
                            val step = (progress * 100).toInt()
                            if (step != lastReported) {
                                lastReported = step
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }
        return target
    }

    private fun commit(apk: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("ame", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.commit(
                PendingIntent.getBroadcast(
                    app,
                    0,
                    Intent(app, InstallReceiver::class.java)
                        .setPackage(app.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ).intentSender,
            )
        }
        apk.delete()
    }

    /** Whether the user has allowed this app to install packages at all. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    /**
     * The settings page where that permission is granted.
     *
     * No `NEW_TASK` flag: it is started for a result, so the caller is told when
     * the user comes back and can carry on with the install they already asked
     * for rather than making them press the row a second time.
     */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${app.packageName}"))

    fun dismiss() {
        _state.value = State.Idle
    }

    companion object {
        private const val TAG = "Updater"

        const val REPO = "AbhishekS04/ame"

        /** Told apart from a network failure so the UI can offer the way out. */
        const val REASON_PERMISSION = "permission"

        private fun parseVersion(v: String): Triple<List<Int>, Int, Int> {
            val clean = v.trim().removePrefix("v")
            val base = clean.substringBefore('-')
            val extra = clean.substringAfter('-', "")
            val parts = base.split('.').map { it.toIntOrNull() ?: 0 }
            val (preType, preNum) = if (extra.isBlank()) {
                Pair(999, 0)
            } else {
                val num = Regex("\\d+").find(extra)?.value?.toIntOrNull() ?: 1
                val type = when {
                    extra.contains("alpha", ignoreCase = true) -> 1
                    extra.contains("beta", ignoreCase = true) -> 2
                    extra.contains("rc", ignoreCase = true) -> 3
                    else -> 2
                }
                Pair(type, num)
            }
            return Triple(parts, preType, preNum)
        }

        /**
         * Compares two SemVer versions numerically, supporting pre-release suffixes (e.g. -beta.1).
         */
        fun isNewer(candidate: String, installed: String): Boolean {
            val (cParts, cType, cNum) = parseVersion(candidate)
            val (iParts, iType, iNum) = parseVersion(installed)
            for (i in 0 until maxOf(cParts.size, iParts.size)) {
                val c = cParts.getOrElse(i) { 0 }
                val inst = iParts.getOrElse(i) { 0 }
                if (c != inst) return c > inst
            }
            if (cType != iType) return cType > iType
            return cNum > iNum
        }
    }
}
