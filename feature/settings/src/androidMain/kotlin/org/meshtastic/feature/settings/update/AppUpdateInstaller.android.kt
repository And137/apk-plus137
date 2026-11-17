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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Composable
actual fun rememberAppUpdateInstaller(): AppUpdateInstaller? {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppUpdateInstaller(context) }
}

/**
 * Picks the APK for this phone's ABI (falling back to the universal one), downloads it into the cache and opens the
 * system package installer through a FileProvider URI. Android itself refuses an APK signed with another key.
 */
internal class AndroidAppUpdateInstaller(private val context: Context) : AppUpdateInstaller {

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
    ): InstallOutcome = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings =
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(settings) }
            return@withContext InstallOutcome.NEEDS_PERMISSION
        }

        val dir =
            File(context.cacheDir, UPDATES_DIR).apply {
                deleteRecursively()
                mkdirs()
            }
        val file = File(dir, fileName.substringAfterLast('/'))
        val digest = download(url, file, onProgress)
        if (sha256 != null && !digest.equals(sha256, ignoreCase = true)) {
            file.delete()
            return@withContext InstallOutcome.CHECKSUM_MISMATCH
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        val install =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) { context.startActivity(install) }
        InstallOutcome.STARTED
    }

    /** Streams [url] into [file] (GitHub redirects to its file host) and returns the SHA-256 of what was written. */
    private fun download(url: String, file: File, onProgress: (Float) -> Unit): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    copy(input, output, digest) { written -> report(written, total, onProgress) }
                }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun report(written: Long, total: Long, onProgress: (Float) -> Unit) {
        if (total > 0) onProgress(written.toFloat() / total)
    }

    /** Copies [input] to [output], feeding [digest] and reporting the bytes written so far. */
    private fun copy(input: InputStream, output: OutputStream, digest: MessageDigest, onWritten: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        var written = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            written += read
            onWritten(written)
            read = input.read(buffer)
        }
    }

    private companion object {
        const val UPDATES_DIR = "updates"
        const val AUTHORITY_SUFFIX = ".updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
    }
}
