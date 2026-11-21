package org.neocraft.neomod.core

private fun manifest(
    id: String,
    version: String,
    dependencies: List<NcpDependency> = emptyList(),
    optionalDependencies: List<NcpDependency> = emptyList(),
    conflicts: Set<String> = emptySet(),
    apiVersionRange: String = ">=1.0.0 <2.0.0",
): NcpManifest = NcpManifest(
    id = id,
    name = id,
    publisher = "self-test",
    version = version,
    apiVersionRange = apiVersionRange,
    capabilities = emptySet(),
    dependencies = dependencies,
    optionalDependencies = optionalDependencies,
    conflicts = conflicts,
    content = emptyList(),
    files = emptyList(),
    extensions = emptyMap(),
)

private fun expectCode(code: String, action: () -> Unit) {
    val error = runCatching(action).exceptionOrNull()
    check(error is NcpValidationException && error.code == code) {
        "expected " + code + ", got " + (error?.javaClass?.simpleName + ": " + error?.message)
    }
}

fun main() {
    val tests = listOf(
        "select highest installed version satisfying request" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("mod", ">=1.0.0 <3.0.0")),
                listOf(manifest("mod", "1.2.0"), manifest("mod", "2.1.0"), manifest("mod", "3.0.0")),
                "1.0.0",
            )
            check(result.orderedPackages.single().version == "2.1.0")
        },
        "mandatory dependencies are ordered before dependents" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("root", "1.0.0")),
                listOf(
                    manifest("root", "1.0.0", listOf(NcpDependency("library", ">=1.0.0"))),
                    manifest("library", "1.4.0"),
                ),
                "1.0.0",
            )
            check(result.orderedPackages.map { it.id } == listOf("library", "root"))
        },
        "backtracking finds a version satisfying transitive constraints" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("alpha", ">=1.0.0"), NcpDependency("beta", "1.0.0")),
                listOf(
                    manifest("alpha", "2.0.0", listOf(NcpDependency("library", ">=1.0.0 <2.0.0"))),
                    manifest("alpha", "1.0.0", listOf(NcpDependency("library", ">=2.0.0 <3.0.0"))),
                    manifest("beta", "1.0.0", listOf(NcpDependency("library", ">=2.0.0 <3.0.0"))),
                    manifest("library", "1.5.0"),
                    manifest("library", "2.1.0"),
                ),
                "1.0.0",
            )
            check(result.orderedPackages.single { it.id == "alpha" }.version == "1.0.0")
            check(result.orderedPackages.single { it.id == "library" }.version == "2.1.0")
        },
        "missing mandatory dependency is reported" to {
            expectCode("NCP_DEPENDENCY_UNSATISFIED") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("root", "1.0.0")),
                    listOf(manifest("root", "1.0.0", listOf(NcpDependency("missing", ">=1.0.0")))),
                    "1.0.0",
                )
            }
        },
        "mandatory dependency cycle is rejected" to {
            expectCode("NCP_DEPENDENCY_CYCLE") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("alpha", "1.0.0")),
                    listOf(
                        manifest("alpha", "1.0.0", listOf(NcpDependency("beta", "1.0.0"))),
                        manifest("beta", "1.0.0", listOf(NcpDependency("alpha", "1.0.0"))),
                    ),
                    "1.0.0",
                )
            }
        },
        "declared package conflict is rejected" to {
            expectCode("NCP_CONFLICT_DECLARED") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("alpha", "1.0.0"), NcpDependency("beta", "1.0.0")),
                    listOf(manifest("alpha", "1.0.0", conflicts = setOf("beta")), manifest("beta", "1.0.0")),
                    "1.0.0",
                )
            }
        },
        "candidate incompatible with requested NeoMod API is rejected" to {
            expectCode("NCP_API_INCOMPATIBLE") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("mod", "1.0.0")),
                    listOf(manifest("mod", "1.0.0", apiVersionRange = ">=2.0.0")),
                    "1.0.0",
                )
            }
        },
        "optional dependency is not auto-selected" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("root", "1.0.0")),
                listOf(
                    manifest("root", "1.0.0", optionalDependencies = listOf(NcpDependency("extra", ">=1.0.0"))),
                    manifest("extra", "1.0.0"),
                ),
                "1.0.0",
            )
            check(result.orderedPackages.map { it.id } == listOf("root"))
            check(result.optionalDependencyNotices.single().reason == NcpOptionalDependencyNotice.Reason.NOT_SELECTED)
        },
        "selected optional dependency outside range is reported" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("root", "1.0.0"), NcpDependency("extra", "1.0.0")),
                listOf(
                    manifest("root", "1.0.0", optionalDependencies = listOf(NcpDependency("extra", ">=2.0.0"))),
                    manifest("extra", "1.0.0"),
                ),
                "1.0.0",
            )
            check(result.optionalDependencyNotices.single().reason ==
                NcpOptionalDependencyNotice.Reason.SELECTED_VERSION_OUT_OF_RANGE)
        },
        "satisfied optional dependency has no notice" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("root", "1.0.0"), NcpDependency("extra", "1.0.0")),
                listOf(
                    manifest("root", "1.0.0", optionalDependencies = listOf(NcpDependency("extra", ">=1.0.0"))),
                    manifest("extra", "1.0.0"),
                ),
                "1.0.0",
            )
            check(result.optionalDependencyNotices.isEmpty())
        },
        "ordering is deterministic for independent roots" to {
            val first = NcpDependencyResolver.resolve(
                listOf(NcpDependency("zeta", "1.0.0"), NcpDependency("alpha", "1.0.0")),
                listOf(manifest("zeta", "1.0.0"), manifest("alpha", "1.0.0")),
                "1.0.0",
            )
            val second = NcpDependencyResolver.resolve(
                listOf(NcpDependency("alpha", "1.0.0"), NcpDependency("zeta", "1.0.0")),
                listOf(manifest("alpha", "1.0.0"), manifest("zeta", "1.0.0")),
                "1.0.0",
            )
            check(first.orderedPackages.map { it.id } == listOf("alpha", "zeta"))
            check(first.orderedPackages.map { it.id } == second.orderedPackages.map { it.id })
        },
        "exact request range is enforced" to {
            val result = NcpDependencyResolver.resolve(
                listOf(NcpDependency("mod", "1.2.3")),
                listOf(manifest("mod", "1.2.3"), manifest("mod", "1.2.4")),
                "1.0.0",
            )
            check(result.orderedPackages.single().version == "1.2.3")
        },
        "ambiguous duplicate candidate manifests are rejected" to {
            expectCode("NCP_CANDIDATE_AMBIGUOUS") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("mod", "1.0.0")),
                    listOf(manifest("mod", "1.0.0"), manifest("mod", "1.0.0").copy(name = "different")),
                    "1.0.0",
                )
            }
        },
        "candidate manifest count is bounded" to {
            val candidates = (0..4096).map { index ->
                manifest("mod", "1.0." + index)
            }
            expectCode("NCP_LIMIT_EXCEEDED") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("mod", ">=1.0.0")),
                    candidates,
                    "1.0.0",
                )
            }
        },
        "selected dependency graph size is bounded" to {
            val dependencyIds = (0..255).map { index -> "pkg" + index.toString().padStart(3, '0') }
            val candidates = listOf(
                manifest("root", "1.0.0", dependencyIds.map { NcpDependency(it, "1.0.0") }),
            ) + dependencyIds.map { manifest(it, "1.0.0") }
            expectCode("NCP_LIMIT_EXCEEDED") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("root", "1.0.0")),
                    candidates,
                    "1.0.0",
                )
            }
        },
        "requested package count is bounded" to {
            val requests = (0..256).map { index ->
                NcpDependency("pkg" + index.toString().padStart(3, '0'), "1.0.0")
            }
            expectCode("NCP_LIMIT_EXCEEDED") {
                NcpDependencyResolver.resolve(requests, emptyList(), "1.0.0")
            }
        },
        "dependency search state limit is enforced" to {
            // 100 versions for each of two requested packages create 10,000
            // failed candidate combinations, exceeding the 10,000-state cap.
            val candidates = (0..99).flatMap { index ->
                listOf(
                    manifest("alpha", "1.0." + index),
                    manifest("beta", "1.0." + index, listOf(NcpDependency("missing", ">=1.0.0"))),
                )
            }
            expectCode("NCP_LIMIT_EXCEEDED") {
                NcpDependencyResolver.resolve(
                    listOf(NcpDependency("alpha", ">=1.0.0"), NcpDependency("beta", ">=1.0.0")),
                    candidates,
                    "1.0.0",
                )
            }
        },
        "empty request set is rejected" to {
            expectCode("NCP_RESOLUTION_INVALID") {
                NcpDependencyResolver.resolve(emptyList(), emptyList(), "1.0.0")
            }
        },
    )

    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
    }
    println("NCP DEPENDENCY RESOLVER RESULT: " + tests.size + "/" + tests.size + " tests passed")
}
