package org.neocraft.neomod.core

import java.io.File

private fun profileManifest(id: String, version: String, deps: List<NcpDependency> = emptyList()) =
    NcpManifest(id, id, "test", version, ">=1.0.0 <2.0.0", emptySet(), deps, emptyList(), emptySet(), emptyList(), emptyList(), emptyMap())

private fun profileInstalled(m: NcpManifest) = NcpPackageStore.InstalledPackage(m.id, m.version, m.name, File("store/${m.id}/${m.version}"), m, emptyList())

private fun profilePlan(): NcpInstalledPackagePlan {
    val root = profileManifest("root", "1.0.0", listOf(NcpDependency("library", ">=1.0.0")))
    val library = profileManifest("library", "1.2.0")
    return NcpInstalledPackagePlanResolver.resolve(
        listOf(profileInstalled(root), profileInstalled(library)),
        listOf(NcpDependency("root", "1.0.0")),
        "1.0.0",
    )
}

private fun validSnapshot(plan: NcpInstalledPackagePlan) = NcpProfileSnapshot(
    profileId = "survival",
    displayName = "Survival",
    gameBuild = NcpGameBuildIdentity("com.mojang.minecraftpe", 972601301L, "a".repeat(64)),
    neoModApiVersion = "1.0.0",
    requestedPackages = listOf(NcpDependency("root", "1.0.0")),
    resolvedPackages = plan.selectedPackages.mapIndexed { i, p -> NcpProfilePackage(p.id, p.version, i) },
)

private fun expectProfileError(code: String, block: () -> Unit) {
    val error = runCatching(block).exceptionOrNull()
    check(error is NcpValidationException && error.code == code) {
        "expected $code, got ${error?.javaClass?.simpleName}: ${error?.message}"
    }
}

fun main() {
    val tests = listOf(
        "valid immutable profile snapshot accepted" to {
            val plan = profilePlan()
            check(NcpProfileSnapshotValidator.validate(validSnapshot(plan), plan) == validSnapshot(plan))
        },
        "invalid profile identifier rejected" to {
            val plan = profilePlan()
            expectProfileError("NCP_PROFILE_INVALID") { NcpProfileSnapshotValidator.validate(validSnapshot(plan).copy(profileId = "../bad"), plan) }
        },
        "blank display name rejected" to {
            val plan = profilePlan()
            expectProfileError("NCP_PROFILE_INVALID") { NcpProfileSnapshotValidator.validate(validSnapshot(plan).copy(displayName = " "), plan) }
        },
        "wrong game package rejected" to {
            val plan = profilePlan()
            expectProfileError("NCP_PROFILE_INVALID") { NcpProfileSnapshotValidator.validate(validSnapshot(plan).copy(gameBuild = validSnapshot(plan).gameBuild.copy(packageName = "other")), plan) }
        },
        "invalid APK fingerprint rejected" to {
            val plan = profilePlan()
            expectProfileError("NCP_PROFILE_INVALID") { NcpProfileSnapshotValidator.validate(validSnapshot(plan).copy(gameBuild = validSnapshot(plan).gameBuild.copy(apkSha256 = "bad")), plan) }
        },
        "profile cannot omit resolved dependency" to {
            val plan = profilePlan()
            expectProfileError("NCP_PROFILE_PLAN_MISMATCH") { NcpProfileSnapshotValidator.validate(validSnapshot(plan).copy(resolvedPackages = listOf(NcpProfilePackage("root", "1.0.0", 0))), plan) }
        },
        "profile order must match dependency-first plan" to {
            val plan = profilePlan()
            val reversed = validSnapshot(plan).copy(resolvedPackages = listOf(NcpProfilePackage("root", "1.0.0", 0), NcpProfilePackage("library", "1.2.0", 1)))
            expectProfileError("NCP_PROFILE_PLAN_MISMATCH") { NcpProfileSnapshotValidator.validate(reversed, plan) }
        },
        "duplicate requested package IDs rejected" to {
            val plan = profilePlan()
            expectProfileError("NCP_PROFILE_INVALID") { NcpProfileSnapshotValidator.validate(validSnapshot(plan).copy(requestedPackages = listOf(NcpDependency("root", "1.0.0"), NcpDependency("root", "1.0.0"))), plan) }
        },
        "profile accepts the exact currently installed game build" to {
            val plan = profilePlan()
            val snapshot = validSnapshot(plan)
            check(NcpProfileGameBuildMatcher.validate(snapshot, snapshot.gameBuild) == snapshot)
        },
        "profile rejects a different Android package" to {
            val plan = profilePlan()
            val snapshot = validSnapshot(plan)
            val otherBuild = snapshot.gameBuild.copy(packageName = "com.example.othergame")
            expectProfileError("NCP_PROFILE_GAME_PACKAGE_MISMATCH") {
                NcpProfileGameBuildMatcher.validate(snapshot, otherBuild)
            }
        },
        "profile rejects a different Minecraft versionCode" to {
            val plan = profilePlan()
            val snapshot = validSnapshot(plan)
            val otherBuild = snapshot.gameBuild.copy(versionCode = snapshot.gameBuild.versionCode + 1)
            expectProfileError("NCP_PROFILE_GAME_VERSION_MISMATCH") {
                NcpProfileGameBuildMatcher.validate(snapshot, otherBuild)
            }
        },
        "profile rejects a different APK fingerprint" to {
            val plan = profilePlan()
            val snapshot = validSnapshot(plan)
            val otherBuild = snapshot.gameBuild.copy(apkSha256 = "b".repeat(64))
            expectProfileError("NCP_PROFILE_GAME_FINGERPRINT_MISMATCH") {
                NcpProfileGameBuildMatcher.validate(snapshot, otherBuild)
            }
        },
        "profile matcher rejects malformed current build identity" to {
            val plan = profilePlan()
            val snapshot = validSnapshot(plan)
            val invalidBuild = snapshot.gameBuild.copy(apkSha256 = "not-a-sha256")
            expectProfileError("NCP_GAME_BUILD_IDENTITY_INVALID") {
                NcpProfileGameBuildMatcher.validate(snapshot, invalidBuild)
            }
        },
    )
    tests.forEach { (name, test) -> test(); println("PASS: " + name) }
    println("NCP PROFILE SNAPSHOT RESULT: ${tests.size}/${tests.size} tests passed")
}
