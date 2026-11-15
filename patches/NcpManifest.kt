package org.neocraft.neomod.core

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class NcpDependency(val id: String, val versionRange: String)

data class NcpContentDeclaration(
    val type: String,
    val localId: String,
    val path: String,
    val namespacedId: String,
)

data class NcpFileRecord(
    val path: String,
    val size: Long,
    val sha256: String,
)

data class NcpManifest(
    val id: String,
    val name: String,
    val publisher: String,
    val version: String,
    val apiVersionRange: String,
    val capabilities: Set<String>,
    val dependencies: List<NcpDependency>,
    val optionalDependencies: List<NcpDependency>,
    val conflicts: Set<String>,
    val content: List<NcpContentDeclaration>,
    val files: List<NcpFileRecord>,
    val extensions: Map<String, Any?>,
)

/** Strict, platform-neutral decoder for the NCP v1 manifest contract. */
object NcpManifestDecoder {
    private val packageIdRegex = Regex("^[a-z][a-z0-9_]{1,47}$")
    private val localIdRegex = Regex("^[a-z][a-z0-9_]{0,63}$")
    private val versionPart = "(?:0|[1-9][0-9]*)"
    private val versionRegex = Regex("^" + versionPart + "\\." + versionPart + "\\." + versionPart + "$")
    private val rangeRegex = Regex("^(?:" + versionPart + "\\." + versionPart + "\\." + versionPart +
        "|>=" + versionPart + "\\." + versionPart + "\\." + versionPart +
        "(?: <" + versionPart + "\\." + versionPart + "\\." + versionPart + ")?)$")
    private val boundedRangeRegex = Regex("^>=(.+) <(.+)$")
    private val sha256Regex = Regex("^[a-f0-9]{64}$")
    private val archivePathRegex = Regex("^content/[A-Za-z0-9._/-]+$")
    private val contentTypes = setOf("resource", "block", "item", "entity", "recipe", "behavior")
    private val knownCapabilities = setOf(
        "content.read",
        "content.register",
        "storage.profile",
        "network.client",
    )

    fun decode(bytes: ByteArray): NcpManifest {
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            fail("NCP_MANIFEST_INVALID", "manifest.json must be valid UTF-8")
        }
        return decode(text)
    }

    fun decode(json: String): NcpManifest {
        if (json.startsWith('\uFEFF')) {
            fail("NCP_MANIFEST_INVALID", "UTF-8 BOM is not allowed")
        }
        val root = try {
            NeoJsonParser(json).parse() as? NeoJsonValue.ObjectValue
                ?: fail("NCP_MANIFEST_INVALID", "manifest root must be an object")
        } catch (e: NeoModValidationException) {
            fail("NCP_MANIFEST_INVALID", e.message ?: "invalid JSON")
        }

        rejectUnknown(
            root,
            setOf(
                "format", "formatVersion", "id", "name", "publisher", "version", "api",
                "capabilities", "dependencies", "optionalDependencies", "conflicts",
                "content", "files", "extensions",
            ),
            "manifest",
        )

        if (root.required("format").asString("format") != "org.neocraft.ncp") {
            fail("NCP_FORMAT_UNSUPPORTED", "format must be org.neocraft.ncp")
        }
        if (root.required("formatVersion").asInt("formatVersion") != 1) {
            fail("NCP_FORMAT_UNSUPPORTED", "formatVersion must be 1")
        }

        val id = root.required("id").asString("id")
        if (!packageIdRegex.matches(id)) {
            fail("NCP_ID_INVALID", "id must match ^[a-z][a-z0-9_]{1,47}$")
        }
        val name = root.required("name").asString("name")
        if (name.isBlank() || name.length > 80) {
            fail("NCP_MANIFEST_INVALID", "name must contain 1..80 characters")
        }
        val publisher = root.required("publisher").asString("publisher")
        if (publisher.isBlank() || publisher.length > 120) {
            fail("NCP_MANIFEST_INVALID", "publisher must contain 1..120 characters")
        }

        val version = root.required("version").asString("version")
        requireVersion(version, "version")

        val api = root.required("api").asObject("api")
        rejectUnknown(api, setOf("versionRange"), "api")
        val apiRange = api.required("versionRange").asString("api.versionRange")
        requireVersionRange(apiRange, "api.versionRange")

        val capabilitiesList = root.required("capabilities").asStringArray("capabilities")
        if (capabilitiesList.size != capabilitiesList.toSet().size) {
            fail("NCP_MANIFEST_INVALID", "capabilities must be unique")
        }
        val unknownCapabilities = capabilitiesList.toSet() - knownCapabilities
        if (unknownCapabilities.isNotEmpty()) {
            fail("NCP_CAPABILITY_UNKNOWN", "unknown capabilities: " + unknownCapabilities.sorted().joinToString())
        }

        val dependencies = decodeDependencies(root.required("dependencies"), "dependencies")
        val optionalDependencies = decodeDependencies(root.required("optionalDependencies"), "optionalDependencies")
        val dependencyIds = mutableSetOf<String>()
        (dependencies + optionalDependencies).forEach { dependency ->
            if (dependency.id == id || !dependencyIds.add(dependency.id)) {
                fail("NCP_DEPENDENCY_INVALID", "dependency IDs must be unique and cannot reference this package")
            }
        }

        val conflictList = root.required("conflicts").asStringArray("conflicts")
        if (conflictList.size != conflictList.toSet().size || id in conflictList) {
            fail("NCP_CONFLICT_DECLARED", "conflicts must be unique and cannot contain this package ID")
        }
        conflictList.forEach { conflictId ->
            if (!packageIdRegex.matches(conflictId)) {
                fail("NCP_CONFLICT_DECLARED", "invalid conflict package ID: " + conflictId)
            }
        }

        val fileRecords = root.required("files").asArray("files").mapIndexed { index, value ->
            val field = "files[" + index + "]"
            val obj = value.asObject(field)
            rejectUnknown(obj, setOf("path", "size", "sha256"), field)
            val path = validateContentPath(obj.required("path").asString(field + ".path"), field + ".path")
            val size = obj.required("size").asLong(field + ".size")
            if (size < 0 || size > MAX_FILE_BYTES) {
                fail("NCP_LIMIT_EXCEEDED", field + ".size must be between 0 and " + MAX_FILE_BYTES)
            }
            val hash = obj.required("sha256").asString(field + ".sha256")
            if (!sha256Regex.matches(hash)) {
                fail("NCP_MANIFEST_INVALID", field + ".sha256 must be lowercase SHA-256 hex")
            }
            NcpFileRecord(path, size, hash)
        }
        val filePaths = fileRecords.map { it.path }
        if (filePaths.size != filePaths.toSet().size) {
            fail("NCP_FILE_INVENTORY_MISMATCH", "files contains duplicate paths")
        }

        val content = root.required("content").asArray("content").mapIndexed { index, value ->
            val field = "content[" + index + "]"
            val obj = value.asObject(field)
            rejectUnknown(obj, setOf("type", "localId", "path"), field)
            val type = obj.required("type").asString(field + ".type")
            if (type !in contentTypes) {
                fail("NCP_MANIFEST_INVALID", "unsupported content type: " + type)
            }
            val localId = obj.required("localId").asString(field + ".localId")
            if (!localIdRegex.matches(localId)) {
                fail("NCP_CONTENT_ID_DUPLICATE", "invalid localId: " + localId)
            }
            val path = validateContentPath(obj.required("path").asString(field + ".path"), field + ".path")
            if (path !in filePaths.toSet()) {
                fail("NCP_FILE_INVENTORY_MISMATCH", "content path is not listed in files: " + path)
            }
            NcpContentDeclaration(type, localId, path, id + ":" + localId)
        }
        val namespacedIds = content.map { it.namespacedId }
        if (namespacedIds.size != namespacedIds.toSet().size) {
            fail("NCP_CONTENT_ID_DUPLICATE", "duplicate namespaced content identifier")
        }

        val extensions = when (val value = root["extensions"]) {
            null -> emptyMap()
            is NeoJsonValue.ObjectValue -> value.values.mapValues { (_, extensionValue) -> toPlainValue(extensionValue) }
            else -> fail("NCP_MANIFEST_INVALID", "extensions must be an object")
        }

        return NcpManifest(
            id = id,
            name = name,
            publisher = publisher,
            version = version,
            apiVersionRange = apiRange,
            capabilities = capabilitiesList.toSet(),
            dependencies = dependencies,
            optionalDependencies = optionalDependencies,
            conflicts = conflictList.toSet(),
            content = content,
            files = fileRecords,
            extensions = extensions,
        )
    }

    private fun decodeDependencies(value: NeoJsonValue, field: String): List<NcpDependency> =
        value.asArray(field).mapIndexed { index, item ->
            val itemField = field + "[" + index + "]"
            val obj = item.asObject(itemField)
            rejectUnknown(obj, setOf("id", "versionRange"), itemField)
            val id = obj.required("id").asString(itemField + ".id")
            if (!packageIdRegex.matches(id)) {
                fail("NCP_DEPENDENCY_INVALID", "invalid dependency ID: " + id)
            }
            val range = obj.required("versionRange").asString(itemField + ".versionRange")
            requireVersionRange(range, itemField + ".versionRange")
            NcpDependency(id, range)
        }

    private fun requireVersion(value: String, field: String) {
        if (!versionRegex.matches(value)) {
            fail("NCP_VERSION_INVALID", field + " must use stable MAJOR.MINOR.PATCH")
        }
        value.split('.').forEach {
            if (it.toBigIntegerOrNull() == null) fail("NCP_VERSION_INVALID", field + " component is too large")
        }
    }

    private fun requireVersionRange(value: String, field: String) {
        if (!rangeRegex.matches(value)) {
            fail("NCP_DEPENDENCY_INVALID", field + " has an unsupported version range")
        }
        val bounded = boundedRangeRegex.matchEntire(value)
        if (bounded != null) {
            requireVersion(bounded.groupValues[1], field)
            requireVersion(bounded.groupValues[2], field)
            if (compareVersions(bounded.groupValues[1], bounded.groupValues[2]) >= 0) {
                fail("NCP_DEPENDENCY_INVALID", field + " lower bound must be less than upper bound")
            }
        } else {
            val exact = value.removePrefix(">=")
            requireVersion(exact, field)
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { BigInteger(it) }
        val b = right.split('.').map { BigInteger(it) }
        for (index in 0..2) {
            val comparison = a[index].compareTo(b[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun validateContentPath(value: String, field: String): String {
        if (!archivePathRegex.matches(value) || '\\' in value || ':' in value || '\u0000' in value) {
            fail("NCP_PATH_UNSAFE", field + " must be a canonical content/ POSIX path")
        }
        val segments = value.split('/')
        if (value.startsWith('/') || segments.any { it.isEmpty() || it == "." || it == ".." }) {
            fail("NCP_PATH_UNSAFE", "unsafe " + field + ": " + value)
        }
        return value
    }

    private fun rejectUnknown(obj: NeoJsonValue.ObjectValue, allowed: Set<String>, field: String) {
        val unknown = obj.values.keys - allowed
        if (unknown.isNotEmpty()) {
            fail("NCP_MANIFEST_INVALID", "unknown " + field + " field(s): " + unknown.sorted().joinToString())
        }
    }

    private fun NeoJsonValue.asObject(field: String): NeoJsonValue.ObjectValue =
        this as? NeoJsonValue.ObjectValue ?: fail("NCP_MANIFEST_INVALID", field + " must be an object")

    private fun NeoJsonValue.asArray(field: String): List<NeoJsonValue> =
        (this as? NeoJsonValue.ArrayValue)?.values ?: fail("NCP_MANIFEST_INVALID", field + " must be an array")

    private fun NeoJsonValue.asString(field: String): String =
        (this as? NeoJsonValue.StringValue)?.value ?: fail("NCP_MANIFEST_INVALID", field + " must be a string")

    private fun NeoJsonValue.asStringArray(field: String): List<String> =
        asArray(field).mapIndexed { index, value -> value.asString(field + "[" + index + "]") }

    private fun NeoJsonValue.asInt(field: String): Int {
        val raw = (this as? NeoJsonValue.NumberValue)?.value
            ?: fail("NCP_MANIFEST_INVALID", field + " must be an integer")
        return raw.toIntOrNull() ?: fail("NCP_MANIFEST_INVALID", field + " must be a 32-bit integer")
    }

    private fun NeoJsonValue.asLong(field: String): Long {
        val raw = (this as? NeoJsonValue.NumberValue)?.value
            ?: fail("NCP_MANIFEST_INVALID", field + " must be an integer")
        return raw.toLongOrNull() ?: fail("NCP_MANIFEST_INVALID", field + " must be a 64-bit integer")
    }

    private fun NeoJsonValue.ObjectValue.required(key: String): NeoJsonValue =
        values[key] ?: fail("NCP_MANIFEST_INVALID", "missing required field '" + key + "'")

    private fun toPlainValue(value: NeoJsonValue): Any? = when (value) {
        is NeoJsonValue.ObjectValue -> value.values.mapValues { (_, child) -> toPlainValue(child) }
        is NeoJsonValue.ArrayValue -> value.values.map(::toPlainValue)
        is NeoJsonValue.StringValue -> value.value
        is NeoJsonValue.NumberValue -> value.value.toBigDecimalOrNull()
        is NeoJsonValue.BooleanValue -> value.value
        NeoJsonValue.NullValue -> null
    }

    private fun fail(code: String, message: String): Nothing =
        throw NcpValidationException(code, message)

    private const val MAX_FILE_BYTES = 32L * 1024 * 1024
}

class NcpValidationException(
    val code: String,
    message: String,
) : IllegalArgumentException(code + ": " + message)
