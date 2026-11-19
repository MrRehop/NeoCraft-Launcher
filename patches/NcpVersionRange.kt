package org.neocraft.neomod.core

import java.math.BigInteger

/** Stable SemVer subset used by NCP v1 (MAJOR.MINOR.PATCH, no prerelease/build suffix). */
data class NcpVersion private constructor(
    val major: BigInteger,
    val minor: BigInteger,
    val patch: BigInteger,
) : Comparable<NcpVersion> {
    override fun compareTo(other: NcpVersion): Int {
        val majorResult = major.compareTo(other.major)
        if (majorResult != 0) return majorResult
        val minorResult = minor.compareTo(other.minor)
        if (minorResult != 0) return minorResult
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")

        fun parse(value: String): NcpVersion {
            val match = VERSION.matchEntire(value)
                ?: fail("NCP_VERSION_INVALID", "version must use stable MAJOR.MINOR.PATCH")
            return try {
                NcpVersion(
                    BigInteger(match.groupValues[1]),
                    BigInteger(match.groupValues[2]),
                    BigInteger(match.groupValues[3]),
                )
            } catch (_: NumberFormatException) {
                fail("NCP_VERSION_INVALID", "version component is invalid")
            }
        }

        private fun fail(code: String, message: String): Nothing =
            throw NcpValidationException(code, message)
    }
}

/** Evaluates only the exact, >=, and >= lower / < upper range forms accepted by NCP v1. */
sealed class NcpVersionRange {
    abstract fun contains(version: NcpVersion): Boolean

    fun contains(version: String): Boolean = contains(NcpVersion.parse(version))

    data class Exact(val version: NcpVersion) : NcpVersionRange() {
        override fun contains(version: NcpVersion): Boolean = this.version == version
    }

    data class AtLeast(val minimum: NcpVersion) : NcpVersionRange() {
        override fun contains(version: NcpVersion): Boolean = version >= minimum
    }

    data class Bounded(val minimum: NcpVersion, val maximumExclusive: NcpVersion) : NcpVersionRange() {
        override fun contains(version: NcpVersion): Boolean =
            version >= minimum && version < maximumExclusive
    }

    companion object {
        private val EXACT = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        private val LOWER = Regex("^>=(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        private val BOUNDED = Regex(
            "^>=(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*) " +
                "<(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
        )

        fun parse(value: String): NcpVersionRange {
            EXACT.matchEntire(value)?.let { return Exact(NcpVersion.parse(value)) }
            LOWER.matchEntire(value)?.let { return AtLeast(NcpVersion.parse(value.removePrefix(">="))) }
            BOUNDED.matchEntire(value)?.let {
                val minimum = NcpVersion.parse(it.groupValues[1] + "." + it.groupValues[2] + "." + it.groupValues[3])
                val maximum = NcpVersion.parse(it.groupValues[4] + "." + it.groupValues[5] + "." + it.groupValues[6])
                if (minimum >= maximum) {
                    fail("NCP_DEPENDENCY_INVALID", "range lower bound must be less than upper bound")
                }
                return Bounded(minimum, maximum)
            }
            fail("NCP_DEPENDENCY_INVALID", "unsupported NCP v1 version range")
        }

        private fun fail(code: String, message: String): Nothing =
            throw NcpValidationException(code, message)
    }
}
