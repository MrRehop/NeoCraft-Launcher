# Android device testing

## Current PR-branch build

The latest verified PR-branch Android debug build is GitHub Actions [run #129](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44461513490), completed on 2026-11-19 from commit `aec0aae43771ddf8f216caff8187b11195d455cd`.

Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44461513490/artifacts/14499674681), 913,765 bytes (artifact ZIP). SHA-256 of the artifact ZIP: `524d7d383a61d42204f0d7628ff2c7f766965786e9910611675c13daaea95849`. It expires on 2027-02-17 at 04:54:40 UTC.

This is a Debug build from the unmerged `feature/ncp-package-reader` branch. CI passed the Android debug APK build and all eight self-test groups:
- NeoMod core: 14/14
- legacy package reader: 14/14
- package manager: 11/11
- game-build identity: 9/9
- NCP manifest: 14/14
- NCP version range: 12/12
- NCP package reader: 12/12
- NCP package store: 9/9

## Install on the Samsung Android device

1. Open the run #129 link above.
2. In **Artifacts**, download `neocraft-debug-apk`.
3. Extract `app-debug.apk` from the downloaded ZIP.
4. Tap the APK and allow installation for the file manager/browser if Android requests it.
5. Install and open **NeoCraft Launcher**.

Do not uninstall, replace, or modify Minecraft PE Patched. If Android refuses to update NeoCraft because the signing certificate differs from an older debug build, remove only the old NeoCraft app and install the new debug APK; this may clear NeoCraft's local data.

## Test Minecraft discovery and launch

1. Tap **Проверить Minecraft**.
2. Confirm the displayed package is `com.mojang.minecraftpe`.
3. If found, tap **Запустить Minecraft**.
4. Confirm Minecraft opens normally.

An earlier physical-device test on 2026-11-15 confirmed successful Minecraft discovery and launch-intent handoff on that earlier build. Run #129 has not yet been tested on the device. Launching the game does not prove NeoMod runtime compatibility.

## Test the NCP picker

Download the safe [Hello World sample NCP package](https://raw.githubusercontent.com/MrRehop/NeoCraft-Launcher/feature/ncp-package-reader/examples/hello-world.ncp) to the phone first. It contains only a five-byte text payload and no executable code.

1. Tap **Проверить NCP-пакет**.
2. Select a trusted `.ncp` file using Android's document picker.
3. For a valid NCP v1 package, NeoCraft should display its name, ID, version, number of verified payload files, and archive size.
4. For an invalid or unsupported archive, NeoCraft should show a rejection reason.
5. Repeat with a file larger than 128 MiB; the import staging step should reject it before NCP validation.

The picker copies the selected document to NeoCraft's private cache, applies a 128 MiB streaming limit, runs read-only validation, and deletes the temporary copy. It does not install, activate, execute, or load package content into Minecraft. The transactional package store is not yet connected to the Android screen.

## Collect Minecraft diagnostics

1. Tap **Проверить Minecraft** and wait for the background inspection.
2. Tap **Скопировать диагностику**.
3. Review the text and paste the report into the NeoCraft development conversation.

The reviewed game-build allow-list remains intentionally empty. The reported Minecraft PE Patched 1.26.13.1 identity is a candidate only; the original APK has not yet been independently re-hashed in the current build environment, and no NeoMod runtime probe has passed.

## Privacy and safety

Do not share passwords, GitHub tokens, signing keystores, or private keys. Do not install untrusted NCP packages. NCP validation is not publisher authentication and does not establish that a package is safe or compatible.
