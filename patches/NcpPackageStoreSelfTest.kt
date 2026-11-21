package org.neocraft.neomod.core

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
        "concurrent duplicate installs from separate store instances are serialized" to {
            val root = Files.createTempDirectory("ncp-store-concurrent-").toFile()
            val concurrentPayload = ByteArray(256 * 1024).also { Random(0x4E4350L).nextBytes(it) }
            val archive = ncpStoreArchive(payload = concurrentPayload)
            val workerCount = 8
            val ready = CountDownLatch(workerCount)
            val start = CountDownLatch(1)
            val successes = AtomicInteger()
            val duplicateErrors = AtomicInteger()
            val unexpected = AtomicReference<Throwable?>(null)
            val workers = (0 until workerCount).map {
                Thread {
                    val store = NcpPackageStore(root)
                    ready.countDown()
                    try {
                        start.await()
                        store.install(archive)
                        successes.incrementAndGet()
                    } catch (error: NcpValidationException) {
                        if (error.code == "NCP_PACKAGE_ALREADY_INSTALLED") {
                            duplicateErrors.incrementAndGet()
                        } else {
                            unexpected.compareAndSet(null, error)
                        }
                    } catch (error: Throwable) {
                        unexpected.compareAndSet(null, error)
                    }
                }.apply { start() }
            }
            try {
                ready.await()
                start.countDown()
                workers.forEach { it.join() }
                check(unexpected.get() == null) { "unexpected concurrent install failure: ${unexpected.get()}" }
                check(successes.get() == 1) { "expected one installer, got ${successes.get()}" }
                check(duplicateErrors.get() == workerCount - 1) {
                    "expected ${workerCount - 1} duplicate rejections, got ${duplicateErrors.get()}"
                }
                val installed = NcpPackageStore(root).listInstalled()
                check(installed.size == 1)
                check(installed.single().directory.resolve("content/data.txt").readBytes()
                    .contentEquals(concurrentPayload))
            } finally {
                archive.delete()
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
        "symbolic-link archive source is rejected" to {
            val root = Files.createTempDirectory("ncp-store-source-link-").toFile()
            val archive = ncpStoreArchive()
            val link = Files.createTempFile("ncp-store-source-link-", ".ncp")
            try {
                Files.delete(link)
                Files.createSymbolicLink(link, archive.toPath())
                val store = NcpPackageStore(root)
                val error = runCatching { store.install(link.toFile()) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_ARCHIVE_INVALID")
                check(store.listInstalled().isEmpty())
            } finally {
                Files.deleteIfExists(link)
                archive.delete()
                root.deleteRecursively()
            }
        },
        "symbolic-link payload tampering is rejected without following target" to {
            val root = Files.createTempDirectory("ncp-store-payload-link-").toFile()
            val archive = ncpStoreArchive()
            val outside = Files.createTempFile("ncp-store-outside-", ".txt")
            Files.writeString(outside, "outside")
            try {
                val store = NcpPackageStore(root)
                val installed = store.install(archive)
                val payload = installed.directory.toPath().resolve("content/data.txt")
                Files.delete(payload)
                Files.createSymbolicLink(payload, outside)
                val error = runCatching { store.listInstalled() }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_STORE_INVALID")
                check(Files.readString(outside) == "outside")
            } finally {
                archive.delete()
                root.deleteRecursively()
                Files.deleteIfExists(outside)
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
