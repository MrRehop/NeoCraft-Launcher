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
                }
            } catch (error: Exception) {
                output += "ZIP inspection failed: ${error.javaClass.simpleName}: ${error.message ?: "no details"}"
            }
        }
        return output
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
