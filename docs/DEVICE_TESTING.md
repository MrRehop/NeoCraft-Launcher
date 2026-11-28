# Android device testing

## Branch-head verification (checked 2026-11-28)

PR #1 remains open and in Draft state. The Android profile-creation flow from commit `ad52e836b199facb29b72cdd085b3c44826f1dc3` is included in successful CI run #222. The latest verified branch-head build before the 2026-11-28 profile-ID fix is run #225, commit `5a302e2da29441ca02845c514857f998d61b7c40`; its self-test step, Android `:app:assembleDebug`, and artifact upload all succeeded. Artifact `neocraft-debug-apk` is available from run #225 (ZIP size 959,672 bytes; artifact ID 15204578544). Physical-device verification of profile creation is still pending.

The 2026-11-28 change set extracts profile-ID generation into `NcpProfileIdGenerator`, fixes escaped Kotlin string interpolation in the profile-creation UI, and adds seven profile-ID self-tests to CI. Run #229 for commit `5ecb534b8df7b14824cd7dda86d97226055eb13c` is in progress; its result is not yet confirmed. Do not treat run #225's APK as containing these latest fixes. Profile editing, runtime activation, and Minecraft runtime compatibility remain unimplemented.

## Current PR-branch build

The latest independently verified PR-branch Android debug build before the 2026-11-28 profile-ID fix is GitHub Actions [run #225](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/46014744132), completed on 2026-11-27 from commit `5a302e2da29441ca02845c514857f998d61b7c40`.

Artifact: [neocraft-debug-apk](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/46014744132/artifacts/15204578544), 959,672 bytes (artifact ZIP). GitHub reports expiry on 2027-02-25. A SHA-256 for this artifact ZIP was not recorded in the workflow metadata.

This Debug build is from the unmerged `feature/ncp-package-reader` branch. The run's job steps confirm that the NeoMod self-test task, Android `:app:assembleDebug`, and artifact upload succeeded. It includes the Android profile-creation UI, but it predates the profile-ID generator/interpolation fixes committed on 2026-11-28.
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

1. Open the verified run #225 link above. Its APK includes the profile-creation UI and is compile-verified, but profile creation still requires physical-device testing. The later profile-ID fix is not in this APK.
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

## Profile snapshot, profile store, and composer tests (host CI only)

The branch includes 13 profile snapshot/game-build matcher cases, 16 profile-store cases, and 8 profile-composer cases in source. Run #208 passed the first 29 cases, including the four integration tests for `load(profileId, plan, currentBuild)`. The composer self-test task is present in the workflow, but its execution for commit `ad52e836b199facb29b72cdd085b3c44826f1dc3` is not confirmed.

Coverage in the current source includes:
- snapshot validation: ID/name/game identity/fingerprint, required resolved dependencies, dependency-first ordering, and duplicate requests;
- game-build matching: exact package name, PackageManager versionCode, and APK SHA-256 match, plus explicit rejection of mismatches or malformed current identity;
- profile storage: save/load, atomic replacement, invalid snapshot rejection, plan mismatch, corrupt/oversized record rejection, symlink rejection (including replaced profile-root symlink), scoped delete, and unsafe ID rejection.

NcpProfileGameBuildMatcher is not yet used by an activation UI. The three-argument store load path composes it with package-plan validation and passed CI run #212. A future activation flow must supply the current installed-game identity and pass this check before offering activation. The new profile-creation UI source selects one exact installed version per package ID, rechecks the current Minecraft identity at save time, resolves dependencies with `NcpProfileComposer`, and stores only profile metadata; commit `ad52e836b199facb29b72cdd085b3c44826f1dc3` is pending CI and device verification. Profile editing and activation remain unimplemented. The read-only browser and metadata deletion are compile-verified in run #212.

## Read-only NeoMod profile browser and metadata deletion (CI compile-verified; physical-device testing pending)

1. Open **Профили NeoMod (просмотр)**.
2. Confirm that saved profiles appear with their display name, ID, saved Minecraft versionCode, and resolved package count.
3. Select a profile and inspect its saved package list and recorded game fingerprint.
4. In the details dialog, tap **Удалить профиль**. Choose **Отмена** once to verify that cancellation leaves the profile intact.
5. Reopen the same profile, tap **Удалить профиль**, then confirm **Удалить**.
6. Confirm the profile disappears from the list. The UI should state that only profile metadata was removed; installed NCP package data and Minecraft files are not modified.

The browser calls listProfiles() and inspect(). It performs structural metadata validation only; it does not re-resolve dependencies or compare the saved game identity with the currently installed Minecraft build. Deletion calls NcpProfileStore.delete(profileId) after an explicit confirmation. It removes one .ncp-profile metadata file and does not uninstall referenced NCP packages or modify Minecraft. Profile creation, editing, package selection, and activation are not implemented.

## Create a NeoMod profile (new source; CI/device verification pending)

The profile-creation action is present in the verified APK from run #225. The additional profile-ID generator and interpolation fixes from 2026-11-28 require a newer successful run before they can be tested:

1. Install at least one trusted NCP package using **Установить NCP (только данные)**.
2. Tap **Создать профиль NeoMod**. NeoCraft rechecks the installed Minecraft package and requires a readable SHA-256 digest for `base.apk`.
3. Select one installed version per package ID. Mandatory dependencies are resolved automatically from a fresh integrity-checked package-store listing; optional dependencies are not auto-selected.
4. Enter a display name and tap **Создать**.
5. Open **Профили NeoMod (просмотр)** and inspect the saved snapshot. Confirm that its requested packages, dependency-first resolved list, Minecraft versionCode, and base.apk fingerprint are recorded.
6. Confirm that profile creation only saves a `.ncp-profile` metadata file. It does not activate packages, execute package content, or modify Minecraft.

The current UI supplies NeoMod API version `1.0.0`. If the installed game identity or base.apk digest cannot be read, creation must fail rather than saving an incomplete target identity. If a requested package version is no longer installed or a mandatory dependency cannot be resolved, creation must fail without saving a profile.

## Collect Minecraft diagnostics

1. Tap **Проверить Minecraft** and wait for the background inspection.
2. Tap **Скопировать диагностику**.
3. Review the text and paste the report into the NeoCraft development conversation.

The reviewed game-build allow-list remains intentionally empty. The archived APK file was independently hashed and parsed: SHA-256 6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916; manifest package com.mojang.minecraftpe, versionName 1.26.13.1, versionCode 972601301; signer certificate fingerprint from APK v2/v3 block b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890. The Stage10 device-evidence archive also contains a user-provided NeoCraft diagnostic report dated 2026-11-15 that records PackageManager versionCode 972601301 and the same signer/base.apk digests. It has not been freshly re-collected in this run. The separately mentioned number 792601301 does not match the manifest or that prior report and remains unverified. No NeoMod runtime probe has passed.

## Privacy and safety

Do not share passwords, GitHub tokens, signing keystores, or private keys. Do not install untrusted NCP packages. NCP validation is not publisher authentication and does not establish that a package is safe or compatible.
