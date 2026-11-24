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

    /** Validates fields without requiring a package-store resolution plan. */
    fun validateStructure(snapshot: NcpProfileSnapshot): NcpProfileSnapshot {
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
        NcpVersion.parse(snapshot.neoModApiVersion)
        if (snapshot.requestedPackages.isEmpty() || snapshot.requestedPackages.size > 256) {
            fail("NCP_PROFILE_INVALID", "requested package count must be 1..256")
        }
        if (snapshot.requestedPackages.any { !packageIdRegex.matches(it.id) }) {
            fail("NCP_PROFILE_INVALID", "requested package ID is invalid")
        }
        snapshot.requestedPackages.forEach { NcpVersionRange.parse(it.versionRange) }
        if (snapshot.requestedPackages.map { it.id }.distinct().size != snapshot.requestedPackages.size) {
            fail("NCP_PROFILE_INVALID", "requested package IDs must be unique")
        }
        if (snapshot.resolvedPackages.isEmpty() || snapshot.resolvedPackages.size > 256) {
            fail("NCP_PROFILE_INVALID", "resolved package count must be 1..256")
        }
        if (snapshot.resolvedPackages.any { !packageIdRegex.matches(it.id) }) {
            fail("NCP_PROFILE_INVALID", "resolved package ID is invalid")
        }
        snapshot.resolvedPackages.forEach { NcpVersion.parse(it.version) }
        if (snapshot.resolvedPackages.map { it.id }.distinct().size != snapshot.resolvedPackages.size) {
            fail("NCP_PROFILE_INVALID", "resolved package IDs must be unique")
        }
        if (snapshot.resolvedPackages.map { it.order } != snapshot.resolvedPackages.indices.toList()) {
            fail("NCP_PROFILE_INVALID", "resolved package order must be contiguous and dependency-first")
        }
        return snapshot
    }

    fun validate(
        snapshot: NcpProfileSnapshot,
        plan: NcpInstalledPackagePlan,
    ): NcpProfileSnapshot {
        validateStructure(snapshot)
        val expected = plan.selectedPackages.mapIndexed { index, installed ->
            NcpProfilePackage(installed.id, installed.version, index)
        }
        if (snapshot.resolvedPackages != expected) {
            fail("NCP_PROFILE_PLAN_MISMATCH", "profile resolved package list does not match the validated plan")
        }
        return snapshot
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)
}

/**
 * Checks a saved profile against the game build currently installed on the device.
 *
 * This is deliberately separate from snapshot and package-plan validation:
 * a valid profile and a valid package plan do not prove that the profile targets
 * the game build currently installed. Call this before offering profile activation.
 */
object NcpProfileGameBuildMatcher {
    private val sha256Regex = Regex("^[a-f0-9]{64}$")

    fun validate(
        snapshot: NcpProfileSnapshot,
        currentBuild: NcpGameBuildIdentity,
    ): NcpProfileSnapshot {
        NcpProfileSnapshotValidator.validateStructure(snapshot)
        if (currentBuild.packageName != "com.mojang.minecraftpe" ||
            currentBuild.versionCode <= 0 ||
            !sha256Regex.matches(currentBuild.apkSha256)
        ) {
            fail("NCP_GAME_BUILD_IDENTITY_INVALID", "current Minecraft build identity is incomplete or invalid")
        }
        if (snapshot.gameBuild.packageName != currentBuild.packageName) {
            fail("NCP_PROFILE_GAME_PACKAGE_MISMATCH", "profile targets a different Android package")
        }
        if (snapshot.gameBuild.versionCode != currentBuild.versionCode) {
            fail("NCP_PROFILE_GAME_VERSION_MISMATCH", "profile targets a different Minecraft versionCode")
        }
        if (snapshot.gameBuild.apkSha256 != currentBuild.apkSha256) {
            fail("NCP_PROFILE_GAME_FINGERPRINT_MISMATCH", "profile targets a different Minecraft APK fingerprint")
        }
        return snapshot
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)
}
