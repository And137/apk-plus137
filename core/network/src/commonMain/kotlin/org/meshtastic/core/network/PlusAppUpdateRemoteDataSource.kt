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
package org.meshtastic.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NetworkPlusAppRelease

/** apk-plus137: the latest published Mesh+ 137 release on GitHub, for the update check on the About screen. */
@Single
class PlusAppUpdateRemoteDataSource(private val client: HttpClient, private val dispatchers: CoroutineDispatchers) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The latest published (non-draft, non-prerelease) release, or null when there is none; throws when offline. */
    suspend fun latestRelease(): NetworkPlusAppRelease? = withContext(dispatchers.io) {
        val response = client.get(HttpClientDefaults.PLUS_APP_LATEST_RELEASE_URL)
        when {
            response.status == HttpStatusCode.NotFound -> null

            response.status.isSuccess() ->
                json.decodeFromString<NetworkPlusAppRelease>(response.bodyAsText()).takeUnless { it.draft }

            else -> error("Unexpected HTTP ${response.status} fetching the Mesh+ 137 release")
        }
    }

    /** The text of a small release file (SHA256SUMS.txt), or null when it cannot be read. */
    suspend fun text(url: String): String? = withContext(dispatchers.io) {
        safeCatching { client.get(url).takeIf { it.status.isSuccess() }?.bodyAsText() }.getOrNull()
    }
}
