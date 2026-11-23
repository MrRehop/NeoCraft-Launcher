package org.neocraft.neomod.core

import java.io.File
import java.nio.file.Files

private fun storeManifest(id: String, version: String, dependencies: List<NcpDependency> = emptyList()) =
    NcpManifest(id, id, "test", version, ">=1.0.0 <2.0.0", emptySet(), dependencies, emptyList(), emptySet(), emptyList(), emptyList(), emptyMap())

private fun storeInstalled(manifest: NcpManifest) =
    NcpPackageStore.InstalledPackage(manifest.id, manifest.version, manifest.name, File("store/${manifest.id}/${manifest.version}"), manifest, emptyList())

private fun storePlan(rootVersion: String = "1.0.0"): NcpInstalledPackagePlan {
    val root = storeManifest("root", rootVersion, listOf(NcpDependency("library", ">=1.0.0 <2.0.0")))
    val library = storeManifest("library", "1.2.0")
    return NcpInstalledPackagePlanResolver.resolve(
        listOf(storeInstalled(root), storeInstalled(library)),
        listOf(NcpDependency("root", ">=1.0.0 <2.0.0")),
        "1.0.0",
    )
}

private fun storeSnapshot(plan: NcpInstalledPackagePlan, name: String = "Survival") = NcpProfileSnapshot(
    profileId = "survival",
    displayName = name,
    gameBuild = NcpGameBuildIdentity("com.mojang.minecraftpe", 972601301L, "a".repeat(64)),
    neoModApiVersion = "1.0.0",
    requestedPackages = listOf(NcpDependency("root", ">=1.0.0 <2.0.0")),
    resolvedPackages = plan.selectedPackages.mapIndexed { index, item -> NcpProfilePackage(item.id, item.version, index) },
)

private fun expectStoreError(code: String, block: () -> Unit) {
    val error = runCatching(block).exceptionOrNull()
    check(error is NcpValidationException && error.code == code) {
        "expected $code, got ${error?.javaClass?.simpleName}: ${error?.message}"
    }
}

fun main() {
    val tests = listOf(
        "save and load preserve a validated profile" to {
            val root = Files.createTempDirectory("ncp-profile-roundtrip-").toFile()
            try {
                val store = NcpProfileStore(root)
                val plan = storePlan()
                val snapshot = storeSnapshot(plan)
                check(store.save(snapshot, plan) == snapshot)
                check(store.load("survival", plan) == snapshot)
                check(store.listProfiles().single().profileId == "survival")
            } finally {
                root.deleteRecursively()
            }
        },
        "save atomically replaces an existing profile" to {
            val root = Files.createTempDirectory("ncp-profile-replace-").toFile()
            try {
                val store = NcpProfileStore(root)
                val plan = storePlan()
                store.save(storeSnapshot(plan), plan)
                val replacement = storeSnapshot(plan, "Survival Updated")
                store.save(replacement, plan)
                check(store.load("survival", plan) == replacement)
                check(store.listProfiles().single().displayName == "Survival Updated")
            } finally {
                root.deleteRecursively()
            }
        },
        "invalid snapshot is rejected before profile is written" to {
            val root = Files.createTempDirectory("ncp-profile-invalid-").toFile()
            try {
                val store = NcpProfileStore(root)
                val plan = storePlan()
                expectStoreError("NCP_PROFILE_INVALID") { store.save(storeSnapshot(plan).copy(profileId = "../escape"), plan) }
                check(store.listProfiles().isEmpty())
            } finally {
                root.deleteRecursively()
            }
        },
        "load rejects resolved package plan mismatch" to {
            val root = Files.createTempDirectory("ncp-profile-plan-").toFile()
            try {
                val store = NcpProfileStore(root)
                val plan = storePlan()
                store.save(storeSnapshot(plan), plan)
                val otherPlan = NcpInstalledPackagePlanResolver.resolve(
                    listOf(storeInstalled(storeManifest("root", "1.1.0", listOf(NcpDependency("library", ">=1.0.0 <2.0.0")))), storeInstalled(storeManifest("library", "1.2.0"))),
                    listOf(NcpDependency("root", ">=1.0.0 <2.0.0")),
                    "1.0.0",
                )
                expectStoreError("NCP_PROFILE_PLAN_MISMATCH") { store.load("survival", otherPlan) }
            } finally {
                root.deleteRecursively()
            }
        },
        "corrupted profile record is rejected" to {
            val root = Files.createTempDirectory("ncp-profile-corrupt-").toFile()
            try {
                val store = NcpProfileStore(root)
                val file = root.resolve("profiles/survival.ncp-profile")
                file.writeText("NEOCRAFT_PROFILE\t1\nid\t%%%\n")
                expectStoreError("NCP_PROFILE_STORE_INVALID") { store.load("survival", storePlan()) }
                expectStoreError("NCP_PROFILE_STORE_INVALID") { store.listProfiles() }
            } finally {
                root.deleteRecursively()
            }
        },
        "oversized profile is rejected" to {
            val root = Files.createTempDirectory("ncp-profile-large-").toFile()
            try {
                val store = NcpProfileStore(root)
                root.resolve("profiles/survival.ncp-profile").writeBytes(ByteArray(64 * 1024 + 1))
                expectStoreError("NCP_PROFILE_STORE_LIMIT") { store.load("survival", storePlan()) }
            } finally {
                root.deleteRecursively()
            }
        },
        "symbolic-link profile is rejected without reading target" to {
            val root = Files.createTempDirectory("ncp-profile-link-").toFile()
            val outside = Files.createTempFile("ncp-profile-outside-", ".txt")
            try {
                Files.writeString(outside, "outside")
                val store = NcpProfileStore(root)
                val link = root.resolve("profiles/survival.ncp-profile")
                Files.createSymbolicLink(link.toPath(), outside)
                expectStoreError("NCP_PROFILE_STORE_INVALID") { store.load("survival", storePlan()) }
                expectStoreError("NCP_PROFILE_STORE_INVALID") { store.listProfiles() }
                check(Files.readString(outside) == "outside")
            } finally {
                root.deleteRecursively()
                Files.deleteIfExists(outside)
            }
        },
        "delete removes only the selected profile" to {
            val root = Files.createTempDirectory("ncp-profile-delete-").toFile()
            try {
                val store = NcpProfileStore(root)
                val plan = storePlan()
                store.save(storeSnapshot(plan), plan)
                val second = storeSnapshot(plan).copy(profileId = "creative", displayName = "Creative")
                store.save(second, plan)
                store.delete("survival")
                check(store.listProfiles().map { it.profileId } == listOf("creative"))
                expectStoreError("NCP_PROFILE_NOT_FOUND") { store.load("survival", plan) }
            } finally {
                root.deleteRecursively()
            }
        },
        "unsafe profile ID is rejected" to {
            val root = Files.createTempDirectory("ncp-profile-path-").toFile()
            try {
                val store = NcpProfileStore(root)
                expectStoreError("NCP_PROFILE_INVALID") { store.load("../survival", storePlan()) }
                expectStoreError("NCP_PROFILE_INVALID") { store.delete("../survival") }
            } finally {
                root.deleteRecursively()
            }
        },
    )
    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
    }
    println("NCP PROFILE STORE RESULT: ${tests.size}/${tests.size} tests passed")
}
