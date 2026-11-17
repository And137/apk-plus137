/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.settings.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16

@Composable
actual fun rememberAppUpdateInstaller(): AppUpdateInstaller? {
    val context = LocalContext.current.applicationContext
    val dispatchers = koinInject<CoroutineDispatchers>()
    return remember(context, dispatchers) { AndroidAppUpdateInstaller(context, dispatchers) }
}

/**
 * Picks the APK for this phone's ABI (falling back to the universal one), downloads it and opens the system package
 * installer through a FileProvider URI. Android itself refuses an APK signed with another key.
 *
 * The download runs in the system DownloadManager, not in the app: it keeps going while the screen is locked or the app
 * is in the background, resumes after a dropped connection, and a second tap on "update" attaches to the download
 * already running (or already finished) for the same file instead of starting over.
 */
internal class AndroidAppUpdateInstaller(private val context: Context, private val dispatchers: CoroutineDispatchers) :
    AppUpdateInstaller {

    private val downloads: DownloadManager = context.getSystemService(DownloadManager::class.java)

    override fun pickAsset(assetNames: List<String>): String? {
        val apks = assetNames.filter { it.endsWith(".apk") }
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.endsWith("-$abi.apk") } }
            ?: apks.firstOrNull { it.endsWith("-universal.apk") }
    }

    override suspend fun downloadAndInstall(
        url: String,
        fileName: String,
        sha256: String?,
        onProgress: (Float) -> Unit,
    ): InstallOutcome = withContext(dispatchers.io) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(dispatchers.main) { context.startActivity(settings) }
            return@withContext InstallOutcome.NEEDS_PERMISSION
        }

        val name = fileName.substringAfterLast('/')
        val file = File(context.getExternalFilesDir(null), "$UPDATES_DIR/$name")
        val id = existingDownload(url, file) ?: enqueue(url, name, file)
        awaitDownload(id, onProgress)

        if (sha256 != null && !sha256Of(file).equals(sha256, ignoreCase = true)) {
            downloads.remove(id)
            file.delete()
            return@withContext InstallOutcome.CHECKSUM_MISMATCH
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        val install =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(dispatchers.main) { context.startActivity(install) }
        InstallOutcome.STARTED
    }

    /**
     * The id of this app's download of [url] into [file] that is still running or finished intact; every other update
     * download (an older release, a failed attempt) is removed together with its file.
     */
    private fun existingDownload(url: String, file: File): Long? {
        var keep: Long? = null
        downloads.query(DownloadManager.Query()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.long(DownloadManager.COLUMN_ID)
                val status = cursor.int(DownloadManager.COLUMN_STATUS)
                val usable =
                    keep == null &&
                        cursor.string(DownloadManager.COLUMN_URI) == url &&
                        status != DownloadManager.STATUS_FAILED &&
                        (status != DownloadManager.STATUS_SUCCESSFUL || file.exists())
                if (usable) keep = id else downloads.remove(id)
            }
        }
        return keep
    }

    private fun enqueue(url: String, name: String, file: File): Long {
        file.parentFile?.mkdirs()
        file.delete()
        val request =
            DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file))
        return downloads.enqueue(request)
    }

    /** Polls download [id] until it finishes, reporting progress; throws when it failed or was cancelled. */
    private suspend fun awaitDownload(id: Long, onProgress: (Float) -> Unit) {
        while (true) {
            val progress =
                downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (!cursor.moveToFirst()) throw IOException("update download $id was cancelled")
                    DownloadProgress(
                        status = cursor.int(DownloadManager.COLUMN_STATUS),
                        done = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                        total = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                }
            if (progress.total > 0) onProgress(progress.done.toFloat() / progress.total)
            when (progress.status) {
                DownloadManager.STATUS_SUCCESSFUL -> return

                DownloadManager.STATUS_FAILED -> {
                    downloads.remove(id)
                    throw IOException("update download $id failed")
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
    }

    private data class DownloadProgress(val status: Int, val done: Long, val total: Long)

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun Cursor.string(column: String): String? = getString(getColumnIndexOrThrow(column))

    private companion object {
        const val UPDATES_DIR = "updates"
        const val AUTHORITY_SUFFIX = ".updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val POLL_INTERVAL_MS = 500L
        const val BUFFER_SIZE = 64 * 1024
    }
}
