package org.neocraft.neomod.core

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun ncpTestHash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun makeNcpTestArchive(entries: List<Pair<String, ByteArray>>): File {
    val file = File.createTempFile("neocraft-ncp-", ".ncp")
    ZipOutputStream(file.outputStream()).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return file
}

private fun ncpTestManifest(hash: String = ncpTestHash("hello".toByteArray()), size: Int = 5): ByteArray =
    """
    {
      "format":"org.neocraft.ncp",
      "formatVersion":1,
      "id":"sample_pack",
      "name":"Sample Pack",
      "publisher":"NeoCraft Tests",
      "version":"1.0.0",
      "api":{"versionRange":">=1.0.0 <2.0.0"},
      "capabilities":[],
      "dependencies":[],
      "optionalDependencies":[],
      "conflicts":[],
      "content":[],
      "files":[{"path":"content/data.txt","size":$size,"sha256":"$hash"}],
      "extensions":{}
    }
    """.trimIndent().toByteArray(Charsets.UTF_8)

fun main() {
    val payload = "hello".toByteArray()
    val tests = listOf(
        "valid archive and payload digest" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/data.txt" to payload,
            ))
            try {
                val report = NcpPackageReader.validate(file)
                check(report.manifest.id == "sample_pack")
                check(report.verifiedFiles.single().sha256 == ncpTestHash(payload))
                check(report.verifiedFiles.single().size == payload.size.toLong())
            } finally { file.delete() }
        },
        "payload hash mismatch rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest("0".repeat(64)),
                "content/data.txt" to payload,
            ))
            try {
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_HASH_MISMATCH")
            } finally { file.delete() }
        },
        "unlisted archive payload rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/data.txt" to payload,
                "content/extra.txt" to "extra".toByteArray(),
            ))
            try {
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_FILE_INVENTORY_MISMATCH")
            } finally { file.delete() }
        },
        "path traversal rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/../escape.txt" to payload,
            ))
            try {
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_PATH_UNSAFE")
            } finally { file.delete() }
        },
    )
    tests.forEach { (name, test) ->
        test()
        println("PASS: $name")
    }
    println("NCP PACKAGE READER RESULT: ${tests.size}/${tests.size} tests passed")
}
