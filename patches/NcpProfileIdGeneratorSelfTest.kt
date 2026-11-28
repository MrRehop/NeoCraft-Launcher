package org.neocraft.neomod.core

private fun expectProfileIdFailure(action: () -> Unit) {
    check(runCatching(action).exceptionOrNull() is IllegalArgumentException) {
        "expected IllegalArgumentException"
    }
}

fun main() {
    val tests = listOf(
        "normalizes a display name to a stable lowercase ID" to {
            check(NcpProfileIdGenerator.createUnique("Survival / LAN", emptySet()) == "survival-lan")
        },
        "uses a safe fallback when the name has no ASCII identifier characters" to {
            check(NcpProfileIdGenerator.createUnique("Тестовый мир", emptySet()) == "profile")
        },
        "adds the first suffix when the base ID already exists" to {
            check(NcpProfileIdGenerator.createUnique("Survival", setOf("survival")) == "survival-2")
        },
        "increments suffixes until an unused ID is found" to {
            check(
                NcpProfileIdGenerator.createUnique(
                    "Survival",
                    setOf("survival", "survival-2"),
                ) == "survival-3"
            )
        },
        "keeps generated IDs within the 48-character profile limit" to {
            val id = NcpProfileIdGenerator.createUnique("a".repeat(80), emptySet())
            check(id.length <= 48)
            check(Regex("^[a-z][a-z0-9_-]{1,47}$").matches(id))
        },
        "rejects a blank display name" to {
            expectProfileIdFailure { NcpProfileIdGenerator.createUnique("  ", emptySet()) }
        },
        "rejects a display name longer than the snapshot limit" to {
            expectProfileIdFailure { NcpProfileIdGenerator.createUnique("x".repeat(81), emptySet()) }
        },
    )

    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
    }
    println("NCP PROFILE ID GENERATOR RESULT: " + tests.size + "/" + tests.size + " tests passed")
}
