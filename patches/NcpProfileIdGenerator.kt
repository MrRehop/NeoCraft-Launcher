package org.neocraft.neomod.core

import java.util.Locale

/**
 * Creates a stable profile ID from a display name without overwriting an existing profile.
 *
 * IDs are limited to the same 48-character contract enforced by NcpProfileSnapshotValidator.
 */
object NcpProfileIdGenerator {
    private const val MAX_PROFILE_ID_LENGTH = 48
    private val profileIdRegex = Regex("^[a-z][a-z0-9_-]{1,47}$")

    fun createUnique(displayName: String, existingIds: Set<String>): String {
        require(displayName.isNotBlank() && displayName.length <= 80) {
            "displayName must contain 1..80 characters"
        }

        val normalized = displayName.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val base = normalized.take(MAX_PROFILE_ID_LENGTH).trimEnd('-')
            .takeIf { it.length >= 2 && it.first() in 'a'..'z' }
            ?: "profile"

        var candidate = base
        var suffix = 2L
        while (candidate in existingIds) {
            val ending = "-$suffix"
            val prefixLength = MAX_PROFILE_ID_LENGTH - ending.length
            check(prefixLength >= 1) { "NeoMod profile ID namespace is exhausted" }
            candidate = base.take(prefixLength).trimEnd('-') + ending
            check(suffix < Long.MAX_VALUE) { "NeoMod profile ID suffix space is exhausted" }
            suffix++
        }

        check(profileIdRegex.matches(candidate)) {
            "Generated NeoMod profile ID violates the profile ID contract"
        }
        return candidate
    }
}
