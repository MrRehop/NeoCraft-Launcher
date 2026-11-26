# Minecraft PE Patched 1.26.13.1 — archived APK static identity

**Analysis date:** 2026-11-26  
**Source:** Library file com.mojang.minecraftpe-972601301.apk  
**File size:** 452,964,617 bytes

## Results

| Field | Independently observed value |
|---|---|
| APK SHA-256 | 6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916 |
| Android package | com.mojang.minecraftpe |
| Manifest versionName | 1.26.13.1 |
| Manifest versionCode | 972601301 |
| APK signing scheme records | v2 and v3 |
| X.509 signer certificate SHA-256 | b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890 |

The signer certificate fingerprint extracted from both the v2 and v3 signing-block records matches the SHA-256 fingerprint previously shown in the device's Signatures screen.

## Method

1. Computed SHA-256 over the complete archived APK file.
2. Read the ZIP end-of-central-directory record and confirmed the archive has one disk and 35,627 entries.
3. Located the APK Signing Block immediately before the central directory and found v2 (0x7109871a) and v3 (0xf05368c0) records.
4. Parsed the signer certificate DER bytes as X.509 and computed the certificate SHA-256 fingerprint.
5. Decoded the binary AndroidManifest.xml string pool and manifest start-element attributes to read package, versionName, and versionCode.

This analysis extracts the certificate and metadata; it does not perform a full cryptographic verification of the APK signing signatures.

## Important discrepancy and remaining device check

The user previously reported a displayed number 792601301 next to version 1.26.13.1, while this archived APK's AndroidManifest.xml declares versionCode 972601301. The number 792601301 is not confirmed as Android versionCode.

The static manifest value is not a substitute for querying the installed package with Android PackageManager. On the Samsung SM-A225F, run NeoCraft's **Проверить Minecraft** diagnostic and confirm that it reports:

- Package: com.mojang.minecraftpe
- Version name: 1.26.13.1
- Version code: 972601301
- Current signer SHA-256: b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890

Compare the device's reported APK/base.apk digest with the archived APK SHA-256 above. Do not register this build in ReviewedGameBuilds until the installed identity is matched and a harmless, version-specific NeoMod runtime compatibility probe passes.
