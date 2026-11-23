package org.neocraft.neomod.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.UUID

/**
 * Atomic metadata-only persistence for NeoMod profiles.
 *
 * A profile is validated against a previously resolved installed-package plan
 * before it is written or returned from load(). This store never activates or
 * executes package content.
 */
class NcpProfileStore(storageDirectory: File) {
    data class ProfileSummary(
        val profileId: String,
        val displayName: String,
        val gameBuild: NcpGameBuildIdentity,
        val neoModApiVersion: String,
        val requestedPackageCount: Int,
        val resolvedPackageCount: Int,
    )

    private val root = storageDirectory.toPath().toAbsolutePath().normalize().resolve("profiles")
    private val lock = LOCKS.computeIfAbsent(root) { Any() }

    init {
        synchronized(lock) { ensureRoot() }
    }

    fun save(snapshot: NcpProfileSnapshot, plan: NcpInstalledPackagePlan): NcpProfileSnapshot =
        synchronized(lock) {
            NcpProfileSnapshotValidator.validate(snapshot, plan)
            ensureRoot()
            val destination = profilePath(snapshot.profileId)
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(destination)) {
                fail("NCP_PROFILE_STORE_INVALID", "profile destination must not be a symbolic link")
            }
            val temp = root.resolve(".tmp-" + UUID.randomUUID())
            var committed = false
            try {
                val bytes = encode(snapshot).toByteArray(StandardCharsets.UTF_8)
                if (bytes.size > MAX_PROFILE_BYTES) {
                    fail("NCP_PROFILE_STORE_LIMIT", "encoded profile exceeds size limit")
                }
                FileOutputStream(temp.toFile()).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                committed = true
                snapshot
            } catch (error: NcpValidationException) {
                throw error
            } catch (error: AtomicMoveNotSupportedException) {
                fail("NCP_PROFILE_STORE_IO", "filesystem does not support atomic profile replacement")
            } catch (error: IOException) {
                throw NcpValidationException("NCP_PROFILE_STORE_IO", "profile save failed: " + (error.message ?: "I/O error"))
            } finally {
                if (!committed) Files.deleteIfExists(temp)
            }
        }

    fun load(profileId: String, plan: NcpInstalledPackagePlan): NcpProfileSnapshot =
        synchronized(lock) {
            val file = profilePath(profileId)
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                fail("NCP_PROFILE_NOT_FOUND", "profile was not found")
            }
            val snapshot = decode(readLimited(file))
            if (snapshot.profileId != profileId) {
                fail("NCP_PROFILE_STORE_INVALID", "profile ID does not match its file name")
            }
            NcpProfileSnapshotValidator.validate(snapshot, plan)
        }

    fun listProfiles(): List<ProfileSummary> = synchronized(lock) {
        ensureRoot()
        val result = ArrayList<ProfileSummary>()
        Files.newDirectoryStream(root).use { entries ->
            for (entry in entries) {
                val name = entry.fileName.toString()
                if (name.startsWith(".")) continue
                if (!name.endsWith(PROFILE_SUFFIX)) continue
                if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    fail("NCP_PROFILE_STORE_INVALID", "profile entry is not a regular file")
                }
                val snapshot = decode(readLimited(entry))
                if (entry.fileName.toString() != snapshot.profileId + PROFILE_SUFFIX) {
                    fail("NCP_PROFILE_STORE_INVALID", "profile ID does not match its file name")
                }
                NcpProfileSnapshotValidator.validateStructure(snapshot)
                result += ProfileSummary(
                    snapshot.profileId,
                    snapshot.displayName,
                    snapshot.gameBuild,
                    snapshot.neoModApiVersion,
                    snapshot.requestedPackages.size,
                    snapshot.resolvedPackages.size,
                )
            }
        }
        result.sortedBy { it.profileId }
    }

    fun delete(profileId: String) = synchronized(lock) {
        val file = profilePath(profileId)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            fail("NCP_PROFILE_NOT_FOUND", "profile was not found")
        }
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            fail("NCP_PROFILE_STORE_INVALID", "profile entry is not a regular file")
        }
        Files.delete(file)
    }

    private fun ensureRoot() {
        val parent = root.parent ?: fail("NCP_PROFILE_STORE_INVALID", "profile root has no parent")
        Files.createDirectories(parent)
        if (Files.isSymbolicLink(parent)) {
            fail("NCP_PROFILE_STORE_INVALID", "profile parent must not be a symbolic link")
        }
        Files.createDirectories(root)
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            fail("NCP_PROFILE_STORE_INVALID", "profile root is not a regular directory")
        }
    }

    private fun profilePath(profileId: String): Path {
        if (!PROFILE_ID.matches(profileId)) {
            fail("NCP_PROFILE_INVALID", "profile ID is invalid")
        }
        val destination = root.resolve(profileId + PROFILE_SUFFIX).normalize()
        if (!destination.startsWith(root) || destination == root) {
            fail("NCP_PATH_UNSAFE", "profile destination is outside the profile store")
        }
        return destination
    }

    private fun readLimited(path: Path): String {
        val bytes = try {
            val size = Files.size(path)
            if (size > MAX_PROFILE_BYTES) {
                fail("NCP_PROFILE_STORE_LIMIT", "profile file exceeds size limit")
            }
            Files.readAllBytes(path).also {
                if (it.size > MAX_PROFILE_BYTES) {
                    fail("NCP_PROFILE_STORE_LIMIT", "profile file exceeds size limit")
                }
            }
        } catch (error: IOException) {
            throw NcpValidationException("NCP_PROFILE_STORE_IO", "profile read failed: " + (error.message ?: "I/O error"))
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            fail("NCP_PROFILE_STORE_INVALID", "profile is not valid UTF-8")
        }
    }

    private fun encode(snapshot: NcpProfileSnapshot): String {
        val lines = ArrayList<String>()
        lines += "NEOCRAFT_PROFILE\t1"
        lines += "id\t" + encodeString(snapshot.profileId)
        lines += "name\t" + encodeString(snapshot.displayName)
        lines += "game\t" + encodeString(snapshot.gameBuild.packageName) + "\t" +
            snapshot.gameBuild.versionCode + "\t" + snapshot.gameBuild.apkSha256
        lines += "api\t" + encodeString(snapshot.neoModApiVersion)
        lines += "requested\t" + snapshot.requestedPackages.size
        snapshot.requestedPackages.forEach { lines += "request\t" + encodeString(it.id) + "\t" + encodeString(it.versionRange) }
        lines += "resolved\t" + snapshot.resolvedPackages.size
        snapshot.resolvedPackages.forEach { lines += "package\t" + encodeString(it.id) + "\t" + encodeString(it.version) + "\t" + it.order }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun decode(text: String): NcpProfileSnapshot {
        if (text.isEmpty() || text.startsWith('\uFEFF') || '\r' in text) {
            fail("NCP_PROFILE_STORE_INVALID", "profile text is empty or contains unsupported line endings")
        }
        val lines = text.split('\n').let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        if (lines.size > MAX_PROFILE_LINES || lines.firstOrNull() != "NEOCRAFT_PROFILE\t1") {
            fail("NCP_PROFILE_STORE_INVALID", "profile header or line count is invalid")
        }
        var cursor = 1
        fun fields(label: String, count: Int): List<String> {
            if (cursor >= lines.size) fail("NCP_PROFILE_STORE_INVALID", "missing " + label + " record")
            val values = lines[cursor++].split('\t')
            if (values.size != count || values.firstOrNull() != label) {
                fail("NCP_PROFILE_STORE_INVALID", "invalid " + label + " record")
            }
            return values
        }
        fun number(value: String, label: String): Long =
            value.toLongOrNull() ?: fail("NCP_PROFILE_STORE_INVALID", label + " is not an integer")
        val profileId = decodeString(fields("id", 2)[1])
        val displayName = decodeString(fields("name", 2)[1])
        val game = fields("game", 4)
        val gameBuild = NcpGameBuildIdentity(
            decodeString(game[1]),
            number(game[2], "game versionCode"),
            game[3],
        )
        val apiVersion = decodeString(fields("api", 2)[1])
        val requestedCount = number(fields("requested", 2)[1], "requested count")
        if (requestedCount !in 1..MAX_PACKAGES.toLong()) {
            fail("NCP_PROFILE_STORE_INVALID", "requested count is out of bounds")
        }
        val requests = (0 until requestedCount.toInt()).map {
            val record = fields("request", 3)
            NcpDependency(decodeString(record[1]), decodeString(record[2]))
        }
        val resolvedCount = number(fields("resolved", 2)[1], "resolved count")
        if (resolvedCount !in 0..MAX_PACKAGES.toLong()) {
            fail("NCP_PROFILE_STORE_INVALID", "resolved count is out of bounds")
        }
        val resolved = (0 until resolvedCount.toInt()).map {
            val record = fields("package", 4)
            NcpProfilePackage(decodeString(record[1]), decodeString(record[2]), number(record[3], "package order").toInt())
        }
        if (cursor != lines.size) fail("NCP_PROFILE_STORE_INVALID", "profile contains trailing records")
        return NcpProfileSnapshot(profileId, displayName, gameBuild, apiVersion, requests, resolved)
    }

    private fun encodeString(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeString(value: String): String {
        val bytes = try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            fail("NCP_PROFILE_STORE_INVALID", "profile contains invalid base64 text")
        }
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            fail("NCP_PROFILE_STORE_INVALID", "profile contains invalid UTF-8 text")
        }
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)

    companion object {
        private const val PROFILE_SUFFIX = ".ncp-profile"
        private const val MAX_PROFILE_BYTES = 64 * 1024
        private const val MAX_PROFILE_LINES = 1024
        private const val MAX_PACKAGES = 256
        private val PROFILE_ID = Regex("^[a-z][a-z0-9_-]{1,47}$")
        private val LOCKS = java.util.concurrent.ConcurrentHashMap<Path, Any>()
    }
}
