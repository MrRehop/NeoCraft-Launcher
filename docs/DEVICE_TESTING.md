# First Android device test

## Current build

The latest successful Android debug build is GitHub Actions [run #53](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43678005228), completed on 2026-11-15 at 04:45 UTC from commit \`d18aeb4cebfaa049efd8643a6dc2601ade8b7d6a\`.

Artifact: \`neocraft-debug-apk\` (885,666-byte ZIP containing \`app-debug.apk\`, 892,692 bytes). It expires on 2027-02-13 at 04:43 UTC. Verified artifact ZIP SHA-256: \`000a737b185b7eba6e2abf5773e0acb9826314b820b4c765b61164da6c62a603\`. Extracted APK SHA-256: \`b645204cc0b1210645109a3bc92d6e338b48ebdc7620d2d838043ff518302717\`.

The APK manifest reports launcher package \`org.neocraft.launcher\`, versionName \`0.1.0\`, versionCode \`1\`, minSdk \`26\`, and targetSdk \`35\`. This run passed the NCP/core self-tests and Android debug APK build. Run #53 has not yet been retested on the physical device.

## Install on the Samsung Android device

1. Open the [latest successful build run](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43678005228).
2. Scroll to **Artifacts** and download **neocraft-debug-apk**.
3. Open the downloaded ZIP and extract \`app-debug.apk\`.
4. Tap \`app-debug.apk\`. If Android asks, allow the file manager or browser to install this APK. Only grant this permission to the app you used to download the project artifact; you can revoke it afterward.
5. Install and open **NeoCraft Launcher**.

Do not uninstall, replace, or modify Minecraft PE Patched for this test.

## Test launching the installed game

1. Tap **Проверить Minecraft** and wait until the inspection finishes.
2. If the game is found, the **Запустить Minecraft** button becomes available.
3. Tap **Запустить Minecraft**. NeoCraft should open the installed \`com.mojang.minecraftpe\` package using Android's launch intent.
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
