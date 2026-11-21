# Android device testing

## Current PR-branch build

The latest verified PR-branch Android debug build is GitHub Actions [run #157](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44866864793), completed on 2026-11-21 from commit `b40401ee5076752dd138fa2beda84b0a1636e2a9`.

Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44866864793/artifacts/14692556327), 925,935 bytes (artifact ZIP). SHA-256 of the artifact ZIP: `9d14a50fdf2e0a32129b762f08e5b288ef9dd97ab7d2a1602be1cc8dd293262c`. It expires on 2027-02-19 at 04:35:36 UTC.

This is a Debug build from the unmerged `feature/ncp-package-reader` branch. CI passed the Android debug APK build and all nine self-test groups:
- NeoMod core: 14/14
- legacy package reader: 14/14
- package manager: 11/11
- game-build identity: 9/9
- NCP manifest: 14/14
- NCP version range: 12/12
- NCP package reader: 12/12
- NCP package store: 10/10
- NCP dependency resolver: 18/18

## Install on the Samsung Android device

1. Open the latest verified run #157 link above. The new package-store UI integration is pending a subsequent CI build, so do not use this older APK to test the install/manage buttons.
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

An earlier physical-device test on 2026-11-15 confirmed successful Minecraft discovery and launch-intent handoff on that earlier build. Run #157 has not yet been tested on the device. It includes the smaller resolver search-state test fixture and successfully builds the APK. Launching the game does not prove NeoMod runtime compatibility.

## Test the NCP picker and package store

Download the safe [Hello World sample NCP package](https://raw.githubusercontent.com/MrRehop/NeoCraft-Launcher/feature/ncp-package-reader/examples/hello-world.ncp) to the phone first. It contains only a five-byte text payload and no executable code.

### Read-only validation

1. Tap **Проверить NCP-пакет**.
2. Select a trusted `.ncp` file using Android's document picker.
3. For a valid NCP v1 package, NeoCraft should display its name, ID, version, number of verified payload files, and archive size.
4. For an invalid or unsupported archive, NeoCraft should show a rejection reason.
5. Repeat with a file larger than 128 MiB; the import staging step should reject it before NCP validation.

### Data-only installation and management (new UI integration; awaiting CI/device verification)

1. Tap **Установить NCP (только данные)** and select the Hello World sample.
2. A successful installation should display the package name, ID, version, verified file count, and confirm that activation was not performed.
3. Tap **Управление NCP-пакетами**. The package should appear in the list as stored data, not as an active Minecraft mod.
4. Select the sample and confirm uninstall. Only the selected package version should be removed from NeoCraft's private storage.
5. Attempt to install the same package version again while it is already stored; the store should reject the duplicate without damaging the existing copy.

The picker stages the selected document in NeoCraft's private cache with a 128 MiB streaming cap. Validation and store operations run in the background. Installation extracts verified data into NeoCraft's private `files/neomod/ncp-store/packages` directory and rechecks the inventory and SHA-256 digests before commit. Package scripts, native libraries, and other executable content are not run or loaded into Minecraft. This is data storage only; it does not establish publisher authenticity, runtime isolation, or game compatibility.

## Collect Minecraft diagnostics

1. Tap **Проверить Minecraft** and wait for the background inspection.
2. Tap **Скопировать диагностику**.
3. Review the text and paste the report into the NeoCraft development conversation.

The reviewed game-build allow-list remains intentionally empty. The reported Minecraft PE Patched 1.26.13.1 identity is a candidate only; the original APK has not yet been independently re-hashed in the current build environment, and no NeoMod runtime probe has passed.

## Privacy and safety

Do not share passwords, GitHub tokens, signing keystores, or private keys. Do not install untrusted NCP packages. NCP validation is not publisher authentication and does not establish that a package is safe or compatible.
