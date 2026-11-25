# Android device testing

## Branch-head verification (checked 2026-11-25)

PR #1 remains open and in Draft state. Current branch head `1bb191c28be82427050a2c4fd76039e60f232349` is covered by GitHub Actions run #208, which passed all 150 self-test cases and Android `:app:assembleDebug`. The run includes the game-build-checked profile load overload and its four integration tests. No Android profile-management screen or Minecraft runtime activation is claimed.

## Current PR-branch build

The latest independently verified PR-branch Android debug build is GitHub Actions [run #208](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45633303912), completed on 2026-11-25 from commit `1bb191c28be82427050a2c4fd76039e60f232349`.

Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45633303912/artifacts/15031792416), 945,691 bytes (artifact ZIP). SHA-256 of the artifact ZIP: `d60a65af27065a798c2f6335bffe30b65b6415672b6021f2c755ac8d42051d50`. It expires on 2027-02-23 at 14:22:16 UTC.

This is a Debug build from the unmerged `feature/ncp-package-reader` branch. GitHub job logs confirm that Android `:app:assembleDebug` succeeded and the following 12 self-test groups passed (150/150 cases total):
- NeoMod core: 14/14
- legacy package reader: 14/14
- package manager: 11/11
- game-build identity: 9/9
- NCP manifest: 14/14
- NCP version range: 12/12
- NCP package reader: 12/12
- NCP package store: 10/10
- NCP dependency resolver: 18/18
- NCP installed-package plan: 7/7
- NCP profile snapshot/game-build matcher: 13/13
- NCP profile store: 16/16

Run #208 includes the profile-root symlink regression test, exact game-build matcher, and four integration tests for `load(profileId, plan, currentBuild)`.

## Install on the Samsung Android device

1. Open the latest verified run #208 link above. Its APK is compile-verified for the package-store UI and profile-load core changes, but the UI still requires physical-device testing.
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

An earlier physical-device test on 2026-11-15 confirmed successful Minecraft discovery and launch-intent handoff on that earlier build. Run #208 has not yet been tested on the physical device. Launching the game does not prove NeoMod runtime compatibility.

## Test the NCP picker and package store

Download the safe [Hello World sample NCP package](https://raw.githubusercontent.com/MrRehop/NeoCraft-Launcher/feature/ncp-package-reader/examples/hello-world.ncp) to the phone first. It contains only a five-byte text payload and no executable code.

### Read-only validation

1. Tap **Проверить NCP-пакет**.
2. Select a trusted `.ncp` file using Android's document picker.
3. For a valid NCP v1 package, NeoCraft should display its name, ID, version, number of verified payload files, and archive size.
4. For an invalid or unsupported archive, NeoCraft should show a rejection reason.
5. Repeat with a file larger than 128 MiB; the import staging step should reject it before NCP validation.

### Data-only installation and management (CI compile-verified; physical-device verification pending)

1. Tap **Установить NCP (только данные)** and select the Hello World sample.
2. A successful installation should display the package name, ID, version, verified file count, and confirm that activation was not performed.
3. Tap **Управление NCP-пакетами**. The package should appear in the list as stored data, not as an active Minecraft mod.
4. Select the sample and confirm uninstall. Only the selected package version should be removed from NeoCraft's private storage.
5. Attempt to install the same package version again while it is already stored; the store should reject the duplicate without damaging the existing copy.

The picker stages the selected document in NeoCraft's private cache with a 128 MiB streaming cap. Validation and store operations run in the background. Installation extracts verified data into NeoCraft's private `files/neomod/ncp-store/packages` directory and rechecks the inventory and SHA-256 digests before commit. Package scripts, native libraries, and other executable content are not run or loaded into Minecraft. This is data storage only; it does not establish publisher authenticity, runtime isolation, or game compatibility.

## Profile snapshot and profile-store core tests (host CI only)

The branch currently includes 13 profile snapshot/game-build matcher self-test cases and 16 profile-store self-test cases in source. Run #208 passed all 29 cases, including the four integration tests for `load(profileId, plan, currentBuild)`. These are platform-neutral core tests and do not expose profile management in the Android UI.

Coverage in the current source includes:
- snapshot validation: ID/name/game identity/fingerprint, required resolved dependencies, dependency-first ordering, and duplicate requests;
- game-build matching: exact package name, PackageManager versionCode, and APK SHA-256 match, plus explicit rejection of mismatches or malformed current identity;
- profile storage: save/load, atomic replacement, invalid snapshot rejection, plan mismatch, corrupt/oversized record rejection, symlink rejection (including replaced profile-root symlink), scoped delete, and unsafe ID rejection.

NcpProfileGameBuildMatcher is not yet connected to profile UI. The three-argument store load path composes it with package-plan validation and passed CI run #208. A future activation flow must supply the current installed-game identity and pass this check before offering activation. The Android profile-management screen and activation flow remain unimplemented.

## Collect Minecraft diagnostics

1. Tap **Проверить Minecraft** and wait for the background inspection.
2. Tap **Скопировать диагностику**.
3. Review the text and paste the report into the NeoCraft development conversation.

The reviewed game-build allow-list remains intentionally empty. The reported Minecraft PE Patched 1.26.13.1 identity is a candidate only; the original APK has not yet been independently re-hashed in the current build environment, and no NeoMod runtime probe has passed.

## Privacy and safety

Do not share passwords, GitHub tokens, signing keystores, or private keys. Do not install untrusted NCP packages. NCP validation is not publisher authentication and does not establish that a package is safe or compatible.
