# Android device testing

## Branch-head verification (checked 2026-11-24)

The latest source commit made in this run is `ea518ccaa23f75a937f3940780b781c7cd4d7c78` on `feature/ncp-package-reader`. PR #1 remains open and in Draft state. The GitHub connector returned no PR-triggered workflow entries for the recent follow-up commits; this does not establish whether a push-triggered workflow ran. No CI result for the current branch head is confirmed.

Run #186 below is the latest build independently verified through its GitHub Actions job and artifact records. It predates the profile-root symlink follow-up and the new exact game-build matcher/tests. Do not treat its APK or test results as verification of those later commits.

## Current PR-branch build

The latest independently verified PR-branch Android debug build is GitHub Actions [run #186](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45116369676), completed on 2026-11-23 from commit `ddfc2ffaef00b5aca2349ba2d4ed7e1b2b3a4ef6`.

Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45116369676/artifacts/14791274577), 944,954 bytes (artifact ZIP). SHA-256 of the artifact ZIP: `54ee07abfc32f7230ffc768ce92d813409a6edd743459b7a792ae90245782d37`. It expires on 2027-02-21 at 05:13:03 UTC.

This is a Debug build from the unmerged `feature/ncp-package-reader` branch. GitHub job logs confirm that the Android `:app:assembleDebug` task succeeded and the following 12 self-test groups passed (138/138 cases total):
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
- NCP profile snapshot: 8/8
- NCP profile store: 9/9

Run #186 does not include the later profile-root symlink regression test or the new profile game-build matcher and its five tests.

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

An earlier physical-device test on 2026-11-15 confirmed successful Minecraft discovery and launch-intent handoff on that earlier build. Run #186 has not yet been tested on the physical device. Launching the game does not prove NeoMod runtime compatibility. Launching the game does not prove NeoMod runtime compatibility.

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

The branch currently includes 13 profile snapshot/game-build matcher self-test cases and 10 profile-store self-test cases in source. These are platform-neutral core tests; they do not expose profile management in the Android UI and are not device-testable through the current launcher screen.

CI run #186 passed the earlier eight snapshot and nine profile-store cases. The later five matcher cases and the tenth store regression test were added after run #186 and are not yet CI-verified.

Coverage in the current source includes:
- snapshot validation: ID/name/game identity/fingerprint, required resolved dependencies, dependency-first ordering, and duplicate requests;
- game-build matching: exact package name, PackageManager versionCode, and APK SHA-256 match, plus explicit rejection of mismatches or malformed current identity;
- profile storage: save/load, atomic replacement, invalid snapshot rejection, plan mismatch, corrupt/oversized record rejection, symlink rejection (including replaced profile-root symlink), scoped delete, and unsafe ID rejection.

NcpProfileGameBuildMatcher is not yet connected to profile UI. A future activation flow must supply the current installed-game identity and pass this check before offering activation. Do not report the new matcher or profile-root regression tests as passed until CI results for a commit containing them are available.

## Collect Minecraft diagnostics

1. Tap **Проверить Minecraft** and wait for the background inspection.
2. Tap **Скопировать диагностику**.
3. Review the text and paste the report into the NeoCraft development conversation.

The reviewed game-build allow-list remains intentionally empty. The reported Minecraft PE Patched 1.26.13.1 identity is a candidate only; the original APK has not yet been independently re-hashed in the current build environment, and no NeoMod runtime probe has passed.

## Privacy and safety

Do not share passwords, GitHub tokens, signing keystores, or private keys. Do not install untrusted NCP packages. NCP validation is not publisher authentication and does not establish that a package is safe or compatible.
