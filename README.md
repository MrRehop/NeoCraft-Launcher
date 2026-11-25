# NeoCraft Launcher

NeoCraft is an open-source Android launcher for an already-installed Minecraft PE Patched build. The project is developing its own NeoMod package system. Offline/LAN support is a future goal, not a current feature.

## Source and build

The Stage 9 source snapshot is available as [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android GitHub Actions workflow extracts that snapshot and applies the integration sources in [`patches/`](./patches).

The current NCP implementation and Android picker are being developed in [Pull Request #1](https://github.com/MrRehop/NeoCraft-Launcher/pull/1), branch `feature/ncp-package-reader`. The PR is still a draft and has not been merged into `main`.

## Latest verified PR-branch build

- GitHub Actions [run #204](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45632699665), commit `eb8087d911714c618da55b54cdd809aab0abfc2a`, completed successfully on 2026-11-25.
- Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45632699665/artifacts/15032386010), 945,694 bytes (workflow artifact ZIP). SHA-256: `9c6427b8fcd69c2f90a5b2847ae24ebcbcf791c1e47094b5d6557a56196e0f9d`. Artifact expires 2027-02-23 14:18:57 UTC.
- CI used JDK 17 and Gradle 8.7. The `:app:assembleDebug` task completed successfully and the artifact was uploaded.
- The run passed 12 self-test groups (150/150 cases): NeoMod core 14/14; legacy package reader 14/14; package manager 11/11; game-build identity 9/9; NCP manifest 14/14; NCP version range 12/12; NCP ZIP reader 12/12; NCP package store 10/10; NCP dependency resolver 18/18; installed-package plan 7/7; profile snapshot/game-build matcher 13/13; profile store 16/16.
- A minimal [Hello World NCP sample](./examples/hello-world.ncp) is included for device testing.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. It stages selected archives in private cache with a 128 MiB input limit. Stored packages are not activated or loaded into Minecraft. The UI compiles in run #204; physical-device verification is still pending.
- Run #204 has not yet been tested on the physical Samsung device.
- The current PR branch adds a three-argument `NcpProfileStore.load(profileId, plan, currentBuild)` path that combines plan validation with exact Minecraft build identity matching. The four new store integration tests passed in run #204.

See [device testing](./docs/DEVICE_TESTING.md) for installation and manual checks.

## Current implementation boundaries

- Minecraft package discovery, identity diagnostics, and launch-intent handoff are implemented.
- The reviewed Minecraft build allow-list is intentionally empty. Discovering or launching a game does not establish NeoMod compatibility.
- Minecraft PE Patched 1.26.13.1 (`com.mojang.minecraftpe`, versionCode `972601301`) is recorded as a candidate from the device report only. Its exported APK and signer identity have not been independently re-hashed in the current build environment, and no runtime compatibility probe has passed.
- NCP v1 manifest decoding, read-only ZIP validation, ZIP metadata checks, and a transactional data-only package store are implemented on the PR branch and pass the listed CI tests.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. Stored packages are not activated or loaded into Minecraft.
- The NCP dependency resolver's 18 self-tests and the seven `NcpInstalledPackagePlanResolver` tests passed in run #204. The resolver caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. The installed-package plan bridge maps resolved manifests back to verified installed-store records and verifies that a selected compatible version maps to its exact installed directory when multiple versions coexist. The bridge is not surfaced in profile UI. `NcpProfileGameBuildMatcher` compares a saved profile against the current Android package name, PackageManager versionCode, and APK SHA-256; its 13 snapshot/matcher tests passed in run #204. All 16 current profile-store tests, including the four game-build-checked load integration tests, passed in run #204. NCP profile activation, runtime loading into Minecraft, and a version-specific game adapter remain unimplemented.
- The legacy `.nmod` reader is separate from NCP and must not be treated as an NCP reader.
- Offline/LAN multiplayer remains planned, not implemented.

No in-game mod injection, native patching, or Minecraft modification is claimed.


- The latest verified APK listed above includes the NCP data-only package-store UI, installed-package plan bridge, profile-root symlink protection, and profile game-build matcher. It includes the game-build-checked profile-store load overload and its four passing integration tests, but does not include a profile-management screen.
