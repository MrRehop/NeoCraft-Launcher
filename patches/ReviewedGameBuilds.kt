package org.neocraft.neomod.core

/**
 * Explicit allow-list of game builds reviewed by the NeoCraft maintainers.
 *
 * Keep this empty until a build's package identity, signing certificate and
 * compatibility evidence have been independently verified. Discovery alone
 * must never grant NeoMod compatibility.
 */
object ReviewedGameBuilds {
    val registry = GameBuildRegistry(emptyList())
}
