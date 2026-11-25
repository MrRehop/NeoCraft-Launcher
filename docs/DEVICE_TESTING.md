# Android device testing

## Branch-head verification (checked 2026-11-25)

PR #1 remains open and in Draft state. The branch head `87cd6a817e949881b8058dab249dbc61256cec32` is covered by GitHub Actions run #202, which passed all 146 self-test cases and `:app:assembleDebug`. The current source now has a newer three-argument profile-load overload and four additional integration tests; these are not covered by run #202 and require a subsequent workflow. Do not treat the run #202 APK as containing those later changes.

## Current PR-branch build

The latest independently verified PR-branch Android debug build is GitHub Actions [run #202](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45549144542), completed on 2026-11-25 from commit `87cd6a817e949881b8058dab249dbc61256cec32`.

Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45549144542/artifacts/14992768023), 945,582 bytes (artifact ZIP). SHA-256 of the artifact ZIP: `94500b9263285a1213e05c0ccff2bac7a4da6bc1080574652c704f8069bc4b0c`.

This is a Debug build from the unmerged `feature/ncp-package-reader` branch. GitHub job logs confirm that the Android `:app:assembleDebug` task succeeded and the following 12 self-test groups passed (146/146 cases total):
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
- NCP profile store: 12/12

Run #202 includes the profile-root symlink regression test and the exact game-build matcher. It does not include the newer three-argument profile-load integration or its four additional tests.

## Install on the Samsung Android device

1. Open the latest verified run #186 link above. Its APK is compile-verified for the package-store UI, but the UI still requires physical-device testing.
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

An earlier physical-device test on 2026-11-15 confirmed successful Minecraft discovery and launch-intent handoff on that earlier build. Run #186 has not yet been tested on the physical device. Launching the game does not prove NeoMod runtime compatibility.

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

The branch currently includes 13 profile snapshot/game-build matcher self-test cases and 16 profile-store self-test cases in source. Run #202 passed the 13 matcher cases and the 12 profile-store cases that existed at that commit. Four new tests now exercise the integrated `load(profileId, plan, currentBuild)` path; those four are awaiting CI. These are platform-neutral core tests and do not expose profile management in the Android UI.

Coverage in the current source includes:
- snapshot validation: ID/name/game identity/fingerprint, required resolved dependencies, dependency-first ordering, and duplicate requests;
- game-build matching: exact package name, PackageManager versionCode, and APK SHA-256 match, plus explicit rejection of mismatches or malformed current identity;
- profile storage: save/load, atomic replacement, invalid snapshot rejection, plan mismatch, corrupt/oversized record rejection, symlink rejection (including replaced profile-root symlink), scoped delete, and unsafe ID rejection.

NcpProfileGameBuildMatcher is not yet connected to profile UI. The new three-argument store load path composes it with package-plan validation, but that integration still awaits CI. A future activation flow must supply the current installed-game identity and pass this check before offering activation. Run #202 already passed the matcher and profile-root regression tests; do not report the four newer store integration tests as passed until a workflow containing them succeeds.

## Collect Minecraft diagnostics

1. Tap **Проверить Minecraft** and wait for the background inspection.
2. Tap **Скопировать диагностику**.
3. Review the text and paste the report into the NeoCraft development conversation.

The reviewed game-build allow-list remains intentionally empty. The reported Minecraft PE Patched 1.26.13.1 identity is a candidate only; the original APK has not yet been independently re-hashed in the current build environment, and no NeoMod runtime probe has passed.

## Privacy and safety

Do not share passwords, GitHub tokens, signing keystores, or private keys. Do not install untrusted NCP packages. NCP validation is not publisher authentication and does not establish that a package is safe or compatible.
