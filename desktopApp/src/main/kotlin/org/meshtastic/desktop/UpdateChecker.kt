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
package org.meshtastic.desktop

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.meshtastic.core.common.util.safeCatching

/**
 * Checks the apk-plus137 GitHub Releases for a newer published Mesh+ 137 desktop build.
 *
 * The `releases/latest` endpoint only ever returns a published, non-draft, non-prerelease release. Tags look like
 * `v2.8.5-137.1`: the upstream base version, then the apk-plus137 build number.
 */
class UpdateChecker(private val httpClient: HttpClient) {

    /** A published release newer than the running build. */
    data class UpdateInfo(val versionName: String, val releaseUrl: String)

    /** Returns the latest published release when it is newer than [currentVersionName], null otherwise. */
    suspend fun check(currentVersionName: String): UpdateInfo? = safeCatching {
        val release = Json.parseToJsonElement(httpClient.get(LATEST_RELEASE_URL).bodyAsText()).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.content
        if (tag != null && isNewer(latest = tag, current = currentVersionName)) {
            UpdateInfo(
                versionName = tag.removePrefix("v"),
                releaseUrl = release["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE_URL,
            )
        } else {
            null
        }
    }
        .getOrElse { e ->
            Logger.d(e) { "Update check skipped: offline, rate-limited, or malformed response" }
            null
        }

    companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/And137/apk-plus137/releases/latest"
        private const val RELEASES_PAGE_URL = "https://github.com/And137/apk-plus137/releases"

        private val VERSION_REGEX = Regex("""(\d+)\.(\d+)\.(\d+)(?:-137\.(\d+))?""")

        /** The running build in release-tag form, `X.Y.Z-137.N`, so a rebuild on the same base still compares. */
        fun plusVersion(versionName: String, plusBuild: Int): String =
            if (plusBuild > 0) "$versionName-137.$plusBuild" else versionName

        /**
         * True when [latest] carries a strictly newer X.Y.Z, or the same X.Y.Z with a higher `-137.N` build, than
         * [current]. Other suffixes (`v`, `-internal.N`) are ignored; versions that don't parse never trigger an
         * update.
         */
        internal fun isNewer(latest: String, current: String): Boolean {
            val latestVersion = parse(latest)
            val currentVersion = parse(current)
            if (latestVersion == null || currentVersion == null) return false
            // Both lists hold major, minor, patch and plus build: the first differing component decides.
            val firstDifference = latestVersion.zip(currentVersion).firstOrNull { (l, c) -> l != c }
            return firstDifference != null && firstDifference.first > firstDifference.second
        }

        private fun parse(raw: String): List<Int>? = VERSION_REGEX.find(raw)?.groupValues?.let { groups ->
            // runCatching: \d+ can match components too large for Int; treat those as unparseable.
            runCatching { groups.drop(1).map { if (it.isEmpty()) 0 else it.toInt() } }.getOrNull()
        }
    }
}
