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

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var diagnosticReport: String? = null

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
        val check = Button(this).apply {
            text = "Проверить Minecraft"
            setOnClickListener { inspectGame() }
        }
        val copy = Button(this).apply {
            text = "Скопировать диагностику"
            isEnabled = false
            setOnClickListener { copyDiagnostics() }
            tag = "diagnostics_copy"
        }
        val mods = Button(this).apply {
            text = "NeoMod — скоро"
            isEnabled = false
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(check)
        layout.addView(copy)
        layout.addView(mods)
        setContentView(layout)
    }

    private fun inspectGame() {
        diagnosticReport = null
        findCopyButton()?.isEnabled = false
        status.text = "Проверка…"
        when (val result = AndroidGameDiscovery(this).inspect("com.mojang.minecraftpe")) {
            is GameDiscoveryResult.Found -> {
                val game = result.fingerprint
                val signers = game.signingCertificateSha256.orEmpty().sorted()
                val match = ReviewedGameBuilds.registry.match(game)
                val compatibility = when (match) {
                    is GameBuildMatch.Supported -> "ПОДТВЕРЖДЕНА: ${match.build.buildId}"
                    is GameBuildMatch.Unknown -> "НЕ ПОДТВЕРЖДЕНА: ${match.reason}"
                }
                diagnosticReport = buildString {
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
                status.text = "Minecraft найдена\nВерсия: ${game.versionName ?: "неизвестна"}" +
                    "\nversionCode: ${game.versionCode}" +
                    "\nSHA-256 сертификата: ${signers.joinToString() .ifEmpty { "нет данных" }}" +
                    "\n\nСовместимость NeoCraft: " +
                    when (match) {
                        is GameBuildMatch.Supported -> "подтверждена (${match.build.buildId})"
                        is GameBuildMatch.Unknown -> "не подтверждена\nПричина: ${match.reason}"
                    } +
                    "\n\nSHA-256 APK и структура архива доступны в скопированном отчёте."
                findCopyButton()?.isEnabled = true
            }
            is GameDiscoveryResult.NotInstalled -> {
                diagnosticReport = "NeoCraft diagnostic report\nPackage: ${result.packageName}\nStatus: not installed"
                status.text = "Minecraft не найдена: ${result.packageName}"
                findCopyButton()?.isEnabled = true
            }
            is GameDiscoveryResult.Unknown -> {
                diagnosticReport = "NeoCraft diagnostic report\nStatus: unknown\nReason: ${result.reason}"
                status.text = "Не удалось проверить Minecraft:\n${result.reason}"
                findCopyButton()?.isEnabled = true
            }
        }
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
                    dex.sorted().forEach { dexName ->
                        output += inspectDexClasses(zip, dexName)
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
            val bytes = zip.getInputStream(zip.getEntry(dexName)).use { it.readBytes() }
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
            require(stringCount in 0..2_000_000 && typeCount in 0..2_000_000 && classCount in 0..2_000_000) { "Invalid DEX table sizes" }
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
            try {
                val methodCount = readInt(88)
                val methodOffset = readInt(92)
                require(methodCount in 0..2_000_000) { "Invalid DEX method table size" }
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
                        val (_, p3) = readUleb(p2)
                        cursor = p3
                        methodIndex += diff
                        if ((flags and 0x100) != 0) {
                            require(methodIndex in 0 until methodCount) { "Invalid native method index" }
                            val item = methodOffset + methodIndex * 8
                            require(item >= 0 && item <= bytes.size - 8) { "DEX method item out of bounds" }
                            val ownerIndex = buffer.getShort(item).toInt() and 0xffff
                            val nameIndex = readInt(item + 4)
                            nativeMethodCount++
                            if (nativeMethodSample.size < 100) {
                                nativeMethodSample += (owners[ownerIndex] ?: "type#$ownerIndex") + "." + stringAt(nameIndex)
                            }
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
                "\n" + dexName + " native method sample: " + nativeMethodSample.joinToString().ifEmpty { "none found" }
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

    private fun findCopyButton(): Button? {
        val root = (findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)
        return findButtonByTag(root)
    }

    private fun findButtonByTag(group: android.view.ViewGroup?): Button? {
        if (group == null) return null
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is Button && child.tag == "diagnostics_copy") return child
            if (child is android.view.ViewGroup) {
                findButtonByTag(child)?.let { return it }
            }
        }
        return null
    }

    private fun copyDiagnostics() {
        val report = diagnosticReport ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NeoCraft diagnostics", report))
        Toast.makeText(this, "Диагностика скопирована", Toast.LENGTH_SHORT).show()
    }
}
