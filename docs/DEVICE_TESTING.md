# First Android device test

## Current build status

The latest successful Android debug build is GitHub Actions [run #87](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44104318461), completed on 2026-11-17 at 14:34 UTC from commit `0e86cfda3220bc37b324c35f62d02b75068f1d4b`.

Artifact: `neocraft-debug-apk`, 898,289 bytes. Artifact ZIP SHA-256: `970ceb4fb22f11b3d13fb8512634248be90fdef196cfac6be7db0348e6d0b06b`. It expires on 2027-02-15 at 14:32:59 UTC. The artifact is from before the transactional NCP package-store changes; it must not be presented as containing that newer code.

The subsequent PR workflow [run #101](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44119819515), from commit `a91db8e5c5ce0e1944ddf455745175c1e6ef565c`, failed in `ncpPackageStoreSelfTest`. The core tests (48/48), NCP manifest tests (14/14), and NCP package-reader tests (11/11) passed. Four package-store tests passed before the tamper test failed because its fixture changed the payload length, triggering the size check before the hash check. No APK artifact was produced by run #101. The fixture has been corrected to preserve the original byte length; that correction still needs a CI run.

## Install the last successful debug APK on the Samsung Android device

1. Open the [successful build run #87](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44104318461).
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

**Earlier device test result (2026-11-15): PASS.** The user confirmed that Minecraft opened successfully from NeoCraft on an earlier debug build (run [43669593047](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43669593047)). This verifies package discovery and launch-intent handoff on the tested device/build; it does not verify NeoMod runtime integration. Run #87 has not been tested on the physical device.

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
- The reported Minecraft PE 1.26.13.1 build is recorded as a discovered candidate only. Its APK/signing identity has not been independently verified from an exported APK, and no NeoMod compatibility probe has passed.
- Discovery and APK inspection do not prove that NeoMod can modify Minecraft or that offline/LAN works.
- This is a debug build for testing, not a production/release APK.

## Privacy note

Review the diagnostic text before sharing it. It contains technical package and APK structure details, but should not contain passwords or account credentials. Do not send private keys, signing keystores, or GitHub tokens.
