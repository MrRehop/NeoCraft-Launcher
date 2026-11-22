# NeoCraft Launcher

NeoCraft is an open-source Android launcher for an already-installed Minecraft PE Patched build. The project is developing its own NeoMod package system. Offline/LAN support is a future goal, not a current feature.

## Source and build

The Stage 9 source snapshot is available as [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android GitHub Actions workflow extracts that snapshot and applies the integration sources in [`patches/`](./patches).

The current NCP implementation and Android picker are being developed in [Pull Request #1](https://github.com/MrRehop/NeoCraft-Launcher/pull/1), branch `feature/ncp-package-reader`. The PR is still a draft and has not been merged into `main`.

## Latest verified PR-branch build

- GitHub Actions [run #157](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44866864793), commit `b40401ee5076752dd138fa2beda84b0a1636e2a9`, completed successfully on 2026-11-21.
- Artifact: `neocraft-debug-apk`, 925,935 bytes (workflow artifact ZIP). SHA-256: `9d14a50fdf2e0a32129b762f08e5b288ef9dd97ab7d2a1602be1cc8dd293262c`. It expires on 2027-02-19 at 04:35:36 UTC.

- CI used JDK 17 and Gradle 8.7. The `:app:assembleDebug` task completed successfully and the artifact was uploaded.
- Test results: NeoMod core 14/14; legacy package reader 14/14; package manager 11/11; game-build identity 9/9; NCP manifest 14/14; NCP version range 12/12; NCP ZIP reader 12/12; NCP package store 10/10; NCP dependency resolver 18/18.
- A minimal [Hello World NCP sample](./examples/hello-world.ncp) is included for device testing.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. It stages selected archives in private cache with a 128 MiB input limit. Stored packages are not activated or loaded into Minecraft. This UI integration was committed after run #157 and awaits CI plus physical-device verification.
- Run #157 has not yet been tested on the physical Samsung device.

See [device testing](./docs/DEVICE_TESTING.md) for installation and manual checks.

## Current implementation boundaries

- Minecraft package discovery, identity diagnostics, and launch-intent handoff are implemented.
- The reviewed Minecraft build allow-list is intentionally empty. Discovering or launching a game does not establish NeoMod compatibility.
- Minecraft PE Patched 1.26.13.1 (`com.mojang.minecraftpe`, versionCode `972601301`) is recorded as a candidate from the device report only. Its exported APK and signer identity have not been independently re-hashed in the current build environment, and no runtime compatibility probe has passed.
- NCP v1 manifest decoding, read-only ZIP validation, ZIP metadata checks, and a transactional data-only package store are implemented on the PR branch and pass the listed CI tests.
- The Android NCP screen provides read-only validation, explicit data-only installation into NeoCraft private storage, listing, and uninstall. Stored packages are not activated or loaded into Minecraft.
- The NCP dependency resolver's 18 self-tests passed in run #157. The earlier run #152 package-store fixture failure was corrected in commit `783707a6`; the package-store suite passed 10/10 in run #156 and again in #157. CI run #157 on commit `b40401ee` passed and built the debug APK. The resolver caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. A new `NcpInstalledPackagePlanResolver` maps resolved manifests back to verified installed-store records; its five tests are wired into CI but not yet verified. The bridge is not surfaced in profile UI. NCP profile activation, runtime loading into Minecraft, and a version-specific game adapter remain unimplemented.
- The legacy `.nmod` reader is separate from NCP and must not be treated as an NCP reader.
- Offline/LAN multiplayer remains planned, not implemented.

No in-game mod injection, native patching, or Minecraft modification is claimed.


- The latest verified APK listed above predates the Android data-only package-store UI integration and the installed-package plan bridge.
