# NeoCraft Launcher

NeoCraft is an open-source Android launcher for an already-installed Minecraft PE Patched build. The project is developing its own NeoMod package system. Offline/LAN support is a future goal, not a current feature.

## Source and build

The Stage 9 source snapshot is available as [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android GitHub Actions workflow extracts that snapshot and applies the integration sources in [`patches/`](./patches).

The current NCP implementation and Android picker are being developed in [Pull Request #1](https://github.com/MrRehop/NeoCraft-Launcher/pull/1), branch `feature/ncp-package-reader`. The PR is still a draft and has not been merged into `main`.

## Latest verified PR-branch build

- GitHub Actions [run #212](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45836074866), commit dd2c0dd286eb9fab3ee66d8753bc0ad92369e2e5, completed successfully on 2026-11-26.
- Artifact: [neocraft-debug-apk](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45836074866/artifacts/15125156968), 950,953 bytes (workflow artifact ZIP). SHA-256: a904e7ff10e660920c129862e23df12ca362c0c9ec217e0d4d32ec5f2a1450ab. Artifact expires 2027-02-24 14:08:58 UTC.
- CI used JDK 17 and Gradle 8.7. Android :app:assembleDebug completed successfully.
- The run passed 12 self-test groups (150/150 cases): NeoMod core 14/14; legacy package reader 14/14; package manager 11/11; game-build identity 9/9; NCP manifest 14/14; NCP version range 12/12; NCP ZIP reader 12/12; NCP package store 10/10; NCP dependency resolver 18/18; installed-package plan 7/7; profile snapshot/game-build matcher 13/13; profile store 16/16.
- A minimal [Hello World NCP sample](./examples/hello-world.ncp) is included for device testing.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. Stored packages are not activated or loaded into Minecraft.
- The Android profile browser lists structurally valid saved profiles, displays stored metadata, and supports confirmation-based deletion of only the selected `.ncp-profile` file.
- The Android UI source now includes an initial **Создать профиль NeoMod** flow (commit `ad52e836b199facb29b72cdd085b3c44826f1dc3`): it rechecks the installed Minecraft package and `base.apk` SHA-256, lets the user select one installed version per NCP package ID, resolves mandatory dependencies through `NcpProfileComposer`, and saves a metadata-only snapshot. The UI currently supplies NeoMod API version `1.0.0`. It does not activate packages or modify Minecraft. CI and physical-device verification for this new flow are pending.
- `NcpProfileComposer` obtains a fresh integrity-checked package-store listing, resolves exact requested package versions and transitive mandatory dependencies, then returns a validated snapshot and plan for `NcpProfileStore.save()`. Its eight self-tests are configured in the Android workflow; execution for the new UI commit is not yet confirmed.
- Run #212 compile-verifies the profile deletion UI. The Debug APK is from the unmerged feature/ncp-package-reader branch and has not been tested on the physical Samsung device.

See [device testing](./docs/DEVICE_TESTING.md) for installation and manual checks.

## Current implementation boundaries

- Minecraft package discovery, identity diagnostics, and launch-intent handoff are implemented.
- The reviewed Minecraft build allow-list is intentionally empty. Discovering or launching a game does not establish NeoMod compatibility.
- Minecraft PE Patched 1.26.13.1 remains an unreviewed candidate. The archived APK file was independently hashed and parsed: SHA-256 6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916; manifest package com.mojang.minecraftpe, versionName 1.26.13.1, versionCode 972601301; certificate fingerprint extracted from its APK v2/v3 signing block b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890. This static evidence matches a user-provided on-device NeoCraft diagnostic report dated 2026-11-15, preserved in the Stage10 device-evidence archive. That report records PackageManager versionCode 972601301 and the same signer-certificate and base.apk SHA-256 digests. The report has not been freshly re-collected from the phone in this run. The separately mentioned number 792601301 does not match the archived manifest or that prior device report and remains unverified. No runtime compatibility probe has passed.
- NCP v1 manifest decoding, read-only ZIP validation, ZIP metadata checks, and a transactional data-only package store are implemented on the PR branch and pass the listed CI tests.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. Stored packages are not activated or loaded into Minecraft.
- The NCP dependency resolver's 18 self-tests and the seven `NcpInstalledPackagePlanResolver` tests passed in run #208. The resolver caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. The installed-package plan bridge maps resolved manifests back to verified installed-store records and verifies that a selected compatible version maps to its exact installed directory when multiple versions coexist. The bridge is not surfaced in profile UI. `NcpProfileGameBuildMatcher` compares a saved profile against the current Android package name, PackageManager versionCode, and APK SHA-256; its 13 snapshot/matcher tests passed in run #208. All 16 current profile-store tests, including the four game-build-checked load integration tests, passed in run #208. NCP profile activation, runtime loading into Minecraft, and a version-specific game adapter remain unimplemented.
- The legacy `.nmod` reader is separate from NCP and must not be treated as an NCP reader.
- Offline/LAN multiplayer remains planned, not implemented.

No in-game mod injection, native patching, or Minecraft modification is claimed.


- The latest verified APK (run #212) includes the NCP data-only package-store UI, installed-package plan bridge, profile-root symlink protection, profile game-build matcher, read-only profile browser, and confirmation-based profile metadata deletion. It predates the new profile-creation UI source commit `ad52e836b199facb29b72cdd085b3c44826f1dc3`; editing and activation remain unimplemented.
