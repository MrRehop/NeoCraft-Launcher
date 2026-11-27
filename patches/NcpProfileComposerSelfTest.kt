package org.neocraft.neomod.core

import java.io.File

private fun composerManifest(
    id: String,
    version: String,
    dependencies: List<NcpDependency> = emptyList(),
    optionalDependencies: List<NcpDependency> = emptyList(),
) = NcpManifest(
    id = id,
    name = id,
    publisher = "composer-self-test",
    version = version,
    apiVersionRange = ">=1.0.0 <2.0.0",
    capabilities = emptySet(),
    dependencies = dependencies,
    optionalDependencies = optionalDependencies,
    conflicts = emptySet(),
    content = emptyList(),
    files = emptyList(),
    extensions = emptyMap(),
)

private fun composerInstalled(manifest: NcpManifest) = NcpPackageStore.InstalledPackage(
    id = manifest.id,
    version = manifest.version,
    name = manifest.name,
    directory = File("verified-store/${manifest.id}/${manifest.version}"),
    manifest = manifest,
    files = emptyList(),
)

private fun composerDraft(
    requests: List<NcpDependency> = listOf(NcpDependency("root", "1.0.0")),
) = NcpProfileDraft(
    profileId = "survival",
    displayName = "Survival",
    gameBuild = NcpGameBuildIdentity(
        "com.mojang.minecraftpe",
        972601301L,
        "a".repeat(64),
    ),
    neoModApiVersion = "1.0.0",
    requestedPackages = requests,
)

private fun expectComposerError(code: String, action: () -> Unit) {
    val error = runCatching(action).exceptionOrNull()
    check(error is NcpValidationException && error.code == code) {
        "expected $code, got ${error?.javaClass?.simpleName}: ${error?.message}"
    }
}

fun main() {
    val libraryOld = composerManifest("library", "1.2.0")
    val libraryNew = composerManifest("library", "1.5.0")
    val root = composerManifest(
        "root",
        "1.0.0",
        dependencies = listOf(NcpDependency("library", ">=1.0.0 <2.0.0")),
    )
    val installed = listOf(
        composerInstalled(root),
        composerInstalled(libraryOld),
        composerInstalled(libraryNew),
    )

    val tests = listOf(
        "composition stores the resolver's dependency-first selected versions" to {
            val result = NcpProfileComposer.compose(composerDraft(), installed)
            check(result.snapshot.profileId == "survival")
            check(result.snapshot.requestedPackages == listOf(NcpDependency("root", "1.0.0")))
            check(result.snapshot.resolvedPackages == listOf(
                NcpProfilePackage("library", "1.5.0", 0),
                NcpProfilePackage("root", "1.0.0", 1),
            ))
            check(result.plan.selectedPackages.map { it.version } == listOf("1.5.0", "1.0.0"))
            check(NcpProfileSnapshotValidator.validate(result.snapshot, result.plan) == result.snapshot)
        },
        "composition preserves game fingerprint and API version" to {
            val draft = composerDraft()
            val result = NcpProfileComposer.compose(draft, installed)
            check(result.snapshot.gameBuild == draft.gameBuild)
            check(result.snapshot.neoModApiVersion == draft.neoModApiVersion)
        },
        "missing requested package is rejected" to {
            expectComposerError("NCP_DEPENDENCY_UNSATISFIED") {
                NcpProfileComposer.compose(
                    composerDraft(listOf(NcpDependency("missing", "1.0.0"))),
                    installed,
                )
            }
        },
        "missing transitive dependency is rejected" to {
            val brokenRoot = composerManifest(
                "root",
                "1.0.0",
                dependencies = listOf(NcpDependency("missing", ">=1.0.0")),
            )
            expectComposerError("NCP_DEPENDENCY_UNSATISFIED") {
                NcpProfileComposer.compose(composerDraft(), listOf(composerInstalled(brokenRoot)))
            }
        },
        "duplicate requested package IDs are rejected by profile validation" to {
            expectComposerError("NCP_PROFILE_INVALID") {
                NcpProfileComposer.compose(
                    composerDraft(listOf(
                        NcpDependency("root", "1.0.0"),
                        NcpDependency("root", ">=1.0.0 <2.0.0"),
                    )),
                    installed,
                )
            }
        },
        "invalid profile identity is rejected" to {
            expectComposerError("NCP_PROFILE_INVALID") {
                NcpProfileComposer.compose(composerDraft().copy(profileId = "../outside"), installed)
            }
        },
        "invalid target game fingerprint is rejected" to {
            expectComposerError("NCP_PROFILE_INVALID") {
                NcpProfileComposer.compose(
                    composerDraft().copy(gameBuild = composerDraft().gameBuild.copy(apkSha256 = "bad")),
                    installed,
                )
            }
        },
        "optional dependencies remain notices and are not auto-selected" to {
            val optionalRoot = composerManifest(
                "root",
                "1.0.0",
                optionalDependencies = listOf(NcpDependency("extra", ">=1.0.0")),
            )
            val result = NcpProfileComposer.compose(
                composerDraft(),
                listOf(composerInstalled(optionalRoot), composerInstalled(composerManifest("extra", "1.0.0"))),
            )
            check(result.snapshot.resolvedPackages.map { it.id } == listOf("root"))
            check(result.plan.resolution.optionalDependencyNotices.single().dependencyId == "extra")
        },
    )

    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
    }
    println("NCP PROFILE COMPOSER RESULT: " + tests.size + "/" + tests.size + " tests passed")
}
