# First Android device test

## Current build

The first debug APK was built by GitHub Actions workflow run [#27](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43575756710) on 2026-11-14. The uploaded artifact is named `neocraft-debug-apk`.

The artifact is temporary and is scheduled to expire on 2027-02-12. A later successful workflow run will create a new artifact.

## Install on the Samsung Android device

1. Open the [successful build run](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43575756710).
2. Scroll to **Artifacts** and download **neocraft-debug-apk**.
3. Open the downloaded ZIP and extract `app-debug.apk`.
4. Tap `app-debug.apk`. If Android asks, allow the file manager or browser to install this APK. Only grant this permission to the app you used to download the project artifact; you can revoke it afterward.
5. Install and open **NeoCraft Launcher**.

Do not uninstall, replace, or modify Minecraft PE Patched for this test.

## Collect the game identity report

1. In NeoCraft, tap **Проверить Minecraft**.
2. Wait for the result.
3. Tap **Скопировать диагностику**.
4. Paste the complete copied report into the NeoCraft development conversation.

The report is expected to include the installed package name, Android version code, version name, current signer SHA-256, readable installed APK/split SHA-256 digests, and a bounded structural inventory of the APK/DEX files.

## How to interpret the result

- **Minecraft найдена** means Android PackageManager could read the installed package.
- **Совместимость не подтверждена** is currently expected: the reviewed game-build allow-list is deliberately empty.
- Discovery and APK inspection do not prove that NeoMod can modify Minecraft or that offline/LAN works.
- This is a debug build for testing, not a production/release APK.

## Privacy note

Review the diagnostic text before sharing it. It contains technical package and APK structure details, but should not contain passwords or account credentials. Do not send private keys, signing keystores, or GitHub tokens.
