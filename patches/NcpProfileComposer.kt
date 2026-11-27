package org.neocraft.neomod.core

/**
 * User-authored metadata needed to compose a profile from already-installed NCP packages.
 *
 * This draft is not persisted and does not activate or execute package content.
 */
data class NcpProfileDraft(
    val profileId: String,
    val displayName: String,
    val gameBuild: NcpGameBuildIdentity,
    val neoModApiVersion: String,
    val requestedPackages: List<NcpDependency>,
)

/** A composed profile and the exact installed-package plan used to create it. */
data class NcpProfileComposition(
    val snapshot: NcpProfileSnapshot,
    val plan: NcpInstalledPackagePlan,
)

/**
 * Builds a validated profile snapshot from a draft and a fresh package-store view.
 *
 * The store overload calls listInstalled(), which re-checks package payload integrity.
 * The snapshot is still metadata only: composition never activates packages or modifies
 * Minecraft. The returned plan must be passed to NcpProfileStore.save().
 */
object NcpProfileComposer {
    fun compose(
        draft: NcpProfileDraft,
        store: NcpPackageStore,
    ): NcpProfileComposition = compose(draft, store.listInstalled())

    /**
     * Pure overload for callers that already hold a verified installed-store snapshot.
     * Do not pass records assembled from untrusted profile or package metadata.
     */
    internal fun compose(
        draft: NcpProfileDraft,
        verifiedInstalledPackages: List<NcpPackageStore.InstalledPackage>,
    ): NcpProfileComposition {
        val requests = draft.requestedPackages.toList()
        val plan = NcpInstalledPackagePlanResolver.resolve(
            installedPackages = verifiedInstalledPackages,
            requests = requests,
            neoModApiVersion = draft.neoModApiVersion,
        )
        val snapshot = NcpProfileSnapshot(
            profileId = draft.profileId,
            displayName = draft.displayName,
            gameBuild = draft.gameBuild,
            neoModApiVersion = draft.neoModApiVersion,
            requestedPackages = requests,
            resolvedPackages = plan.selectedPackages.mapIndexed { index, installed ->
                NcpProfilePackage(installed.id, installed.version, index)
            },
        )
        NcpProfileSnapshotValidator.validate(snapshot, plan)
        return NcpProfileComposition(snapshot, plan)
    }
}
