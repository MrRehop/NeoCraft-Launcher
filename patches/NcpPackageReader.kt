package org.neocraft.neomod.core

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.ZipException
import java.util.zip.ZipFile

data class NcpVerifiedFile(val path: String, val size: Long, val sha256: String)

data class NcpPackageReport(
    val manifest: NcpManifest,
    val verifiedFiles: List<NcpVerifiedFile>,
    val archiveBytes: Long,
)

/**
 * Read-only NCP v1 archive validator. It never extracts files or executes package content.
 * ZIP metadata is treated as untrusted; actual uncompressed byte counts and digests are streamed.
 */
object NcpPackageReader {
    private const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024
    private const val MAX_ENTRIES = 2048
    private const val MAX_MANIFEST_BYTES = 256L * 1024
    private const val MAX_FILE_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    private const val MAX_RATIO = 100L
    private const val BUFFER_SIZE = 16 * 1024

    fun validate(file: File): NcpPackageReport {
        if (!file.isFile || !file.canRead()) fail("NCP_ARCHIVE_INVALID", "package must be a readable regular file")
        val archiveBytes = file.length()
        if (archiveBytes <= 0 || archiveBytes > MAX_ARCHIVE_BYTES) {
            fail("NCP_LIMIT_EXCEEDED", "archive size must be between 1 and $MAX_ARCHIVE_BYTES bytes")
        }

        try {
            ZipFile(file).use { zip ->
                val entries = Collections.list(zip.entries())
                if (entries.size > MAX_ENTRIES) fail("NCP_LIMIT_EXCEEDED", "archive contains more than $MAX_ENTRIES entries")

                val seen = HashSet<String>()
                val files = LinkedHashMap<String, java.util.zip.ZipEntry>()
                var manifestEntry: java.util.zip.ZipEntry? = null

                for (entry in entries) {
                    val name = validateEntryName(entry.name, entry.isDirectory)
                    if (!seen.add(name)) fail("NCP_DUPLICATE_ENTRY", "duplicate archive entry: $name")
                    if (entry.isDirectory) continue
                    if (name == "manifest.json") {
                        if (manifestEntry != null) fail("NCP_DUPLICATE_ENTRY", "manifest.json occurs more than once")
                        manifestEntry = entry
                    } else {
                        if (!name.startsWith("content/")) {
                            fail("NCP_PATH_UNSAFE", "unexpected top-level archive entry: $name")
                        }
                        val size = entry.size
                        val compressed = entry.compressedSize
                        if (size < 0 || compressed < 0) fail("NCP_ARCHIVE_INVALID", "ZIP entry has unknown size: $name")
                        if (size > MAX_FILE_BYTES) fail("NCP_LIMIT_EXCEEDED", "entry exceeds 32 MiB: $name")
                        if (size > 0 && (compressed == 0L || size > compressed * MAX_RATIO)) {
                            fail("NCP_LIMIT_EXCEEDED", "entry compression ratio exceeds 100:1: $name")
                        }
                        files[name] = entry
                    }
                }

                val manifestZipEntry = manifestEntry ?: fail("NCP_MANIFEST_MISSING", "root manifest.json is required")
                if (manifestZipEntry.size < 0 || manifestZipEntry.size > MAX_MANIFEST_BYTES) {
                    fail("NCP_LIMIT_EXCEEDED", "manifest.json exceeds 256 KiB")
                }
                val manifestBytes = readBounded(zip.getInputStream(manifestZipEntry), MAX_MANIFEST_BYTES, "manifest.json")
                val manifest = try {
                    NcpManifestDecoder.decode(manifestBytes)
                } catch (error: NcpValidationException) {
                    throw error
                } catch (error: Exception) {
                    fail("NCP_MANIFEST_INVALID", error.message ?: "manifest decoding failed")
                }

                val declared = manifest.files.associateBy { it.path }
                if (declared.size != manifest.files.size || declared.keys != files.keys) {
                    fail("NCP_FILE_INVENTORY_MISMATCH", "archive payload entries do not exactly match manifest.files")
                }

                var totalBytes = 0L
                val verified = ArrayList<NcpVerifiedFile>(files.size)
                for ((path, entry) in files) {
                    val record = declared[path] ?: fail("NCP_FILE_INVENTORY_MISMATCH", "unlisted file: $path")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            count += read
                            totalBytes += read
                            if (count > MAX_FILE_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                fail("NCP_LIMIT_EXCEEDED", "actual uncompressed data exceeds package limits")
                            }
                            digest.update(buffer, 0, read)
                        }
                    }
                    if (count != record.size) {
                        fail("NCP_FILE_INVENTORY_MISMATCH", "uncompressed size mismatch for $path")
                    }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (hash != record.sha256) fail("NCP_HASH_MISMATCH", "SHA-256 mismatch for $path")
                    verified += NcpVerifiedFile(path, count, hash)
                }
                return NcpPackageReport(manifest, verified, archiveBytes)
            }
        } catch (error: NcpValidationException) {
            throw error
        } catch (error: ZipException) {
            fail("NCP_ARCHIVE_INVALID", error.message ?: "invalid or unsupported ZIP archive")
        } catch (error: IOException) {
            fail("NCP_ARCHIVE_INVALID", error.message ?: "cannot read ZIP archive")
        } catch (error: SecurityException) {
            fail("NCP_ARCHIVE_INVALID", error.message ?: "archive access denied")
        }
    }

    private fun validateEntryName(raw: String, directory: Boolean): String {
        if (raw.isEmpty() || '\u0000' in raw || '\\' in raw || raw.startsWith("/") ||
            Regex("^[A-Za-z]:").containsMatchIn(raw)) {
            fail("NCP_PATH_UNSAFE", "unsafe ZIP entry path")
        }
        val name = if (directory) raw.removeSuffix("/") else raw
        val segments = name.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("NCP_PATH_UNSAFE", "non-canonical ZIP entry path: $raw")
        }
        if (name != "manifest.json" && name != "content" && !name.startsWith("content/")) {
            fail("NCP_PATH_UNSAFE", "entry is outside the NCP v1 root layout: $raw")
        }
        return name
    }

    private fun readBounded(input: java.io.InputStream, limit: Long, label: String): ByteArray {
        input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var count = 0L
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                count += read
                if (count > limit) fail("NCP_LIMIT_EXCEEDED", "$label exceeds $limit bytes")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)
}
