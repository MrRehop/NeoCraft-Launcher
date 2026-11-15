package org.neocraft.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.neocraft.neomod.android.AndroidGameDiscovery
import org.neocraft.neomod.android.GameDiscoveryResult
import org.neocraft.neomod.core.GameBuildMatch
import org.neocraft.neomod.core.ReviewedGameBuilds
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var checkButton: Button
    private lateinit var copyButton: Button
    private var diagnosticReport: String? = null
    private val inspectionExecutor = Executors.newSingleThreadExecutor()
    private var inspectionToken = 0

    private companion object {
        const val MAX_DEX_BYTES = 64 * 1024 * 1024
        const val MAX_DEX_FILES = 8
        const val MAX_DEX_STRINGS = 1_000_000
        const val MAX_DEX_TYPES = 250_000
        const val MAX_DEX_CLASSES = 150_000
        const val MAX_DEX_METHODS = 200_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
        }
        val title = TextView(this).apply {
            text = "NeoCraft Launcher"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Minecraft ещё не проверена"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
        }
        checkButton = Button(this).apply {
            text = "Проверить Minecraft"
            setOnClickListener { inspectGame() }
        }
        copyButton = Button(this).apply {
            text = "Скопировать диагностику"
            isEnabled = false
            setOnClickListener { copyDiagnostics() }
        }
        val mods = Button(this).apply {
            text = "NeoMod — скоро"
            isEnabled = false
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(checkButton)
        layout.addView(copyButton)
        layout.addView(mods)
        setContentView(layout)
    }

    private fun inspectGame() {
        diagnosticReport = null
        copyButton.isEnabled = false
        checkButton.isEnabled = false
        status.text = "Проверка Minecraft и анализ APK…"
        val request = ++inspectionToken

        inspectionExecutor.execute {
            val result = try {
                AndroidGameDiscovery(this).inspect("com.mojang.minecraftpe")
            } catch (error: Exception) {
                GameDiscoveryResult.Unknown(
                    "Unexpected diagnostic error: ${error.message ?: error.javaClass.simpleName}"
                )
            }

            val (report, display) = when (result) {
                is GameDiscoveryResult.Found -> {
                    val game = result.fingerprint
                    val signers = game.signingCertificateSha256.orEmpty().sorted()
                    val match = ReviewedGameBuilds.registry.match(game)
                    val compatibility = when (match) {
                        is GameBuildMatch.Supported -> "ПОДТВЕРЖДЕНА: ${match.build.buildId}"
                        is GameBuildMatch.Unknown -> "НЕ ПОДТВЕРЖДЕНА: ${match.reason}"
                    }
                    val reportText = buildString {
                        appendLine("NeoCraft diagnostic report")
                        appendLine("Package: ${game.packageName}")
                        appendLine("Version name: ${game.versionName ?: "unknown"}")
                        appendLine("Version code: ${game.versionCode}")
                        appendLine("Current signer SHA-256:")
                        if (signers.isEmpty()) appendLine("unavailable")
                        else signers.forEach(::appendLine)
                        appendLine("Artifact digests:")
                        if (game.artifactDigests.isEmpty()) appendLine("unavailable (Android did not expose readable APK paths)")
                        else game.artifactDigests.toSortedMap().forEach { (name, digest) -> appendLine("$name SHA-256: $digest") }
                        appendLine("NeoMod compatibility: $compatibility")
                        appendLine("APK structure:")
                        inspectApkStructure(game.packageName).forEach(::appendLine)
                    }
                    val displayText = "Minecraft найдена\nВерсия: ${game.versionName ?: "неизвестна"}" +
                        "\nversionCode: ${game.versionCode}" +
                        "\nSHA-256 сертификата: ${signers.joinToString().ifEmpty { "нет данных" }}" +
                        "\n\nСовместимость NeoCraft: " +
                        when (match) {
                            is GameBuildMatch.Supported -> "подтверждена (${match.build.buildId})"
                            is GameBuildMatch.Unknown -> "не подтверждена\nПричина: ${match.reason}"
                        } +
                        "\n\nSHA-256 APK и структура архива доступны в скопированном отчёте."
                    reportText to displayText
                }
                is GameDiscoveryResult.NotInstalled -> {
                    "NeoCraft diagnostic report\nPackage: ${result.packageName}\nStatus: not installed" to
                        "Minecraft не найдена: ${result.packageName}"
                }
                is GameDiscoveryResult.Unknown -> {
                    "NeoCraft diagnostic report\nStatus: unknown\nReason: ${result.reason}" to
                        "Не удалось проверить Minecraft:\n${result.reason}"
                }
            }

            runOnUiThread {
                if (isFinishing || isDestroyed || request != inspectionToken) return@runOnUiThread
                diagnosticReport = report
                status.text = display
                copyButton.isEnabled = true
                checkButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        inspectionToken++
        inspectionExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun inspectApkStructure(packageName: String): List<String> {
        val appInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (error: Exception) {
            return listOf("APK structure: unavailable (${error.javaClass.simpleName})")
        }

        val apkPaths = buildList {
            appInfo.sourceDir?.let(::add)
            appInfo.splitSourceDirs?.forEach(::add)
        }.distinct()
        val output = mutableListOf<String>()
        for (path in apkPaths) {
            output += "APK: ${java.io.File(path).name}"
            try {
                java.util.zip.ZipFile(path).use { zip ->
                    val dex = mutableListOf<String>()
                    val native = mutableListOf<String>()
                    val assetRoots = sortedSetOf<String>()
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.matches(Regex("classes(\\d*)\\.dex"))) dex += name
                        if (name.startsWith("lib/") && name.endsWith(".so")) native += name
                        if (name.startsWith("assets/")) {
                            val root = name.removePrefix("assets/").substringBefore("/")
                            if (root.isNotBlank()) assetRoots += root
                        }
                    }
                    output += "DEX entries: ${dex.sorted().joinToString().ifEmpty { "none listed" }}"
                    output += "Native libraries: ${native.sorted().joinToString().ifEmpty { "none listed" }}"
                    output += "Asset top-level entries: ${assetRoots.joinToString().ifEmpty { "none listed" }}"
                    val dexNames = dex.sorted()
                    dexNames.take(MAX_DEX_FILES).forEach { dexName ->
                        output += inspectDexClasses(zip, dexName)
                    }
                    if (dexNames.size > MAX_DEX_FILES) {
                        output += "DEX scan limited to the first $MAX_DEX_FILES files; ${dexNames.size - MAX_DEX_FILES} additional DEX files were skipped"
                    }
                    output += inspectAndroidComponents(packageName)
                }
            } catch (error: Exception) {
                output += "ZIP inspection failed: ${error.javaClass.simpleName}: ${error.message ?: "no details"}"
            }
        }
        return output
    }

    private fun inspectDexClasses(zip: java.util.zip.ZipFile, dexName: String): String {
        return try {
            val dexEntry = zip.getEntry(dexName) ?: error("DEX entry is missing")
            require(dexEntry.size < 0L || dexEntry.size <= MAX_DEX_BYTES.toLong()) {
                "DEX entry exceeds the ${MAX_DEX_BYTES}-byte inspection limit"
            }
            val bytes = zip.getInputStream(dexEntry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_DEX_BYTES) { "DEX entry exceeds the inspection limit" }
                    output.write(chunk, 0, count)
                }
                output.toByteArray()
            }
            val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            fun readInt(offset: Int): Int {
                require(offset >= 0 && offset <= bytes.size - 4) { "DEX offset out of bounds" }
                return buffer.getInt(offset)
            }
            fun readUleb(offset: Int): Pair<Int, Int> {
                var value = 0
                var shift = 0
                var cursor = offset
                while (shift < 35) {
                    require(cursor in bytes.indices) { "ULEB128 out of bounds" }
                    val b = bytes[cursor++].toInt() and 0xff
                    value = value or ((b and 0x7f) shl shift)
                    if ((b and 0x80) == 0) return value to cursor
                    shift += 7
                }
                error("Invalid ULEB128")
            }
            require(bytes.size >= 112 && String(bytes, 0, 4, Charsets.US_ASCII) == "dex\n") { "Invalid DEX header" }
            val stringCount = readInt(56)
            val stringOffset = readInt(60)
            val typeCount = readInt(64)
            val typeOffset = readInt(68)
            val classCount = readInt(96)
            val classOffset = readInt(100)
            require(stringCount in 0..MAX_DEX_STRINGS && typeCount in 0..MAX_DEX_TYPES && classCount in 0..MAX_DEX_CLASSES) {
                "DEX table sizes exceed safe inspection limits"
            }
            val cache = HashMap<Int, String>()
            fun stringAt(index: Int): String = cache.getOrPut(index) {
                require(index in 0 until stringCount) { "Invalid string index" }
                val dataOffset = readInt(stringOffset + index * 4)
                val (_, start) = readUleb(dataOffset)
                var end = start
                while (end < bytes.size && bytes[end].toInt() != 0) end++
                require(end < bytes.size) { "Unterminated DEX string" }
                String(bytes, start, end - start, Charsets.UTF_8)
            }
            val classNames = sortedSetOf<String>()
            for (i in 0 until classCount) {
                val classIndex = readInt(classOffset + i * 32)
                require(classIndex in 0 until typeCount) { "Invalid class type index" }
                val descriptorIndex = readInt(typeOffset + classIndex * 4)
                val descriptor = stringAt(descriptorIndex)
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    classNames += descriptor.substring(1, descriptor.length - 1).replace("/", ".")
                }
            }
            var nativeMethodCount = 0
            val nativeMethodSample = mutableListOf<String>()
            val relevantNativeMethodSample = mutableListOf<String>()
            val patchClassMethodSample = mutableListOf<String>()
            try {
                val methodCount = readInt(88)
                val methodOffset = readInt(92)
                require(methodCount in 0..MAX_DEX_METHODS) { "DEX method table exceeds safe inspection limits" }
                val owners = HashMap<Int, String>()
                for (i in 0 until classCount) {
                    val classIndex = readInt(classOffset + i * 32)
                    val descriptor = stringAt(readInt(typeOffset + classIndex * 4))
                    if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                        owners[classIndex] = descriptor.substring(1, descriptor.length - 1).replace("/", ".")
                    }
                }
                fun scanMethods(start: Int, size: Int) {
                    var cursor = start
                    var methodIndex = 0
                    repeat(size) {
                        val (diff, p1) = readUleb(cursor)
                        val (flags, p2) = readUleb(p1)
                        val (codeOffset, p3) = readUleb(p2)
                        cursor = p3
                        methodIndex += diff
                        require(methodIndex in 0 until methodCount) { "Invalid DEX method index" }
                        val item = methodOffset + methodIndex * 8
                        require(item >= 0 && item <= bytes.size - 8) { "DEX method item out of bounds" }
                        val ownerIndex = buffer.getShort(item).toInt() and 0xffff
                        val nameIndex = readInt(item + 4)
                        val owner = owners[ownerIndex] ?: "type#$ownerIndex"
                        val method = owner + "." + stringAt(nameIndex)
                        if ((flags and 0x100) != 0) {
                            nativeMethodCount++
                            if (nativeMethodSample.size < 100) nativeMethodSample += method
                            if ((owner.startsWith("com.mojang.") || owner.startsWith("net.minecraft.") || owner.startsWith("com.modscraft.")) && relevantNativeMethodSample.size < 200) {
                                relevantNativeMethodSample += method
                            }
                        }
                        if ((owner == "com.modscraft.mcpepatch.Patches" || owner == "com.modscraft.mcpepatch.KillerApplication") && patchClassMethodSample.size < 200) {
                            val codeInfo = if (codeOffset == 0) {
                                "no code body"
                            } else {
                                try {
                                    val registers = buffer.getShort(codeOffset).toInt() and 0xffff
                                    val ins = buffer.getShort(codeOffset + 2).toInt() and 0xffff
                                    val outs = buffer.getShort(codeOffset + 4).toInt() and 0xffff
                                    val tries = buffer.getShort(codeOffset + 6).toInt() and 0xffff
                                    val instructionUnits = readInt(codeOffset + 12)
                                    require(instructionUnits in 0..65536) { "Invalid DEX instruction count" }
                                    require(codeOffset >= 0 && codeOffset <= bytes.size - 16 &&
                                        instructionUnits <= (bytes.size - codeOffset - 16) / 2) { "DEX code item out of bounds" }
                                    val dumpUnits = minOf(instructionUnits, 128)
                                    val rawInstructions = (0 until dumpUnits).joinToString(" ") { index ->
                                        (buffer.getShort(codeOffset + 16 + index * 2).toInt() and 0xffff)
                                            .toString(16).padStart(4, '0')
                                    }
                                    val dumpSuffix = if (instructionUnits > dumpUnits) " … (truncated)" else ""
                                    "codeOff=$codeOffset registers=$registers ins=$ins outs=$outs tries=$tries instructionUnits=$instructionUnits rawCodeUnits[$dumpUnits]=$rawInstructions$dumpSuffix"
                                } catch (error: Exception) {
                                    "code metadata unavailable: " + error.javaClass.simpleName
                                }
                            }
                            patchClassMethodSample += method + " [access=0x" + flags.toString(16) + ", $codeInfo]"
                        }
                    }
                }
                for (i in 0 until classCount) {
                    val dataOffset = readInt(classOffset + i * 32 + 24)
                    if (dataOffset == 0) continue
                    var cursor = dataOffset
                    val (staticFields, p1) = readUleb(cursor); cursor = p1
                    val (instanceFields, p2) = readUleb(cursor); cursor = p2
                    val (directMethods, p3) = readUleb(cursor); cursor = p3
                    val (virtualMethods, p4) = readUleb(cursor); cursor = p4
                    repeat(staticFields + instanceFields) {
                        val (_, pA) = readUleb(cursor)
                        val (_, pB) = readUleb(pA)
                        cursor = pB
                    }
                    scanMethods(cursor, directMethods)
                    repeat(directMethods) {
                        val (_, pA) = readUleb(cursor)
                        val (_, pB) = readUleb(pA)
                        val (_, pC) = readUleb(pB)
                        cursor = pC
                    }
                    scanMethods(cursor, virtualMethods)
                }
            } catch (error: Exception) {
                nativeMethodSample.clear()
                relevantNativeMethodSample.clear()
                patchClassMethodSample.clear()
                nativeMethodSample += "Native method scan failed: " + error.javaClass.simpleName + ": " + (error.message ?: "no details")
                nativeMethodCount = -1
            }
            val packageCounts = classNames.mapNotNull { it.substringBeforeLast(".", "").takeIf { p -> p.isNotBlank() } }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(15)
            val mojangSample = classNames.filter { it.startsWith("com.mojang.") || it.startsWith("net.minecraft.") }.take(40)
            dexName + " defined classes: " + classNames.size +
                "\n" + dexName + " package prefixes: " + packageCounts.joinToString { it.key + " (" + it.value + ")" }.ifEmpty { "none" } +
                "\n" + dexName + " Mojang/Minecraft sample: " + mojangSample.joinToString().ifEmpty { "none in selected namespaces" } +
                "\n" + dexName + " native methods: " + if (nativeMethodCount < 0) nativeMethodSample.joinToString() else nativeMethodCount.toString() +
                "\n" + dexName + " native method sample (first 100): " + nativeMethodSample.joinToString().ifEmpty { "none found" } +
                "\n" + dexName + " Mojang/Minecraft/modscraft native methods: " + relevantNativeMethodSample.joinToString().ifEmpty { "none found in scanned owners" } +
                "\n" + dexName + " modscraft/amogus class sample: " + classNames.filter { it.startsWith("com.modscraft.") || it.startsWith("amogus.") }.take(100).joinToString().ifEmpty { "none found" } +
                "\n" + dexName + " patch class methods: " + patchClassMethodSample.joinToString().ifEmpty { "none found in target classes" }
        } catch (error: Exception) {
            dexName + " class scan failed: " + error.javaClass.simpleName + ": " + (error.message ?: "no details")
        }
    }

    private fun inspectAndroidComponents(packageName: String): String {
        return try {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_ACTIVITIES or android.content.pm.PackageManager.GET_SERVICES or android.content.pm.PackageManager.GET_RECEIVERS or android.content.pm.PackageManager.GET_PROVIDERS or android.content.pm.PackageManager.GET_PERMISSIONS)
            val lines = mutableListOf<String>()
            lines += "Android activities: " + (info.activities?.map { it.name }?.sorted()?.joinToString()?.ifEmpty { "none declared" } ?: "none declared")
            lines += "Android services: " + (info.services?.map { it.name }?.sorted()?.joinToString()?.ifEmpty { "none declared" } ?: "none declared")
            lines += "Android receivers: " + (info.receivers?.map { it.name }?.sorted()?.joinToString()?.ifEmpty { "none declared" } ?: "none declared")
            lines += "Android providers: " + (info.providers?.map { it.name }?.sorted()?.joinToString()?.ifEmpty { "none declared" } ?: "none declared")
            lines += "Requested permissions: " + (info.requestedPermissions?.sorted()?.joinToString()?.ifEmpty { "none listed" } ?: "none listed")
            lines.joinToString("\n")
        } catch (error: Exception) {
            "Android component scan failed: " + error.javaClass.simpleName + ": " + (error.message ?: "no details")
        }
    }

    private fun copyDiagnostics() {
        val report = diagnosticReport ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NeoCraft diagnostics", report))
        Toast.makeText(this, "Диагностика скопирована", Toast.LENGTH_SHORT).show()
    }
}
