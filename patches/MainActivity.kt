package org.neocraft.launcher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.neocraft.neomod.android.AndroidGameDiscovery
import org.neocraft.neomod.android.GameDiscoveryResult
import org.neocraft.neomod.core.GameBuildMatch
import org.neocraft.neomod.core.ReviewedGameBuilds

class MainActivity : Activity() {
    private lateinit var status: TextView

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
        val mods = Button(this).apply {
            text = "NeoMod — скоро"
            isEnabled = false
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(check)
        layout.addView(mods)
        setContentView(layout)
    }

    private fun inspectGame() {
        status.text = "Проверка…"
        when (val result = AndroidGameDiscovery(this).inspect("com.mojang.minecraftpe")) {
            is GameDiscoveryResult.Found -> {
                val game = result.fingerprint
                val identity = "Minecraft найдена\nВерсия: ${game.versionName ?: "неизвестна"}" +
                    "\nversionCode: ${game.versionCode}" +
                    "\nSHA-256 сертификата: ${game.signingCertificateSha256?.joinToString() ?: "нет данных"}"
                when (val match = ReviewedGameBuilds.registry.match(game)) {
                    is GameBuildMatch.Supported -> {
                        status.text = identity +
                            "\n\nСовместимость NeoCraft: подтверждена" +
                            "\nПрофиль: ${match.build.buildId}"
                    }
                    is GameBuildMatch.Unknown -> {
                        status.text = identity +
                            "\n\nСовместимость NeoCraft: не подтверждена" +
                            "\nПричина: ${match.reason}"
                    }
                }
            }
            is GameDiscoveryResult.NotInstalled ->
                status.text = "Minecraft не найдена: ${result.packageName}"
            is GameDiscoveryResult.Unknown ->
                status.text = "Не удалось проверить Minecraft:\n${result.reason}"
        }
    }
}
