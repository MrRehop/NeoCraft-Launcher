package org.neocraft.neomod.core

import java.io.File

private fun planManifest(
    id: String,
    version: String,
    dependencies: List<NcpDependency> = emptyList(),
    optionalDependencies: List<NcpDependency> = emptyList(),
): NcpManifest = NcpManifest(
    id = id,
    name = id,
    publisher = "self-test",
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

private fun installed(manifest: NcpManifest) = NcpPackageStore.InstalledPackage(
    id = manifest.id,
    version = manifest.version,
    name = manifest.name,
    directory = File("test-store/" + manifest.id + "/" + manifest.version),
    manifest = manifest,
    files = emptyList(),
)

private fun expectPlanError(code: String, action: () -> Unit) {
    val error = runCatching(action).exceptionOrNull()
    check(error is NcpValidationException && error.code == code) {
        "expected " + code + ", got " + (error?.javaClass?.simpleName + ": " + error?.message)
    }
}

fun main() {
    val tests = listOf(
        "store records are returned in dependency-first order" to {
            val root = planManifest("root", "1.0.0", listOf(NcpDependency("library", ">=1.0.0")))
            val library = planManifest("library", "1.2.0")
            val plan = NcpInstalledPackagePlanResolver.resolve(
                listOf(installed(root), installed(library)),
                listOf(NcpDependency("root", "1.0.0")),
                "1.0.0",
            )
            check(plan.resolution.orderedPackages.map { it.id } == listOf("library", "root"))
            check(plan.selectedPackages.map { it.id } == listOf("library", "root"))
            check(plan.selectedPackages.map { it.directory.name } == listOf("1.2.0", "1.0.0"))
        },
        "missing requested package remains unsatisfied" to {
            expectPlanError("NCP_DEPENDENCY_UNSATISFIED") {
                NcpInstalledPackagePlanResolver.resolve(
                    emptyList(),
                    listOf(NcpDependency("missing", "1.0.0")),
                    "1.0.0",
                )
            }
        },
        "missing mandatory transitive dependency remains unsatisfied" to {
            expectPlanError("NCP_DEPENDENCY_UNSATISFIED") {
                NcpInstalledPackagePlanResolver.resolve(
                    listOf(installed(planManifest("root", "1.0.0", listOf(NcpDependency("missing", ">=1.0.0"))))),
                    listOf(NcpDependency("root", "1.0.0")),
                    "1.0.0",
                )
            }
        },
        "duplicate stored ID and version are rejected" to {
            val manifest = planManifest("mod", "1.0.0")
            expectPlanError("NCP_STORE_INVALID") {
                NcpInstalledPackagePlanResolver.resolve(
                    listOf(installed(manifest), installed(manifest)),
                    listOf(NcpDependency("mod", "1.0.0")),
                    "1.0.0",
                )
            }
        },
        "store identity mismatch is rejected before resolving" to {
            val manifest = planManifest("mod", "1.0.0")
            val corruptRecord = installed(manifest).copy(id = "other")
            expectPlanError("NCP_STORE_INVALID") {
                NcpInstalledPackagePlanResolver.resolve(
                    listOf(corruptRecord),
                    listOf(NcpDependency("mod", "1.0.0")),
                    "1.0.0",
                )
            }
        },
        "optional dependency notices are preserved" to {
            val root = planManifest(
                "root",
                "1.0.0",
                optionalDependencies = listOf(NcpDependency("extra", ">=1.0.0")),
            )
            val plan = NcpInstalledPackagePlanResolver.resolve(
                listOf(installed(root), installed(planManifest("extra", "1.0.0"))),
                listOf(NcpDependency("root", "1.0.0")),
                "1.0.0",
            )
            check(plan.selectedPackages.map { it.id } == listOf("root"))
            check(plan.resolution.optionalDependencyNotices.single().dependencyId == "extra")
        },
    )

    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
    }
    println("NCP INSTALLED PACKAGE PLAN RESULT: " + tests.size + "/" + tests.size + " tests passed")
}
