package org.neocraft.neomod.core

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Transactional, data-only storage for validated NCP v1 packages.
 *
 * Packages are installed into immutable ID/version directories. This class does not
 * execute package content, activate it in Minecraft, or claim runtime isolation.
 */
class NcpPackageStore(storageDirectory: File) {
    data class InstalledPackage(
        val id: String,
        val version: String,
        val name: String,
        val directory: File,
        val manifest: NcpManifest,
        val files: List<NcpVerifiedFile>,
    )

    private val root = storageDirectory.toPath().toAbsolutePath().normalize()
    private val packagesRoot = root.resolve("packages")
    private val storeLock = ROOT_LOCKS.computeIfAbsent(root) { Any() }

    init {
        synchronized(storeLock) {
            ensureDirectory(root)
            ensureDirectory(packagesRoot)
        }
    }

    fun install(packageFile: File): InstalledPackage = synchronized(storeLock) {
        installLocked(packageFile)
    }

    private fun installLocked(packageFile: File): InstalledPackage {
        val source = packageFile.toPath().toAbsolutePath().normalize()
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) {
            fail("NCP_ARCHIVE_INVALID", "source must be a regular non-symbolic-link file")
        }

        val sourceDigest = sha256(source)
        val report = NcpPackageReader.validate(packageFile)
        if (sha256(source) != sourceDigest) {
            fail("NCP_ARCHIVE_INVALID", "source package changed during validation")
        }

        val idDirectory = packagesRoot.resolve(report.manifest.id)
        val destination = idDirectory.resolve(report.manifest.version).normalize()
        if (!destination.startsWith(packagesRoot)) {
            fail("NCP_PATH_UNSAFE", "resolved package destination is outside the store")
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            fail("NCP_PACKAGE_ALREADY_INSTALLED", "package version is already installed")
        }

        ensureDirectory(idDirectory)
        val staging = packagesRoot.resolve(".staging-" + UUID.randomUUID())
        ensureDirectory(staging)
        var committed = false
        try {
            extractVerified(source, staging, report)
            if (sha256(source) != sourceDigest) {
                fail("NCP_ARCHIVE_INVALID", "source package changed during installation")
            }
            val staged = inspectDirectory(staging, report.manifest.id, report.manifest.version)
            if (staged.manifest != report.manifest || staged.files != report.verifiedFiles) {
                fail("NCP_FILE_INVENTORY_MISMATCH", "staged package differs from validated archive")
            }
            moveAtomically(staging, destination)
            committed = true
            return staged.copy(directory = destination.toFile())
        } finally {
            if (!committed) deleteTree(staging)
        }
    }

    fun listInstalled(): List<InstalledPackage> = synchronized(storeLock) {
        listInstalledLocked()
    }

    private fun listInstalledLocked(): List<InstalledPackage> {
        if (!Files.exists(packagesRoot, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        if (Files.isSymbolicLink(packagesRoot) || !Files.isDirectory(packagesRoot, LinkOption.NOFOLLOW_LINKS)) {
            fail("NCP_STORE_INVALID", "package store root is not a regular directory")
        }
        val result = ArrayList<InstalledPackage>()
        Files.newDirectoryStream(packagesRoot).use { idDirectories ->
            for (idDirectory in idDirectories) {
                val id = idDirectory.fileName.toString()
                if (id.startsWith(".")) continue
                if (Files.isSymbolicLink(idDirectory)) fail("NCP_STORE_INVALID", "symbolic link in package store")
                if (!Files.isDirectory(idDirectory, LinkOption.NOFOLLOW_LINKS)) continue
                Files.newDirectoryStream(idDirectory).use { versions ->
                    for (versionDirectory in versions) {
                        if (Files.isSymbolicLink(versionDirectory)) {
                            fail("NCP_STORE_INVALID", "symbolic link in package version store")
                        }
                        if (!Files.isDirectory(versionDirectory, LinkOption.NOFOLLOW_LINKS)) continue
                        result += inspectDirectory(versionDirectory, id, versionDirectory.fileName.toString())
                    }
                }
            }
        }
        return result.sortedWith(compareBy<InstalledPackage> { it.id }.thenBy { it.version })
    }

    fun uninstall(id: String, version: String) = synchronized(storeLock) {
        uninstallLocked(id, version)
    }

    private fun uninstallLocked(id: String, version: String) {
        if (!PACKAGE_ID.matches(id) || !STABLE_VERSION.matches(version)) {
            fail("NCP_ID_INVALID", "invalid package ID or stable version")
        }
        val target = packagesRoot.resolve(id).resolve(version).normalize()
        if (!target.startsWith(packagesRoot) ||
            !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(target)) {
            fail("NCP_PACKAGE_NOT_FOUND", "installed package was not found")
        }
        inspectDirectory(target, id, version)
        val trash = packagesRoot.resolve(".trash-" + UUID.randomUUID())
        moveAtomically(target, trash)
        try {
            deleteTree(trash)
            val idDirectory = packagesRoot.resolve(id)
            if (Files.isDirectory(idDirectory, LinkOption.NOFOLLOW_LINKS)) {
                Files.newDirectoryStream(idDirectory).use { children ->
                    if (!children.iterator().hasNext()) Files.deleteIfExists(idDirectory)
                }
            }
        } catch (error: Throwable) {
            throw IOException("package was detached but cleanup failed: " + trash, error)
        }
    }

    private fun extractVerified(source: Path, staging: Path, report: NcpPackageReport) {
        val expected = report.verifiedFiles.associateBy { it.path }
        val extracted = linkedSetOf<String>()
        var totalBytes = 0L
        ZipFile(source.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || entry.name.endsWith("/")) continue
                val relative = entry.name
                if (relative != "manifest.json" && relative !in expected) {
                    fail("NCP_FILE_INVENTORY_MISMATCH", "unexpected archive file during extraction: " + relative)
                }
                if (!extracted.add(relative)) {
                    fail("NCP_DUPLICATE_ENTRY", "duplicate archive file during extraction: " + relative)
                }
                val output = staging.resolve(relative).normalize()
                if (!output.startsWith(staging) || output == staging) {
                    fail("NCP_PATH_UNSAFE", "unsafe extraction destination")
                }
                Files.createDirectories(output.parent)
                val digest = MessageDigest.getInstance("SHA-256")
                var entryBytes = 0L
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            entryBytes += count
                            totalBytes += count
                            if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                fail("NCP_LIMIT_EXCEEDED", "actual extracted bytes exceed NCP limits")
                            }
                            digest.update(buffer, 0, count)
                            out.write(buffer, 0, count)
                        }
                    }
                }
                if (relative == "manifest.json") {
                    if (entryBytes > MAX_MANIFEST_BYTES) {
                        fail("NCP_LIMIT_EXCEEDED", "manifest exceeds size limit during extraction")
                    }
                } else {
                    val record = expected[relative]
                        ?: fail("NCP_FILE_INVENTORY_MISMATCH", "unlisted archive payload")
                    val hash = digest.digest().toHex()
                    if (entryBytes != record.size || hash != record.sha256) {
                        fail("NCP_HASH_MISMATCH", "payload changed during extraction: " + relative)
                    }
                }
            }
        }
        if (extracted != (expected.keys + "manifest.json")) {
            fail("NCP_FILE_INVENTORY_MISMATCH", "archive entries changed during extraction")
        }
    }

    private fun inspectDirectory(directory: Path, expectedId: String, expectedVersion: String): InstalledPackage {
        val normalized = directory.toAbsolutePath().normalize()
        if (!normalized.startsWith(packagesRoot) && normalized != packagesRoot) {
            fail("NCP_STORE_INVALID", "package directory escapes store root")
        }
        if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            fail("NCP_STORE_INVALID", "package directory is not a regular directory")
        }
        val manifestPath = normalized.resolve("manifest.json")
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(manifestPath)) {
            fail("NCP_STORE_INVALID", "installed package has no regular root manifest")
        }
        if (Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
            fail("NCP_LIMIT_EXCEEDED", "installed manifest exceeds size limit")
        }
        val manifest = NcpManifestDecoder.decode(Files.readAllBytes(manifestPath))
        if (manifest.id != expectedId || manifest.version != expectedVersion) {
            fail("NCP_STORE_INVALID", "manifest identity does not match storage path")
        }

        val actual = linkedMapOf<String, Path>()
        Files.walkFileTree(normalized, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Files.isSymbolicLink(dir) || !attrs.isDirectory) {
                    fail("NCP_STORE_INVALID", "unsafe directory in installed package")
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (Files.isSymbolicLink(file) || !attrs.isRegularFile) {
                    fail("NCP_STORE_INVALID", "non-regular file in installed package")
                }
                actual[normalized.relativize(file).joinToString("/") { it.toString() }] = file
                return FileVisitResult.CONTINUE
            }
        })
        val expectedPaths = manifest.files.map { it.path }.toSet() + "manifest.json"
        if (actual.keys != expectedPaths) {
            fail("NCP_FILE_INVENTORY_MISMATCH", "installed files differ from manifest inventory")
        }
        val verified = manifest.files.map { record ->
            val file = actual[record.path]
                ?: fail("NCP_FILE_INVENTORY_MISMATCH", "missing installed payload: " + record.path)
            val size = Files.size(file)
            val hash = sha256(file)
            if (size != record.size) fail("NCP_FILE_INVENTORY_MISMATCH", "installed size mismatch: " + record.path)
            if (hash != record.sha256) fail("NCP_HASH_MISMATCH", "installed digest mismatch: " + record.path)
            NcpVerifiedFile(record.path, size, hash)
        }
        return InstalledPackage(manifest.id, manifest.version, manifest.name, normalized.toFile(), manifest, verified)
    }

    private fun ensureDirectory(path: Path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                fail("NCP_STORE_INVALID", "storage path is not a regular directory: " + path)
            }
        } else {
            Files.createDirectories(path)
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                fail("NCP_STORE_INVALID", "could not create storage directory: " + path)
            }
        }
    }

    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            fail("NCP_STORE_ATOMIC_MOVE_UNAVAILABLE", "filesystem does not support atomic package commit")
        }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        if (Files.isSymbolicLink(path)) {
            Files.deleteIfExists(path)
            return
        }
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)

    companion object {
        // Serialize mutations and inspection for the same normalized store root,
        // even when callers create multiple NcpPackageStore instances.
        private val ROOT_LOCKS = ConcurrentHashMap<Path, Any>()
        private val PACKAGE_ID = Regex("^[a-z][a-z0-9_]{1,47}$")
        private val STABLE_VERSION = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_MANIFEST_BYTES = 256L * 1024
        private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    }
}
