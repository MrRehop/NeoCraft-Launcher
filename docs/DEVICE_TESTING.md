# Android device testing

## Current PR-branch build

The latest verified PR-branch Android debug build is GitHub Actions [run #120](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44330094264), completed on 2026-11-18 from commit `e33cf63f2e79543ecb9b461571014a8e2e877d33`.

Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44330094264/artifacts/14437983566), 909,725 bytes (artifact ZIP). SHA-256 of the artifact ZIP: `fce927f42dddc2e3ca83ba92722deac97b0c9d227aab6d977f8180cf5f08cd04`. It expires on 2027-02-16 at 14:57:51 UTC.

This is a Debug build from the unmerged `feature/ncp-package-reader` branch. CI passed the Android debug APK build and all seven self-test groups:
- NeoMod core: 14/14
- legacy package reader: 14/14
- package manager: 11/11
- game-build identity: 9/9
- NCP manifest: 14/14
- NCP package reader: 11/11
- NCP package store: 9/9

## Install on the Samsung Android device

1. Open the run #120 link above.
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

An earlier physical-device test on 2026-11-15 confirmed successful Minecraft discovery and launch-intent handoff on that earlier build. Run #120 has not yet been tested on the device. Launching the game does not prove NeoMod runtime compatibility.

## Test the NCP picker

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
