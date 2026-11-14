package org.neocraft.neomod.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import org.neocraft.neomod.core.GameBuildFingerprint
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Reads package identity and exact installed APK artifact digests. */
class AndroidGameDiscovery(private val context: Context) {
    fun inspect(packageName: String): GameDiscoveryResult {
        if (packageName.isBlank()) return GameDiscoveryResult.Unknown("Target package name is blank")

        val packageInfo = try {
            getPackageInfo(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return GameDiscoveryResult.NotInstalled(packageName)
        } catch (error: SecurityException) {
            return GameDiscoveryResult.Unknown("Android denied package visibility: ${error.message ?: "no details"}")
        } catch (error: RuntimeException) {
            return GameDiscoveryResult.Unknown("Could not read package information: ${error.message ?: error.javaClass.simpleName}")
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        if (versionCode <= 0L) return GameDiscoveryResult.Unknown("Installed package returned an invalid versionCode")

        val signers = try {
            currentSignerCertificates(packageInfo)
        } catch (error: RuntimeException) {
            return GameDiscoveryResult.Unknown("Could not read signing certificates: ${error.message ?: error.javaClass.simpleName}")
        }
        if (signers.isEmpty()) return GameDiscoveryResult.Unknown("No current signing certificates are available")

        val artifacts = readArtifactDigests(packageInfo)
        return GameDiscoveryResult.Found(
            GameBuildFingerprint(
                packageName = packageInfo.packageName,
                versionCode = versionCode,
                versionName = packageInfo.versionName,
                signingCertificateSha256 = signers.map(::sha256).toSet(),
                artifactDigests = artifacts,
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String): PackageInfo {
        val pm = context.packageManager
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                    ),
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            else -> {
                // GET_SIGNING_CERTIFICATES was introduced in API 28.
                // API 23–27 must use the legacy flag to populate signatures.
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSignerCertificates(info: PackageInfo): List<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyList()
            signingInfo.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
    }

    private fun readArtifactDigests(info: PackageInfo): Map<String, String> {
        val appInfo = info.applicationInfo ?: return emptyMap()
        val paths = buildList {
            appInfo.sourceDir?.let(::add)
            appInfo.splitSourceDirs?.forEach(::add)
        }.distinct()
        return paths.mapNotNull { path ->
            val file = File(path)
            if (!file.isFile || !file.canRead()) return@mapNotNull null
            runCatching { file.name to sha256File(file) }.getOrNull()
        }.toMap()
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(signature: Signature): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

sealed interface GameDiscoveryResult {
    data class Found(val fingerprint: GameBuildFingerprint) : GameDiscoveryResult
    data class NotInstalled(val packageName: String) : GameDiscoveryResult
    data class Unknown(val reason: String) : GameDiscoveryResult
}
