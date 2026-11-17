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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlusAppVersionTest {
    @Test
    fun theRunningBuildIsInReleaseTagForm() {
        assertEquals("2.8.5-137.5", PlusAppVersion.current("2.8.5 (29123) fdroid", 5))
        assertEquals("2.8.5", PlusAppVersion.current("2.8.5", 0))
    }

    @Test
    fun aHigherPlusBuildOrBaseIsNewer() {
        assertTrue(PlusAppVersion.isNewer("v2.8.5-137.6", "2.8.5-137.5"))
        assertTrue(PlusAppVersion.isNewer("v2.8.6-137.1", "2.8.5-137.5"))
        assertFalse(PlusAppVersion.isNewer("v2.8.5-137.5", "2.8.5-137.5"))
        assertFalse(PlusAppVersion.isNewer("v2.8.5-137.4", "2.8.5-137.5"))
        assertFalse(PlusAppVersion.isNewer("nightly", "2.8.5-137.5"))
    }

    @Test
    fun shaSumsAreLookedUpByFileName() {
        val sums = "A82189DC  MeshPlus137-2.8.5-137.5-arm64-v8a.apk\nf7ac7fd5  other.rpm\n"
        assertEquals("a82189dc", PlusAppVersion.sha256For(sums, "MeshPlus137-2.8.5-137.5-arm64-v8a.apk"))
        assertNull(PlusAppVersion.sha256For(sums, "missing.apk"))
    }
}
