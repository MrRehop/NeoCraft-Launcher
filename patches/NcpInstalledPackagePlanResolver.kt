package org.neocraft.neomod.core

/**
 * Resolves requested NCP packages against the manifests of verified packages in
 * the local store and maps the resulting dependency-first plan back to storage.
 *
 * This component only plans which installed package data is selected. It does
 * not install dependencies, activate profiles, or load content into Minecraft.
 */
data class NcpInstalledPackagePlan(
    val resolution: NcpDependencyResolution,
    val selectedPackages: List<NcpPackageStore.InstalledPackage>,
)

object NcpInstalledPackagePlanResolver {
    /**
     * Lists and integrity-checks the store once, then resolves requests against
     * the verified installed manifests.
     */
    fun resolve(
        store: NcpPackageStore,
        requests: List<NcpDependency>,
        neoModApiVersion: String,
    ): NcpInstalledPackagePlan = resolve(store.listInstalled(), requests, neoModApiVersion)

    /**
     * Pure mapping overload, suitable for tests and callers that already have a
     * verified store snapshot.
     */
    fun resolve(
        installedPackages: List<NcpPackageStore.InstalledPackage>,
        requests: List<NcpDependency>,
        neoModApiVersion: String,
    ): NcpInstalledPackagePlan {
        val byKey = linkedMapOf<Pair<String, String>, NcpPackageStore.InstalledPackage>()
        for (installed in installedPackages) {
            if (installed.id != installed.manifest.id || installed.version != installed.manifest.version) {
                fail(
                    "NCP_STORE_INVALID",
                    "installed package identity differs from its manifest: " + installed.id + "@" + installed.version,
                )
            }
            val key = installed.id to installed.version
            if (byKey.put(key, installed) != null) {
                fail("NCP_STORE_INVALID", "duplicate installed package version: " + installed.id + "@" + installed.version)
            }
        }

        val resolution = NcpDependencyResolver.resolve(
            requests = requests,
            installedCandidates = installedPackages.map { it.manifest },
            neoModApiVersion = neoModApiVersion,
        )
        val selected = resolution.orderedPackages.map { manifest ->
            byKey[manifest.id to manifest.version]
                ?: fail("NCP_STORE_INVALID", "resolved package is missing from the verified store: " + manifest.id + "@" + manifest.version)
        }
        return NcpInstalledPackagePlan(resolution, selected)
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)
}
