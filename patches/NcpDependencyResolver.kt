package org.neocraft.neomod.core

/**
 * Resolves a requested NCP package set against already-installed manifests.
 *
 * This is a deterministic, platform-neutral planning component. It does not install,
 * activate, execute, or load package content into Minecraft.
 */
data class NcpOptionalDependencyNotice(
    val ownerId: String,
    val dependencyId: String,
    val versionRange: String,
    val reason: Reason,
) {
    enum class Reason {
        NOT_SELECTED,
        SELECTED_VERSION_OUT_OF_RANGE,
    }
}

data class NcpDependencyResolution(
    /** Dependency-first order suitable for a future activation plan. */
    val orderedPackages: List<NcpManifest>,
    val optionalDependencyNotices: List<NcpOptionalDependencyNotice>,
)

object NcpDependencyResolver {
    private val packageIdRegex = Regex("^[a-z][a-z0-9_]{1,47}$")
    private const val MAX_REQUESTS = 256
    private const val MAX_CANDIDATE_MANIFESTS = 4096
    private const val MAX_CONSTRAINT_IDS = 4096
    private const val MAX_SELECTED_PACKAGES = 256
    private const val MAX_SEARCH_STATES = 10000

    private data class Candidate(
        val manifest: NcpManifest,
        val version: NcpVersion,
        val apiRange: NcpVersionRange,
    )

    fun resolve(
        requests: List<NcpDependency>,
        installedCandidates: List<NcpManifest>,
        neoModApiVersion: String,
    ): NcpDependencyResolution {
        if (requests.isEmpty()) {
            fail("NCP_RESOLUTION_INVALID", "at least one requested package is required")
        }
        if (requests.size > MAX_REQUESTS) {
            fail("NCP_LIMIT_EXCEEDED", "requested package count exceeds " + MAX_REQUESTS)
        }
        if (installedCandidates.size > MAX_CANDIDATE_MANIFESTS) {
            fail("NCP_LIMIT_EXCEEDED", "candidate manifest count exceeds " + MAX_CANDIDATE_MANIFESTS)
        }
        val apiVersion = NcpVersion.parse(neoModApiVersion)
        val parsedRequests = requests.map { request ->
            if (!packageIdRegex.matches(request.id)) {
                fail("NCP_DEPENDENCY_INVALID", "invalid requested package ID: " + request.id)
            }
            request.id to NcpVersionRange.parse(request.versionRange)
        }

        val candidatesById = installedCandidates
            .groupBy { it.id }
            .mapValues { (id, manifests) ->
                val byVersion = manifests.groupBy { it.version }
                byVersion.forEach { (version, duplicates) ->
                    if (duplicates.distinct().size > 1) {
                        fail(
                            "NCP_CANDIDATE_AMBIGUOUS",
                            "installed candidates contain different manifests for " + id + "@" + version,
                        )
                    }
                }
                byVersion.values.map { duplicates ->
                    val manifest = duplicates.first()
                    Candidate(
                        manifest = manifest,
                        version = NcpVersion.parse(manifest.version),
                        apiRange = NcpVersionRange.parse(manifest.apiVersionRange),
                    )
                }.sortedByDescending { it.version }
            }

        val constraints = parsedRequests.groupBy({ it.first }, { it.second })
        var unsatisfiedId: String? = null
        var apiIncompatibleId: String? = null
        var cyclePath: List<String>? = null
        var conflictPair: Pair<String, String>? = null
        var searchStates = 0

        fun compatibleCandidates(id: String, ranges: List<NcpVersionRange>): List<Candidate> {
            val all = candidatesById[id].orEmpty()
            val apiCompatible = all.filter { it.apiRange.contains(apiVersion) }
            if (apiCompatible.isEmpty() && all.isNotEmpty()) {
                apiIncompatibleId = id
                return emptyList()
            }
            val matching = apiCompatible.filter { candidate ->
                ranges.all { it.contains(candidate.version) }
            }
            if (matching.isEmpty()) unsatisfiedId = id
            return matching
        }

        fun findConflict(selected: Map<String, NcpManifest>): Pair<String, String>? {
            val ids = selected.keys.sorted()
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val left = selected.getValue(ids[i])
                    val right = selected.getValue(ids[j])
                    if (right.id in left.conflicts || left.id in right.conflicts) {
                        return left.id to right.id
                    }
                }
            }
            return null
        }

        fun findCycle(selected: Map<String, NcpManifest>): List<String>? {
            val state = mutableMapOf<String, Int>()
            val stack = mutableListOf<String>()

            fun visit(id: String): List<String>? {
                state[id] = 1
                stack += id
                val dependencies = selected.getValue(id).dependencies
                    .map { it.id }
                    .filter { it in selected }
                    .distinct()
                    .sorted()
                for (dependencyId in dependencies) {
                    when (state[dependencyId] ?: 0) {
                        0 -> visit(dependencyId)?.let { return it }
                        1 -> {
                            val start = stack.indexOf(dependencyId)
                            return stack.subList(start, stack.size).toList() + dependencyId
                        }
                    }
                }
                stack.removeAt(stack.lastIndex)
                state[id] = 2
                return null
            }

            for (id in selected.keys.sorted()) {
                if ((state[id] ?: 0) == 0) visit(id)?.let { return it }
            }
            return null
        }

        fun search(
            currentConstraints: Map<String, List<NcpVersionRange>>,
            selected: Map<String, NcpManifest>,
        ): Map<String, NcpManifest>? {
            searchStates += 1
            if (searchStates > MAX_SEARCH_STATES) {
                fail("NCP_LIMIT_EXCEEDED", "dependency resolution exceeded " + MAX_SEARCH_STATES + " search states")
            }
            if (selected.size > MAX_SELECTED_PACKAGES) {
                fail("NCP_LIMIT_EXCEEDED", "resolved package count exceeds " + MAX_SELECTED_PACKAGES)
            }
            for ((id, manifest) in selected) {
                val ranges = currentConstraints[id].orEmpty()
                if (ranges.any { !it.contains(manifest.version) }) {
                    unsatisfiedId = id
                    return null
                }
            }
            findConflict(selected)?.let {
                conflictPair = it
                return null
            }
            findCycle(selected)?.let {
                cyclePath = it
                return null
            }

            val unresolved = currentConstraints.keys.filter { it !in selected }
            if (unresolved.isEmpty()) return selected

            val nextId = unresolved.minWithOrNull(
                compareBy<String>(
                    { candidatesById[it].orEmpty().count { candidate -> candidate.apiRange.contains(apiVersion) } },
                    { it },
                ),
            ) ?: return selected

            val ranges = currentConstraints.getValue(nextId)
            val options = compatibleCandidates(nextId, ranges)
            if (options.isEmpty()) return null

            for (candidate in options) {
                val nextSelected = selected + (nextId to candidate.manifest)
                val nextConstraints = currentConstraints.mapValues { it.value.toList() }.toMutableMap()
                for (dependency in candidate.manifest.dependencies) {
                    val dependencyRange = NcpVersionRange.parse(dependency.versionRange)
                    nextConstraints[dependency.id] =
                        nextConstraints[dependency.id].orEmpty() + dependencyRange
                    if (nextConstraints.size > MAX_CONSTRAINT_IDS) {
                        fail("NCP_LIMIT_EXCEEDED", "dependency constraint IDs exceed " + MAX_CONSTRAINT_IDS)
                    }
                }
                search(nextConstraints, nextSelected)?.let { return it }
            }
            return null
        }

        val selected = search(constraints, emptyMap()) ?: run {
            when {
                unsatisfiedId != null ->
                    fail(
                        "NCP_DEPENDENCY_UNSATISFIED",
                        "no installed version satisfies all constraints for " + unsatisfiedId,
                    )
                conflictPair != null ->
                    fail(
                        "NCP_CONFLICT_DECLARED",
                        "selected packages conflict: " + conflictPair!!.first + " and " + conflictPair!!.second,
                    )
                cyclePath != null ->
                    fail("NCP_DEPENDENCY_CYCLE", "mandatory dependency cycle: " + cyclePath!!.joinToString(" -> "))
                apiIncompatibleId != null ->
                    fail("NCP_API_INCOMPATIBLE", "installed candidates for " + apiIncompatibleId + " do not support NeoMod API " + neoModApiVersion)
                else -> fail("NCP_DEPENDENCY_UNSATISFIED", "no compatible installed package plan exists")
            }
        }

        val ordered = topologicalOrder(selected)
        val notices = ordered.flatMap { manifest ->
            manifest.optionalDependencies.mapNotNull { dependency ->
                val chosen = selected[dependency.id]
                when {
                    chosen == null -> NcpOptionalDependencyNotice(
                        manifest.id,
                        dependency.id,
                        dependency.versionRange,
                        NcpOptionalDependencyNotice.Reason.NOT_SELECTED,
                    )
                    !NcpVersionRange.parse(dependency.versionRange).contains(chosen.version) ->
                        NcpOptionalDependencyNotice(
                            manifest.id,
                            dependency.id,
                            dependency.versionRange,
                            NcpOptionalDependencyNotice.Reason.SELECTED_VERSION_OUT_OF_RANGE,
                        )
                    else -> null
                }
            }
        }
        return NcpDependencyResolution(ordered, notices)
    }

    private fun topologicalOrder(selected: Map<String, NcpManifest>): List<NcpManifest> {
        val state = mutableMapOf<String, Int>()
        val result = mutableListOf<NcpManifest>()

        fun visit(id: String) {
            when (state[id]) {
                1 -> fail("NCP_DEPENDENCY_CYCLE", "mandatory dependency cycle encountered during ordering")
                2 -> return
            }
            state[id] = 1
            selected.getValue(id).dependencies
                .map { it.id }
                .filter { it in selected }
                .distinct()
                .sorted()
                .forEach(::visit)
            state[id] = 2
            result += selected.getValue(id)
        }

        selected.keys.sorted().forEach(::visit)
        return result
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)
}
