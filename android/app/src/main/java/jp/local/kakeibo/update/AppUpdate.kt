package jp.local.kakeibo.update

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import jp.local.kakeibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/ASDFFRGH/kakeibo/releases/latest"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val LOG_TAG = "KakeiboUpdate"

internal data class AppUpdate(
    val version: String,
    val downloadUrl: String,
)

private object AppUpdateChecker {
    suspend fun findUpdate(currentVersion: String): AppUpdate? =
        withContext(Dispatchers.IO) {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "kakeibo-android/${BuildConfig.VERSION_NAME}")
            }

            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("GitHub Releases returned HTTP ${connection.responseCode}")
                }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = release.getString("tag_name")
                if (!isVersionNewer(tag, currentVersion)) return@withContext null

                val expectedAssetName = "my-kakeibo-$tag.apk"
                val assets = release.getJSONArray("assets")
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    if (asset.optString("name") == expectedAssetName) {
                        val downloadUrl = asset.optString("browser_download_url")
                        if (downloadUrl.startsWith("https://github.com/")) {
                            return@withContext AppUpdate(
                                version = tag.removePrefix("v"),
                                downloadUrl = downloadUrl,
                            )
                        }
                    }
                }
                throw IllegalStateException("Release asset $expectedAssetName was not found")
            } finally {
                connection.disconnect()
            }
        }
}

private data class PendingDownload(
    val id: Long,
    val version: String,
)

private sealed interface DownloadState {
    data object InProgress : DownloadState
    data class Ready(val uri: Uri) : DownloadState
    data object Failed : DownloadState
}

private class AppUpdateDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun enqueue(update: AppUpdate): PendingDownload {
        val safeVersion = update.version.replace(Regex("[^0-9.]"), "")
        val fileName = "my-kakeibo-v$safeVersion-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("My 家計簿 ${update.version}")
            .setDescription("更新をダウンロードしています")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)

        val pending = PendingDownload(manager.enqueue(request), update.version)
        preferences.edit()
            .putLong(KEY_DOWNLOAD_ID, pending.id)
            .putString(KEY_VERSION, pending.version)
            .apply()
        return pending
    }

    fun restore(): PendingDownload? {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        val version = preferences.getString(KEY_VERSION, null)
        return if (id >= 0 && !version.isNullOrBlank()) PendingDownload(id, version) else null
    }

    fun state(id: Long): DownloadState {
        val query = DownloadManager.Query().setFilterById(id)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadState.Failed
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    manager.getUriForDownloadedFile(id)?.let(DownloadState::Ready)
                        ?: DownloadState.Failed
                }
                DownloadManager.STATUS_FAILED -> DownloadState.Failed
                else -> DownloadState.InProgress
            }
        }
    }

    fun clear(removeDownload: Boolean) {
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        preferences.edit().clear().apply()
        if (removeDownload && id >= 0) manager.remove(id)
    }

    private companion object {
        const val PREFERENCES_NAME = "app_update"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_VERSION = "version"
    }
}

@Composable
fun AppUpdateCoordinator(content: @Composable () -> Unit) {
    content()

    val context = LocalContext.current
    val downloader = remember { AppUpdateDownloader(context) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var downloadedUri by remember { mutableStateOf<Uri?>(null) }

    fun openInstaller(uri: Uri) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME_TYPE)
                    clipData = ClipData.newRawUri("My 家計簿 update", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Could not open the package installer", error)
            Toast.makeText(context, "インストーラーを開けませんでした", Toast.LENGTH_LONG).show()
        }
    }

    val unknownAppsSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = downloadedUri
            if (uri != null && context.packageManager.canRequestPackageInstalls()) {
                openInstaller(uri)
            } else {
                Toast.makeText(
                    context,
                    "このアプリからのインストールを許可してください",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    fun requestInstallation(uri: Uri) {
        if (context.packageManager.canRequestPackageInstalls()) {
            openInstaller(uri)
        } else {
            unknownAppsSettingsLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        val restored = downloader.restore()
        if (restored != null && isVersionNewer(restored.version, BuildConfig.VERSION_NAME)) {
            pendingDownload = restored
        } else {
            if (restored != null) downloader.clear(removeDownload = true)
            availableUpdate =
                try {
                    AppUpdateChecker.findUpdate(BuildConfig.VERSION_NAME)
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "Update check failed", error)
                    null
                }
        }
    }

    LaunchedEffect(pendingDownload?.id) {
        val pending = pendingDownload ?: return@LaunchedEffect
        while (true) {
            when (val state = downloader.state(pending.id)) {
                DownloadState.InProgress -> delay(1_000)
                is DownloadState.Ready -> {
                    downloadedUri = state.uri
                    break
                }
                DownloadState.Failed -> {
                    downloader.clear(removeDownload = true)
                    pendingDownload = null
                    Toast.makeText(
                        context,
                        "更新のダウンロードに失敗しました。次回起動時に再試行します",
                        Toast.LENGTH_LONG,
                    ).show()
                    break
                }
            }
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("新しいバージョンがあります") },
            text = {
                Text("My 家計簿 ${update.version} に更新できます。更新ファイルをダウンロードしますか？")
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) { Text("あとで") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            pendingDownload = downloader.enqueue(update)
                            availableUpdate = null
                            Toast.makeText(
                                context,
                                "更新のダウンロードを開始しました",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } catch (error: Exception) {
                            Log.e(LOG_TAG, "Could not enqueue update", error)
                            Toast.makeText(
                                context,
                                "更新のダウンロードを開始できませんでした",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) { Text("更新") }
            },
        )
    }

    downloadedUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { downloadedUri = null },
            title = { Text("更新の準備ができました") },
            text = { Text("Android の確認画面を開いて、新しいバージョンをインストールします。") },
            dismissButton = {
                TextButton(onClick = { downloadedUri = null }) { Text("あとで") }
            },
            confirmButton = {
                TextButton(onClick = { requestInstallation(uri) }) { Text("インストール") }
            },
        )
    }
}
