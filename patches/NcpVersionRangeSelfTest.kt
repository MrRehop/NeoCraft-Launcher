package org.neocraft.neomod.core

fun main() {
    val tests = listOf(
        "exact range matches only the same version" to {
            check(NcpVersionRange.parse("1.2.3").contains("1.2.3"))
            check(!NcpVersionRange.parse("1.2.3").contains("1.2.4"))
        },
        "inclusive lower bound accepts equal version" to {
            check(NcpVersionRange.parse(">=1.2.3").contains("1.2.3"))
        },
        "lower bound rejects an older version" to {
            check(!NcpVersionRange.parse(">=1.2.3").contains("1.2.2"))
        },
        "lower bound accepts a newer major version" to {
            check(NcpVersionRange.parse(">=1.2.3").contains("2.0.0"))
        },
        "bounded range includes lower bound" to {
            check(NcpVersionRange.parse(">=1.2.3 <2.0.0").contains("1.2.3"))
        },
        "bounded range excludes upper bound" to {
            check(!NcpVersionRange.parse(">=1.2.3 <2.0.0").contains("2.0.0"))
        },
        "bounded range accepts middle version" to {
            check(NcpVersionRange.parse(">=1.2.3 <2.0.0").contains("1.9.99"))
        },
        "numeric comparison handles components beyond Int range" to {
            check(NcpVersion.parse("999999999999999999999.0.0") > NcpVersion.parse("999999999999999999998.99.99"))
        },
        "leading zero component is rejected" to {
            val error = runCatching { NcpVersion.parse("1.02.3") }.exceptionOrNull()
            check(error is NcpValidationException && error.code == "NCP_VERSION_INVALID")
        },
        "unsupported caret range is rejected" to {
            val error = runCatching { NcpVersionRange.parse("^1.2.3") }.exceptionOrNull()
            check(error is NcpValidationException && error.code == "NCP_DEPENDENCY_INVALID")
        },
        "empty bounded range is rejected" to {
            val error = runCatching { NcpVersionRange.parse(">=2.0.0 <2.0.0") }.exceptionOrNull()
            check(error is NcpValidationException && error.code == "NCP_DEPENDENCY_INVALID")
        },
        "reversed bounded range is rejected" to {
            val error = runCatching { NcpVersionRange.parse(">=2.0.0 <1.0.0") }.exceptionOrNull()
            check(error is NcpValidationException && error.code == "NCP_DEPENDENCY_INVALID")
        },
    )
    tests.forEach { (name, test) ->
        test()
        println("PASS: $name")
    }
    println("NCP VERSION RANGE RESULT: ${tests.size}/${tests.size} tests passed")
}
