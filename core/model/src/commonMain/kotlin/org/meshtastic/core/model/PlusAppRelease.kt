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
package org.meshtastic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** apk-plus137: a GitHub release of And137/apk-plus137 (only the fields the update check uses). */
@Serializable
data class NetworkPlusAppRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("assets") val assets: List<NetworkReleaseAsset> = emptyList(),
)

/** One downloadable file of a GitHub release. */
@Serializable
data class NetworkReleaseAsset(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    @SerialName("size") val size: Long = 0,
)

/** Mesh+ 137 release versions: the upstream base, then the apk-plus137 build ("v2.8.5-137.6"). */
object PlusAppVersion {
    private const val BASE_PARTS = 3
    private val VERSION = Regex("""(\d+)\.(\d+)\.(\d+)(?:-137\.(\d+))?""")

    /** The running build in release-tag form, "X.Y.Z-137.N", from a version name such as "2.8.5 (29123) fdroid". */
    fun current(versionName: String, plusBuild: Int): String {
        val base =
            VERSION.find(versionName)?.let { m -> m.groupValues.drop(1).take(BASE_PARTS).joinToString(".") }
                ?: versionName
        return if (plusBuild > 0) "$base-137.$plusBuild" else base
    }

    /**
     * True when [latest] carries a strictly newer X.Y.Z, or the same X.Y.Z with a higher "-137.N" build, than
     * [current]. Versions that do not parse never count as newer.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        // Both lists hold major, minor, patch and plus build: the first differing component decides.
        val difference = if (l != null && c != null) l.zip(c).firstOrNull { (a, b) -> a != b } else null
        return difference != null && difference.first > difference.second
    }

    private fun parse(raw: String): List<Int>? = VERSION.find(raw)?.groupValues?.drop(1)?.let { groups ->
        // \d+ can match components too large for Int: treat those as unparseable.
        groups.map { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
    }

    /** The SHA-256 listed for [fileName] in a SHA256SUMS.txt ("<hex> <name>" lines), or null when absent. */
    fun sha256For(sums: String, fileName: String): String? = sums
        .lineSequence()
        .map { it.trim().split(Regex("""\s+\*?"""), limit = 2) }
        .firstOrNull { it.size == 2 && it[1] == fileName }
        ?.first()
        ?.lowercase()
}
