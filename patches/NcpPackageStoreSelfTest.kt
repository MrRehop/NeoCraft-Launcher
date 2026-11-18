package org.neocraft.neomod.core

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun ncpStoreHash(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun ncpStoreArchive(version: String = "1.0.0", payload: ByteArray = "hello".toByteArray(), declaredHashOverride: String? = null): File {
    val hash = declaredHashOverride ?: ncpStoreHash(payload)
    val manifest = """
        {
          "format":"org.neocraft.ncp",
          "formatVersion":1,
          "id":"sample_pack",
          "name":"Sample Pack",
          "publisher":"NeoCraft Tests",
          "version":"__VERSION__",
          "api":{"versionRange":">=1.0.0 <2.0.0"},
          "capabilities":[],
          "dependencies":[],
          "optionalDependencies":[],
          "conflicts":[],
          "content":[],
          "files":[{"path":"content/data.txt","size":__SIZE__,"sha256":"__HASH__"}],
          "extensions":{}
        }
    """.trimIndent()
        .replace("__VERSION__", version)
        .replace("__SIZE__", payload.size.toString())
        .replace("__HASH__", hash)
        .toByteArray(Charsets.UTF_8)
    val file = File.createTempFile("neocraft-ncp-store-", ".ncp")
    ZipOutputStream(file.outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(manifest)
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("content/data.txt"))
        zip.write(payload)
        zip.closeEntry()
    }
    return file
}

fun main() {
    val tests = listOf(
        "valid package installs and is listed" to {
            val root = Files.createTempDirectory("ncp-store-valid-").toFile()
            val archive = ncpStoreArchive()
            try {
                val store = NcpPackageStore(root)
                val installed = store.install(archive)
                check(installed.id == "sample_pack")
                check(installed.version == "1.0.0")
                check(installed.directory.resolve("content/data.txt").readText() == "hello")
                check(store.listInstalled().map { it.id to it.version } == listOf("sample_pack" to "1.0.0"))
            } finally {
                archive.delete()
                root.deleteRecursively()
            }
        },
        "multiple versions coexist without overwriting" to {
            val root = Files.createTempDirectory("ncp-store-versions-").toFile()
            val first = ncpStoreArchive("1.0.0")
            val second = ncpStoreArchive("1.1.0", "world".toByteArray())
            try {
                val store = NcpPackageStore(root)
                store.install(first)
                store.install(second)
                check(store.listInstalled().map { it.version } == listOf("1.0.0", "1.1.0"))
                check(root.resolve("packages/sample_pack/1.0.0/content/data.txt").readText() == "hello")
                check(root.resolve("packages/sample_pack/1.1.0/content/data.txt").readText() == "world")
            } finally {
                first.delete()
                second.delete()
                root.deleteRecursively()
            }
        },
        "duplicate version is rejected without damaging installed copy" to {
            val root = Files.createTempDirectory("ncp-store-duplicate-").toFile()
            val first = ncpStoreArchive()
            val duplicate = ncpStoreArchive(payload = "other".toByteArray())
            try {
                val store = NcpPackageStore(root)
                store.install(first)
                val error = runCatching { store.install(duplicate) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_PACKAGE_ALREADY_INSTALLED")
                check(root.resolve("packages/sample_pack/1.0.0/content/data.txt").readText() == "hello")
                check(store.listInstalled().size == 1)
            } finally {
                first.delete()
                duplicate.delete()
                root.deleteRecursively()
            }
        },
        "failed validation leaves no visible package" to {
            val root = Files.createTempDirectory("ncp-store-failure-").toFile()
            val invalid = ncpStoreArchive(payload = "tampered".toByteArray(), declaredHashOverride = "0".repeat(64))
            try {
                val store = NcpPackageStore(root)
                val error = runCatching { store.install(invalid) }.exceptionOrNull()
                check(error is NcpValidationException &&
                    (error.code == "NCP_FILE_INVENTORY_MISMATCH" || error.code == "NCP_HASH_MISMATCH"))
                check(store.listInstalled().isEmpty())
                check(root.resolve("packages").listFiles()?.none { it.name.startsWith(".staging-") } != false)
            } finally {
                invalid.delete()
                root.deleteRecursively()
            }
        },
        "modified installed payload is detected" to {
            val root = Files.createTempDirectory("ncp-store-tamper-").toFile()
            val archive = ncpStoreArchive()
            try {
                val store = NcpPackageStore(root)
                store.install(archive)
                // Keep the original byte length so the integrity check reaches SHA-256.
                root.resolve("packages/sample_pack/1.0.0/content/data.txt").writeText("xxxxx")
                val error = runCatching { store.listInstalled() }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_HASH_MISMATCH")
            } finally {
                archive.delete()
                root.deleteRecursively()
            }
        },
        "uninstall removes only requested version" to {
            val root = Files.createTempDirectory("ncp-store-uninstall-").toFile()
            val first = ncpStoreArchive("1.0.0")
            val second = ncpStoreArchive("1.1.0", "world".toByteArray())
            try {
                val store = NcpPackageStore(root)
                store.install(first)
                store.install(second)
                store.uninstall("sample_pack", "1.0.0")
                check(store.listInstalled().map { it.version } == listOf("1.1.0"))
            } finally {
                first.delete()
                second.delete()
                root.deleteRecursively()
            }
        },
        "uninstall of absent package is rejected" to {
            val root = Files.createTempDirectory("ncp-store-absent-").toFile()
            try {
                val store = NcpPackageStore(root)
                val error = runCatching { store.uninstall("sample_pack", "1.0.0") }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_PACKAGE_NOT_FOUND")
            } finally {
                root.deleteRecursively()
            }
        },
    )
    tests.forEach { (name, test) ->
        test()
        println("PASS: " + name)
    }
    println("NCP PACKAGE STORE RESULT: " + tests.size + "/" + tests.size + " tests passed")
}
