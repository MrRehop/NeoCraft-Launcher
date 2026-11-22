package org.neocraft.neomod.core

/**
 * Immutable, validated snapshot of a future NeoMod profile.
 *
 * This is metadata only: it does not persist, activate, or load packages.
 */
data class NcpProfilePackage(
    val id: String,
    val version: String,
    val order: Int,
)

data class NcpGameBuildIdentity(
    val packageName: String,
    val versionCode: Long,
    val apkSha256: String,
)

data class NcpProfileSnapshot(
    val profileId: String,
    val displayName: String,
    val gameBuild: NcpGameBuildIdentity,
    val neoModApiVersion: String,
    val requestedPackages: List<NcpDependency>,
    val resolvedPackages: List<NcpProfilePackage>,
)

object NcpProfileSnapshotValidator {
    private val profileIdRegex = Regex("^[a-z][a-z0-9_-]{1,47}$")
    private val packageIdRegex = Regex("^[a-z][a-z0-9_]{1,47}$")
    private val sha256Regex = Regex("^[a-f0-9]{64}$")

    fun validate(
        snapshot: NcpProfileSnapshot,
        plan: NcpInstalledPackagePlan,
    ): NcpProfileSnapshot {
        if (!profileIdRegex.matches(snapshot.profileId)) {
            fail("NCP_PROFILE_INVALID", "profileId must be a stable lowercase identifier")
        }
        if (snapshot.displayName.isBlank() || snapshot.displayName.length > 80) {
            fail("NCP_PROFILE_INVALID", "displayName must contain 1..80 characters")
        }
        val build = snapshot.gameBuild
        if (build.packageName != "com.mojang.minecraftpe" || build.versionCode <= 0 ||
            !sha256Regex.matches(build.apkSha256)) {
            fail("NCP_PROFILE_INVALID", "game build identity is incomplete or invalid")
        }
        if (snapshot.requestedPackages.isEmpty() || snapshot.requestedPackages.size > 256) {
            fail("NCP_PROFILE_INVALID", "requested package count must be 1..256")
        }
        if (snapshot.requestedPackages.any { !packageIdRegex.matches(it.id) }) {
            fail("NCP_PROFILE_INVALID", "requested package ID is invalid")
        }
        if (snapshot.requestedPackages.map { it.id }.distinct().size != snapshot.requestedPackages.size) {
            fail("NCP_PROFILE_INVALID", "requested package IDs must be unique")
        }

        val expected = plan.selectedPackages.mapIndexed { index, installed ->
            NcpProfilePackage(installed.id, installed.version, index)
        }
        if (snapshot.resolvedPackages != expected) {
            fail("NCP_PROFILE_PLAN_MISMATCH", "profile resolved package list does not match the validated plan")
        }
        if (snapshot.resolvedPackages.map { it.id }.distinct().size != snapshot.resolvedPackages.size) {
            fail("NCP_PROFILE_INVALID", "resolved package IDs must be unique")
        }
        if (snapshot.resolvedPackages.map { it.order } != snapshot.resolvedPackages.indices.toList()) {
            fail("NCP_PROFILE_INVALID", "resolved package order must be contiguous and dependency-first")
        }
        return snapshot
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)
}
