package org.neocraft.neomod.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Validates ZIP central-directory metadata not exposed by java.util.zip.ZipEntry.
 *
 * This deliberately rejects ZIP64 and multi-disk archives for NCP v1. No entries are extracted.
 */
internal object NcpZipMetadataGuard {
    fun validate(file: File, zipEntryNames: List<String>) {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length < 22) fail("NCP_ARCHIVE_INVALID", "file is too small to be a ZIP archive")

            val searchStart = (length - 22 - 65_535).coerceAtLeast(0)
            var eocd = -1L
            var position = length - 22
            while (position >= searchStart) {
                if (readU32(raf, position) == EOCD_SIGNATURE) {
                    val commentLength = readU16(raf, position + 20)
                    if (position + 22L + commentLength == length) {
                        eocd = position
                        break
                    }
                }
                position--
            }
            if (eocd < 0) fail("NCP_ARCHIVE_INVALID", "ZIP end-of-central-directory record not found")

            val diskNumber = readU16(raf, eocd + 4)
            val centralDisk = readU16(raf, eocd + 6)
            val entriesOnDisk = readU16(raf, eocd + 8)
            val totalEntries = readU16(raf, eocd + 10)
            val centralSize = readU32(raf, eocd + 12)
            val centralOffset = readU32(raf, eocd + 16)

            if (diskNumber != 0 || centralDisk != 0 || entriesOnDisk != totalEntries) {
                fail("NCP_ARCHIVE_INVALID", "multi-disk ZIP archives are not supported")
            }
            if (totalEntries == 0xFFFF || centralSize == 0xFFFF_FFFFL || centralOffset == 0xFFFF_FFFFL) {
                fail("NCP_ARCHIVE_INVALID", "ZIP64 archives are not supported by NCP v1")
            }
            if (totalEntries != zipEntryNames.size) {
                fail("NCP_ARCHIVE_INVALID", "ZIP entry count differs between central directory and ZIP reader")
            }

            val centralEnd = centralOffset + centralSize
            if (centralOffset < 0 || centralEnd < centralOffset || centralEnd > eocd || centralEnd > length) {
                fail("NCP_ARCHIVE_INVALID", "invalid ZIP central directory bounds")
            }

            var cursor = centralOffset
            repeat(totalEntries) { index ->
                if (cursor + 46 > centralEnd) {
                    fail("NCP_ARCHIVE_INVALID", "truncated ZIP central directory")
                }
                if (readU32(raf, cursor) != CENTRAL_SIGNATURE) {
                    fail("NCP_ARCHIVE_INVALID", "invalid ZIP central directory entry")
                }

                val madeBy = readU16(raf, cursor + 4)
                val platform = madeBy ushr 8
                val flags = readU16(raf, cursor + 8)
                if (flags and ENCRYPTION_FLAGS != 0) {
                    fail("NCP_ARCHIVE_INVALID", "encrypted ZIP entries are not allowed")
                }
                val method = readU16(raf, cursor + 10)
                if (method != METHOD_STORED && method != METHOD_DEFLATED) {
                    fail("NCP_ARCHIVE_INVALID", "unsupported ZIP compression method: $method")
                }

                val externalAttributes = readU32(raf, cursor + 38)
                val nameLength = readU16(raf, cursor + 28)
                val extraLength = readU16(raf, cursor + 30)
                val commentLength = readU16(raf, cursor + 32)
                if (nameLength == 0) fail("NCP_ARCHIVE_INVALID", "ZIP entry has an empty name")

                val next = cursor + 46L + nameLength + extraLength + commentLength
                if (next > centralEnd || next < cursor) {
                    fail("NCP_ARCHIVE_INVALID", "ZIP central directory entry exceeds declared bounds")
                }

                val nameBytes = ByteArray(nameLength)
                raf.seek(cursor + 46)
                raf.readFully(nameBytes)
                val centralName = decodeUtf8Strict(nameBytes)
                if (centralName != zipEntryNames[index]) {
                    fail("NCP_ARCHIVE_INVALID", "ZIP entry name differs between central directory and ZIP reader")
                }

                if (platform == UNIX_PLATFORM || platform == MAC_OSX_PLATFORM) {
                    val mode = ((externalAttributes ushr 16) and 0xFFFF).toInt()
                    val type = mode and UNIX_FILE_TYPE_MASK
                    if (type == UNIX_SYMLINK || (type != 0 && type != UNIX_REGULAR && type != UNIX_DIRECTORY)) {
                        fail("NCP_PATH_UNSAFE", "symbolic links and special files are not allowed in NCP packages")
                    }
                    if (type == UNIX_DIRECTORY && !centralName.endsWith('/')) {
                        fail("NCP_ARCHIVE_INVALID", "Unix directory entry must end with '/': $centralName")
                    }
                    if (type == UNIX_REGULAR && centralName.endsWith('/')) {
                        fail("NCP_ARCHIVE_INVALID", "regular file entry must not end with '/': $centralName")
                    }
                }

                cursor = next
            }
            if (cursor != centralEnd) {
                fail("NCP_ARCHIVE_INVALID", "ZIP central directory size mismatch")
            }
        }
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        fail("NCP_ARCHIVE_INVALID", "ZIP entry names must be valid UTF-8")
    }

    private fun readU16(raf: RandomAccessFile, offset: Long): Int {
        raf.seek(offset)
        val a = raf.read()
        val b = raf.read()
        if (a < 0 || b < 0) fail("NCP_ARCHIVE_INVALID", "truncated ZIP metadata")
        return a or (b shl 8)
    }

    private fun readU32(raf: RandomAccessFile, offset: Long): Long {
        raf.seek(offset)
        val a = raf.read()
        val b = raf.read()
        val c = raf.read()
        val d = raf.read()
        if (a < 0 || b < 0 || c < 0 || d < 0) {
            fail("NCP_ARCHIVE_INVALID", "truncated ZIP metadata")
        }
        return (a.toLong() and 0xFF) or
            ((b.toLong() and 0xFF) shl 8) or
            ((c.toLong() and 0xFF) shl 16) or
            ((d.toLong() and 0xFF) shl 24)
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)

    private const val EOCD_SIGNATURE = 0x06054B50L
    private const val CENTRAL_SIGNATURE = 0x02014B50L
    private const val UNIX_PLATFORM = 3
    private const val MAC_OSX_PLATFORM = 19
    private const val UNIX_FILE_TYPE_MASK = 0xF000
    private const val UNIX_REGULAR = 0x8000
    private const val UNIX_DIRECTORY = 0x4000
    private const val UNIX_SYMLINK = 0xA000
    private const val ENCRYPTION_FLAGS = 0x2041
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8
}
