package org.neocraft.neomod.core

private const val VALID_NCP_MANIFEST = """
{
  "format": "org.neocraft.ncp",
  "formatVersion": 1,
  "id": "copper_expansion",
  "name": "Copper Expansion",
  "publisher": "NeoCraft Example Publisher",
  "version": "1.0.0",
  "api": { "versionRange": ">=1.0.0 <2.0.0" },
  "capabilities": ["content.read", "content.register"],
  "dependencies": [],
  "optionalDependencies": [],
  "conflicts": [],
  "content": [
    { "type": "item", "localId": "copper_ingot", "path": "content/items/copper_ingot.json" }
  ],
  "files": [
    {
      "path": "content/items/copper_ingot.json",
      "size": 56,
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
    }
  ],
  "extensions": { "org.example.note": "fixture only" }
}
"""

fun main() {
    val tests = listOf<Pair<String, () -> Unit>>(
        "valid NCP v1 manifest decoded" to {
            val manifest = NcpManifestDecoder.decode(VALID_NCP_MANIFEST)
            check(manifest.id == "copper_expansion")
            check(manifest.content.single().namespacedId == "copper_expansion:copper_ingot")
            check(manifest.files.single().sha256.length == 64)
            check(manifest.extensions["org.example.note"] == "fixture only")
        },
        "duplicate JSON keys rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace(
                "\"name\": \"Copper Expansion\"",
                "\"name\": \"Copper Expansion\", \"name\": \"Shadow\"",
            )
            check(runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() is NcpValidationException)
        },
        "UTF-8 BOM rejected" to {
            val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                VALID_NCP_MANIFEST.toByteArray(Charsets.UTF_8)
            check(runCatching { NcpManifestDecoder.decode(bytes) }.exceptionOrNull() is NcpValidationException)
        },
        "unsupported format rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace("org.neocraft.ncp", "org.example.other")
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_FORMAT_UNSUPPORTED")
        },
        "unknown manifest field rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace("\"formatVersion\": 1,", "\"formatVersion\": 1, \"unexpected\": true,")
            check(runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() is NcpValidationException)
        },
        "invalid package ID rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace("copper_expansion", "Copper-Expansion")
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_ID_INVALID")
        },
        "unsafe traversal path rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace("content/items/copper_ingot.json", "content/../escape.json")
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_PATH_UNSAFE")
        },
        "unknown capability rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace("\"content.register\"", "\"native.execute\"")
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_CAPABILITY_UNKNOWN")
        },
        "bad SHA-256 rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace("0000000000000000000000000000000000000000000000000000000000000000", "not-a-hash")
            check(runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() is NcpValidationException)
        },
        "unlisted content path rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace(
                "\"path\": \"content/items/copper_ingot.json\",\n      \"size\"",
                "\"path\": \"content/items/other.json\",\n      \"size\"",
            )
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_FILE_INVENTORY_MISMATCH")
        },
        "duplicate namespaced content ID rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace(
                "\"type\": \"item\", \"localId\": \"copper_ingot\", \"path\": \"content/items/copper_ingot.json\"",
                "\"type\": \"item\", \"localId\": \"copper_ingot\", \"path\": \"content/items/copper_ingot.json\" },\n    { \"type\": \"block\", \"localId\": \"copper_ingot\", \"path\": \"content/items/copper_ingot.json\"",
            )
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_CONTENT_ID_DUPLICATE")
        },
        "invalid bounded version range rejected" to {
            val invalid = VALID_NCP_MANIFEST.replace(">=1.0.0 <2.0.0", ">=2.0.0 <1.0.0")
            check((runCatching { NcpManifestDecoder.decode(invalid) }.exceptionOrNull() as NcpValidationException).code == "NCP_DEPENDENCY_INVALID")
        },
    )

    var passed = 0
    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
        passed++
    }
    println("NCP MANIFEST RESULT: " + passed + "/" + tests.size + " tests passed")
}
