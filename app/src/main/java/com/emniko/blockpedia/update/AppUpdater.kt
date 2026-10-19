package com.emniko.blockpedia.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.emniko.blockpedia.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A published GitHub release that carries an APK. */
data class Release(
    val tag: String,
    val versionCode: Long,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float?) : UpdateState
    data class ReadyToInstall(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Checks the GitHub releases of [BuildConfig.UPDATE_REPO] for a newer build, downloads its APK
 * and hands it to Android's installer. Android only accepts it if it's signed with the same key.
 */
class AppUpdater(context: Context, private val http: OkHttpClient) {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Long = runCatching {
        PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
    }.getOrDefault(BuildConfig.VERSION_CODE.toLong())

    private val updatesDir: File get() = File(app.cacheDir, "updates")

    fun check() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = try {
                val release = latestRelease()
                when {
                    release == null -> UpdateState.Failed("No updates have been published yet.")
                    release.versionCode > versionCode -> UpdateState.Available(release)
                    else -> UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't check for updates. Check your connection.")
            }
        }
    }

    fun download(release: Release) {
        if (job?.isActive == true) return
        _state.value = UpdateState.Downloading(release, null)
        job = scope.launch {
            _state.value = try {
                val file = fetchApk(release)
                verify(file, release)
                UpdateState.ReadyToInstall(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message?.takeIf { e is UpdateException } ?: "Download failed. Try again.", release)
            }
        }
    }

    /**
     * Opens the system installer for the downloaded APK. If Blockpedia isn't yet allowed to
     * install apps, opens that setting instead; the user comes back and taps Install again.
     */
    fun install(context: Context, file: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun latestRelease(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
            val tag = json.optString("tag_name")
            val code = Regex("(\\d+)$").find(tag)?.value?.toLongOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (!asset.optString("name").endsWith(".apk")) continue
                return Release(
                    tag = tag,
                    versionCode = code,
                    title = json.optString("name").ifBlank { tag },
                    notes = json.optString("body").trim(),
                    apkUrl = asset.getString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                )
            }
            return null
        }
    }

    private fun fetchApk(release: Release): File {
        val dir = updatesDir.apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the newest download
        val target = File(dir, "blockpedia-${release.versionCode}.apk")
        val partial = File(dir, target.name + ".part")
        val request = Request.Builder().url(release.apkUrl).cacheControl(CacheControl.FORCE_NETWORK).build()
        http.newBuilder().cache(null).build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes.takeIf { it > 0 }
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total != null) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceAtMost(1f))
                            }
                        }
                    }
                }
            }
        }
        if (!partial.renameTo(target)) throw IOException("Couldn't save the update")
        return target
    }

    /** Refuses files that aren't a newer Blockpedia build. */
    private fun verify(file: File, release: Release) {
        val info = app.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw UpdateException("The downloaded file isn't a valid app.")
        if (info.packageName != app.packageName) throw UpdateException("The download isn't a Blockpedia build.")
        val code = PackageInfoCompat.getLongVersionCode(info)
        if (code <= versionCode || code != release.versionCode) {
            throw UpdateException("The download doesn't match the release version.")
        }
    }

    private class UpdateException(message: String) : Exception(message)
}
