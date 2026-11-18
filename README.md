# NeoCraft Launcher

NeoCraft is an open-source Android launcher for an already-installed Minecraft PE Patched build. The project is developing its own NeoMod package system. Offline/LAN support is a future goal, not a current feature.

## Source and build

The Stage 9 source snapshot is available as [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android GitHub Actions workflow extracts that snapshot and applies the integration sources in [`patches/`](./patches).

The current NCP implementation and Android picker are being developed in [Pull Request #1](https://github.com/MrRehop/NeoCraft-Launcher/pull/1), branch `feature/ncp-package-reader`. The PR is still a draft and has not been merged into `main`.

## Latest verified PR-branch build

- GitHub Actions [run #120](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44330094264) completed successfully on 2026-11-18 from commit `e33cf63f2e79543ecb9b461571014a8e2e877d33`.
- Artifact: `neocraft-debug-apk`, 909,725 bytes (workflow artifact ZIP).
- Artifact ZIP SHA-256: `fce927f42dddc2e3ca83ba92722deac97b0c9d227aab6d977f8180cf5f08cd04`.
- The artifact expires on 2027-02-16 at 14:57:51 UTC.
- CI used JDK 17 and Gradle 8.7. The `:app:assembleDebug` task completed successfully and the artifact was uploaded.
- Test results: NeoMod core 14/14; legacy package reader 14/14; package manager 11/11; game-build identity 9/9; NCP manifest 14/14; NCP ZIP reader 11/11; NCP package store 9/9.
- A minimal [Hello World NCP sample](./examples/hello-world.ncp) is included for device testing.
- The Android screen includes **Проверить NCP-пакет**. It opens the Android document picker, copies a selected archive to private cache with a 128 MiB input limit, validates it with the NCP reader, displays package metadata, and deletes the temporary copy. It does not install or activate the package.
- Run #120 has not yet been tested on the physical Samsung device.

See [device testing](./docs/DEVICE_TESTING.md) for installation and manual checks.

## Current implementation boundaries

- Minecraft package discovery, identity diagnostics, and launch-intent handoff are implemented.
- The reviewed Minecraft build allow-list is intentionally empty. Discovering or launching a game does not establish NeoMod compatibility.
- Minecraft PE Patched 1.26.13.1 (`com.mojang.minecraftpe`, versionCode `972601301`) is recorded as a candidate from the device report only. Its exported APK and signer identity have not been independently re-hashed in the current build environment, and no runtime compatibility probe has passed.
- NCP v1 manifest decoding, read-only ZIP validation, ZIP metadata checks, and a transactional data-only package store are implemented on the PR branch and pass the listed CI tests.
- The Android NCP picker currently validates packages only. The package store is not yet connected to the screen.
- NCP dependency resolution, NCP profile activation, runtime loading into Minecraft, and a version-specific game adapter remain unimplemented.
- The legacy `.nmod` reader is separate from NCP and must not be treated as an NCP reader.
- Offline/LAN multiplayer remains planned, not implemented.

No in-game mod injection, native patching, or Minecraft modification is claimed.
