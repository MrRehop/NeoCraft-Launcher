# First Android device test

## Current build

The latest successful Android debug build is GitHub Actions [run #54](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43724339536), completed on 2026-11-15 at 14:27 UTC from commit `5c9a4a4b0afae49c210ea31ab1fafd1be55f449c`.

Artifact: `neocraft-debug-apk` (886,438-byte ZIP containing `app-debug.apk`, 893,460 bytes). It expires on 2027-02-13 at 14:26 UTC. Verified artifact ZIP SHA-256: `c90a8580b72d1ef23c6ad4bb7b2cd9507cce2c09fd86c3860964fea74862365c`. Extracted APK SHA-256: `31d5c996cf4da5664f34a60221d30f9a1d35338e45e094aa77f216c78c023a1c`.

The APK manifest reports launcher package `org.neocraft.launcher`, versionName `0.1.0`, versionCode `1`, minSdk `26`, and targetSdk `35`. This run passed the NCP/core self-tests and Android debug APK build. Run #53 has not yet been retested on the physical device.

## Install on the Samsung Android device

1. Open the [latest successful build run](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43678005228).
2. Scroll to **Artifacts** and download **neocraft-debug-apk**.
3. Open the downloaded ZIP and extract `app-debug.apk`.
4. Tap `app-debug.apk`. If Android asks, allow the file manager or browser to install this APK. Only grant this permission to the app you used to download the project artifact; you can revoke it afterward.
5. Install and open **NeoCraft Launcher**.

Do not uninstall, replace, or modify Minecraft PE Patched for this test.

## Test launching the installed game

1. Tap **Проверить Minecraft** and wait until the inspection finishes.
2. If the game is found, the **Запустить Minecraft** button becomes available.
3. Tap **Запустить Minecraft**. NeoCraft should open the installed `com.mojang.minecraftpe` package using Android's launch intent.
4. Confirm that Minecraft opens normally. This test does not install, patch, or modify Minecraft.

**Earlier device test result (2026-11-15): PASS.** The user confirmed that Minecraft opened successfully from NeoCraft on an earlier debug build (run [43669593047](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43669593047)). This verifies package discovery and launch-intent handoff on the tested device/build; it does not verify NeoMod runtime integration. The newer run #53 still needs a separate on-device check.

If Android reports that the launch activity is unavailable, record the exact message. Do not uninstall or replace the game.

## Collect the game identity report

1. In NeoCraft, tap **Проверить Minecraft**.
2. Wait for the result. APK and DEX inspection runs in a background worker; unusually large DEX files or tables are skipped at configured safety limits.
3. Tap **Скопировать диагностику**.
4. Paste the complete copied report into the NeoCraft development conversation.

The report is expected to include the installed package name, Android version code, version name, current signer SHA-256, readable installed APK/split SHA-256 digests, the declared Application class and app component factory, and a bounded structural inventory of the APK/DEX files.

## How to interpret the result

- **Minecraft найдена** means Android PackageManager could read the installed package.
- **Совместимость не подтверждена** is currently expected: the reviewed game-build allow-list is deliberately empty.
- Discovery and APK inspection do not prove that NeoMod can modify Minecraft or that offline/LAN works.
- This is a debug build for testing, not a production/release APK.

## Privacy note

Review the diagnostic text before sharing it. It contains technical package and APK structure details, but should not contain passwords or account credentials. Do not send private keys, signing keystores, or GitHub tokens.
