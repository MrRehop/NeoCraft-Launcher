package org.neocraft.neomod.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Validates ZIP metadata not exposed by java.util.zip.ZipEntry.
 * NCP v1 deliberately rejects ZIP64 and multi-disk archives. No entries are extracted.
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
            val localRanges = ArrayList<LongRange>(totalEntries)
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
                if ((flags and ENCRYPTION_FLAGS) != 0) {
                    fail("NCP_ARCHIVE_INVALID", "encrypted ZIP entries are not allowed")
                }
                val method = readU16(raf, cursor + 10)
                if (method != METHOD_STORED && method != METHOD_DEFLATED) {
                    fail("NCP_ARCHIVE_INVALID", "unsupported ZIP compression method: $method")
                }

                val crc = readU32(raf, cursor + 16)
                val compressedSize = readU32(raf, cursor + 20)
                val uncompressedSize = readU32(raf, cursor + 24)
                val nameLength = readU16(raf, cursor + 28)
                val extraLength = readU16(raf, cursor + 30)
                val commentLength = readU16(raf, cursor + 32)
                val diskStart = readU16(raf, cursor + 34)
                val externalAttributes = readU32(raf, cursor + 38)
                val localHeaderOffset = readU32(raf, cursor + 42)

                if (nameLength == 0) fail("NCP_ARCHIVE_INVALID", "ZIP entry has an empty name")
                if (diskStart != 0) fail("NCP_ARCHIVE_INVALID", "multi-disk ZIP entry is not allowed")
                if (compressedSize == UINT32_MAX || uncompressedSize == UINT32_MAX ||
                    localHeaderOffset == UINT32_MAX) {
                    fail("NCP_ARCHIVE_INVALID", "ZIP64 entry metadata is not supported by NCP v1")
                }

                val next = cursor + 46L + nameLength + extraLength + commentLength
                if (next > centralEnd || next < cursor) {
                    fail("NCP_ARCHIVE_INVALID", "ZIP central directory entry exceeds declared bounds")
                }

                val nameBytes = readBytes(raf, cursor + 46, nameLength)
                val centralName = decodeUtf8Strict(nameBytes)
                if (centralName != zipEntryNames[index]) {
                    fail("NCP_ARCHIVE_INVALID", "ZIP entry name differs between central directory and ZIP reader")
                }
                validateExtraFields(raf, cursor + 46L + nameLength, extraLength, "central directory")

                validateLocalHeader(
                    raf = raf,
                    archiveLength = length,
                    centralOffset = centralOffset,
                    localOffset = localHeaderOffset,
                    centralNameBytes = nameBytes,
                    flags = flags,
                    method = method,
                    crc = crc,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                ).also(localRanges::add)

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

            val orderedRanges = localRanges.sortedBy { it.first }
            for (index in 1 until orderedRanges.size) {
                if (orderedRanges[index].first <= orderedRanges[index - 1].last) {
                    fail("NCP_ARCHIVE_INVALID", "ZIP local file records overlap")
                }
            }
        }
    }

    private fun validateLocalHeader(
        raf: RandomAccessFile,
        archiveLength: Long,
        centralOffset: Long,
        localOffset: Long,
        centralNameBytes: ByteArray,
        flags: Int,
        method: Int,
        crc: Long,
        compressedSize: Long,
        uncompressedSize: Long,
    ): LongRange {
        if (localOffset < 0 || localOffset + 30 > centralOffset ||
            readU32(raf, localOffset) != LOCAL_SIGNATURE) {
            fail("NCP_ARCHIVE_INVALID", "invalid ZIP local file header")
        }

        val localFlags = readU16(raf, localOffset + 6)
        val localMethod = readU16(raf, localOffset + 8)
        if (localFlags != flags || localMethod != method) {
            fail("NCP_ARCHIVE_INVALID", "local and central ZIP flags or compression methods differ")
        }

        val localCrc = readU32(raf, localOffset + 14)
        val localCompressedSize = readU32(raf, localOffset + 18)
        val localUncompressedSize = readU32(raf, localOffset + 22)
        if (localCompressedSize == UINT32_MAX || localUncompressedSize == UINT32_MAX) {
            fail("NCP_ARCHIVE_INVALID", "ZIP64 local entry metadata is not supported by NCP v1")
        }

        val localNameLength = readU16(raf, localOffset + 26)
        val localExtraLength = readU16(raf, localOffset + 28)
        if (localNameLength != centralNameBytes.size) {
            fail("NCP_ARCHIVE_INVALID", "local and central ZIP entry names differ")
        }
        val localNameOffset = localOffset + 30
        val localExtraOffset = localNameOffset + localNameLength
        val dataOffset = localExtraOffset + localExtraLength
        if (dataOffset < localOffset || dataOffset > centralOffset || dataOffset > archiveLength) {
            fail("NCP_ARCHIVE_INVALID", "ZIP local header exceeds archive bounds")
        }
        val localName = readBytes(raf, localNameOffset, localNameLength)
        if (!localName.contentEquals(centralNameBytes)) {
            fail("NCP_ARCHIVE_INVALID", "local and central ZIP entry names differ")
        }
        validateExtraFields(raf, localExtraOffset, localExtraLength, "local header")

        val hasDataDescriptor = (flags and DATA_DESCRIPTOR_FLAG) != 0
        if (!hasDataDescriptor) {
            if (localCrc != crc || localCompressedSize != compressedSize ||
                localUncompressedSize != uncompressedSize) {
                fail("NCP_ARCHIVE_INVALID", "local and central ZIP sizes or CRC differ")
            }
        } else {
            if ((localCrc != 0L && localCrc != crc) ||
                (localCompressedSize != 0L && localCompressedSize != compressedSize) ||
                (localUncompressedSize != 0L && localUncompressedSize != uncompressedSize)) {
                fail("NCP_ARCHIVE_INVALID", "local ZIP descriptor placeholders are inconsistent")
            }
        }

        val dataEnd = dataOffset + compressedSize
        if (dataEnd < dataOffset || dataEnd > centralOffset || dataEnd > archiveLength) {
            fail("NCP_ARCHIVE_INVALID", "compressed ZIP payload exceeds archive bounds")
        }
        return localOffset..(dataEnd - 1)
    }

    private fun validateExtraFields(
        raf: RandomAccessFile,
        offset: Long,
        length: Int,
        section: String,
    ) {
        var cursor = offset
        val end = offset + length
        if (end < offset) fail("NCP_ARCHIVE_INVALID", "invalid ZIP extra-field bounds")
        while (cursor < end) {
            if (cursor + 4 > end) fail("NCP_ARCHIVE_INVALID", "truncated ZIP extra field in $section")
            val id = readU16(raf, cursor)
            val dataLength = readU16(raf, cursor + 2)
            val next = cursor + 4L + dataLength
            if (next > end || next < cursor) {
                fail("NCP_ARCHIVE_INVALID", "ZIP extra field exceeds $section bounds")
            }
            if (id == ZIP64_EXTRA_FIELD_ID) {
                fail("NCP_ARCHIVE_INVALID", "ZIP64 extra fields are not supported by NCP v1")
            }
            cursor = next
        }
    }

    private fun readBytes(raf: RandomAccessFile, offset: Long, length: Int): ByteArray {
        if (length < 0 || offset < 0 || offset + length > raf.length()) {
            fail("NCP_ARCHIVE_INVALID", "ZIP byte range is outside archive bounds")
        }
        return ByteArray(length).also {
            raf.seek(offset)
            raf.readFully(it)
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
    private const val LOCAL_SIGNATURE = 0x04034B50L
    private const val UINT32_MAX = 0xFFFF_FFFFL
    private const val ZIP64_EXTRA_FIELD_ID = 0x0001
    private const val DATA_DESCRIPTOR_FLAG = 0x0008
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
