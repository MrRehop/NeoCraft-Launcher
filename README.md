# NeoCraft Launcher

NeoCraft is an open-source Android launcher for an already-installed Minecraft PE Patched build. The project is developing its own NeoMod package system. Offline/LAN support is a future goal, not a current feature.

## Source and build

The Stage 9 source snapshot is available as [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android GitHub Actions workflow extracts that snapshot and applies the integration sources in [`patches/`](./patches).

The current NCP implementation and Android picker are being developed in [Pull Request #1](https://github.com/MrRehop/NeoCraft-Launcher/pull/1), branch `feature/ncp-package-reader`. The PR is still a draft and has not been merged into `main`.

## Latest verified PR-branch build

- GitHub Actions [run #186](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45116369676), commit `ddfc2ffaef00b5aca2349ba2d4ed7e1b2b3a4ef6`, completed successfully on 2026-11-23.
- Artifact: [`neocraft-debug-apk`](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45116369676/artifacts/14791274577), 944,954 bytes (workflow artifact ZIP). SHA-256: `54ee07abfc32f7230ffc768ce92d813409a6edd743459b7a792ae90245782d37`. It expires on 2027-02-21 at 05:13:03 UTC.
- CI used JDK 17 and Gradle 8.7. The `:app:assembleDebug` task completed successfully and the artifact was uploaded.
- The run passed 12 self-test groups (138/138 cases): NeoMod core 14/14; legacy package reader 14/14; package manager 11/11; game-build identity 9/9; NCP manifest 14/14; NCP version range 12/12; NCP ZIP reader 12/12; NCP package store 10/10; NCP dependency resolver 18/18; installed-package plan 7/7; profile snapshot 8/8; profile store 9/9.
- A minimal [Hello World NCP sample](./examples/hello-world.ncp) is included for device testing.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. It stages selected archives in private cache with a 128 MiB input limit. Stored packages are not activated or loaded into Minecraft. The UI compiles in run #186; physical-device verification is still pending.
- Run #186 has not yet been tested on the physical Samsung device.
- The current PR branch adds a profile-root symlink regression test and an explicit profile game-build matcher with five new tests after run #186. Those follow-up changes are present in source but are not covered by that APK or its CI results.

See [device testing](./docs/DEVICE_TESTING.md) for installation and manual checks.

## Current implementation boundaries

- Minecraft package discovery, identity diagnostics, and launch-intent handoff are implemented.
- The reviewed Minecraft build allow-list is intentionally empty. Discovering or launching a game does not establish NeoMod compatibility.
- Minecraft PE Patched 1.26.13.1 (`com.mojang.minecraftpe`, versionCode `972601301`) is recorded as a candidate from the device report only. Its exported APK and signer identity have not been independently re-hashed in the current build environment, and no runtime compatibility probe has passed.
- NCP v1 manifest decoding, read-only ZIP validation, ZIP metadata checks, and a transactional data-only package store are implemented on the PR branch and pass the listed CI tests.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. Stored packages are not activated or loaded into Minecraft.
- The NCP dependency resolver's 18 self-tests and the seven `NcpInstalledPackagePlanResolver` tests passed in run #186. The resolver caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. The installed-package plan bridge maps resolved manifests back to verified installed-store records and verifies that a selected compatible version maps to its exact installed directory when multiple versions coexist. The bridge is not surfaced in profile UI. `NcpProfileGameBuildMatcher` now compares a saved profile against the current Android package name, PackageManager versionCode, and APK SHA-256; its five new tests are not yet covered by a successful CI run. NCP profile activation, runtime loading into Minecraft, and a version-specific game adapter remain unimplemented.
- The legacy `.nmod` reader is separate from NCP and must not be treated as an NCP reader.
- Offline/LAN multiplayer remains planned, not implemented.

No in-game mod injection, native patching, or Minecraft modification is claimed.


- The latest verified APK listed above includes the NCP data-only package-store UI and installed-package plan bridge, but predates the profile-root symlink follow-up and profile game-build matcher.
