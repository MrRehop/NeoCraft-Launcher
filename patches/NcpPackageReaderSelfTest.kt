package org.neocraft.neomod.core

import java.io.File
import java.io.RandomAccessFile
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


private fun readNcpTestU16(raf: RandomAccessFile, offset: Long): Int {
    raf.seek(offset)
    val a = raf.read()
    val b = raf.read()
    check(a >= 0 && b >= 0)
    return a or (b shl 8)
}

private fun readNcpTestU32(raf: RandomAccessFile, offset: Long): Long {
    raf.seek(offset)
    val a = raf.read()
    val b = raf.read()
    val c = raf.read()
    val d = raf.read()
    check(a >= 0 && b >= 0 && c >= 0 && d >= 0)
    return (a.toLong() and 0xff) or ((b.toLong() and 0xff) shl 8) or
        ((c.toLong() and 0xff) shl 16) or ((d.toLong() and 0xff) shl 24)
}

private fun patchNcpEocdU16(file: File, fieldOffset: Long, value: Int) {
    RandomAccessFile(file, "rw").use { raf ->
        val length = raf.length()
        val searchStart = (length - 22 - 65_535).coerceAtLeast(0)
        var eocd = -1L
        var position = length - 22
        while (position >= searchStart) {
            if (readNcpTestU32(raf, position) == 0x06054B50L &&
                position + 22L + readNcpTestU16(raf, position + 20) == length) {
                eocd = position
                break
            }
            position--
        }
        check(eocd >= 0)
        raf.seek(eocd + fieldOffset)
        raf.write(value and 0xff)
        raf.write((value ushr 8) and 0xff)
    }
}

private fun changeFirstNcpLocalCompressionMethod(file: File, method: Int) {
    RandomAccessFile(file, "rw").use { raf ->
        val length = raf.length()
        val searchStart = (length - 22 - 65_535).coerceAtLeast(0)
        var eocd = -1L
        var position = length - 22
        while (position >= searchStart) {
            if (readNcpTestU32(raf, position) == 0x06054B50L &&
                position + 22L + readNcpTestU16(raf, position + 20) == length) {
                eocd = position
                break
            }
            position--
        }
        check(eocd >= 0)
        val centralOffset = readNcpTestU32(raf, eocd + 16)
        check(readNcpTestU32(raf, centralOffset) == 0x02014B50L)
        val localOffset = readNcpTestU32(raf, centralOffset + 42)
        check(readNcpTestU32(raf, localOffset) == 0x04034B50L)
        raf.seek(localOffset + 8)
        raf.write(method and 0xff)
        raf.write((method ushr 8) and 0xff)
    }
}

private fun markFirstNcpCentralEntryAsUnixSymlink(file: File) {
    RandomAccessFile(file, "rw").use { raf ->
        val length = raf.length()
        val searchStart = (length - 22 - 65_535).coerceAtLeast(0)
        var eocd = -1L
        var position = length - 22
        while (position >= searchStart) {
            if (readNcpTestU32(raf, position) == 0x06054B50L &&
                position + 22L + readNcpTestU16(raf, position + 20) == length) {
                eocd = position
                break
            }
            position--
        }
        check(eocd >= 0)
        val centralOffset = readNcpTestU32(raf, eocd + 16)
        check(readNcpTestU32(raf, centralOffset) == 0x02014B50L)
        raf.seek(centralOffset + 4)
        raf.write(0x14)
        raf.write(0x03) // Unix host platform, ZIP version 2.0
        raf.seek(centralOffset + 38)
        raf.write(0x00)
        raf.write(0x00)
        raf.write(0xFF)
        raf.write(0xA1) // Unix symlink mode in the upper 16 external-attribute bits
    }
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

        "declared uncompressed size mismatch rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(size = 99),
                "content/data.txt" to payload,
            ))
            try {
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_FILE_INVENTORY_MISMATCH")
            } finally { file.delete() }
        },
        "entry count limit enforced during enumeration" to {
            val entries = ArrayList<Pair<String, ByteArray>>()
            entries += "manifest.json" to ncpTestManifest()
            repeat(2048) { index ->
                entries += "content/extra_$index.bin" to byteArrayOf(1)
            }
            val file = makeNcpTestArchive(entries)
            try {
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_LIMIT_EXCEEDED")
            } finally { file.delete() }
        },
        "local and central compression methods must match" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/data.txt" to payload,
            ))
            try {
                changeFirstNcpLocalCompressionMethod(file, 99)
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_ARCHIVE_INVALID")
            } finally { file.delete() }
        },
        "Unix symbolic link metadata rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/data.txt" to payload,
            ))
            try {
                markFirstNcpCentralEntryAsUnixSymlink(file)
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_PATH_UNSAFE")
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
        "compression ratio limit enforced" to {
            val largePayload = ByteArray(100_000) { 'A'.code.toByte() }
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(ncpTestHash(largePayload), largePayload.size),
                "content/data.txt" to largePayload,
            ))
            try {
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_LIMIT_EXCEEDED")
            } finally { file.delete() }
        },
        "multi-disk ZIP metadata rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/data.txt" to payload,
            ))
            try {
                patchNcpEocdU16(file, 4, 1)
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_ARCHIVE_INVALID")
            } finally { file.delete() }
        },
        "ZIP64 sentinel entry counts rejected" to {
            val file = makeNcpTestArchive(listOf(
                "manifest.json" to ncpTestManifest(),
                "content/data.txt" to payload,
            ))
            try {
                patchNcpEocdU16(file, 8, 0xFFFF)
                patchNcpEocdU16(file, 10, 0xFFFF)
                val error = runCatching { NcpPackageReader.validate(file) }.exceptionOrNull()
                check(error is NcpValidationException && error.code == "NCP_ARCHIVE_INVALID")
            } finally { file.delete() }
        },
    )
    tests.forEach { (name, test) ->
        test()
        println("PASS: $name")
    }
    println("NCP PACKAGE READER RESULT: ${tests.size}/${tests.size} tests passed")
}
