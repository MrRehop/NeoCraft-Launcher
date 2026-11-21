# NeoCraft Launcher

NeoCraft is an open-source Android launcher for an already-installed Minecraft PE Patched build. The project is developing its own NeoMod package system. Offline/LAN support is a future goal, not a current feature.

## Source and build

The Stage 9 source snapshot is available as [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android GitHub Actions workflow extracts that snapshot and applies the integration sources in [`patches/`](./patches).

The current NCP implementation and Android picker are being developed in [Pull Request #1](https://github.com/MrRehop/NeoCraft-Launcher/pull/1), branch `feature/ncp-package-reader`. The PR is still a draft and has not been merged into `main`.

## Latest verified PR-branch build

- GitHub Actions [run #156](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44866793723), commit `d7cd370192cbd0be91d652e1676d56f0146dca2a`, completed successfully on 2026-11-21.
- Artifact: `neocraft-debug-apk`, 925,933 bytes (workflow artifact ZIP). SHA-256: `3258e7314369ca79478c5b7b7da38d2f3592e7acabe0e58690edad4bfc17a561`. It expires on 2027-02-19 at 04:34:46 UTC.


- CI used JDK 17 and Gradle 8.7. The `:app:assembleDebug` task completed successfully and the artifact was uploaded.
- Test results: NeoMod core 14/14; legacy package reader 14/14; package manager 11/11; game-build identity 9/9; NCP manifest 14/14; NCP version range 12/12; NCP ZIP reader 12/12; NCP package store 9/9.
- A minimal [Hello World NCP sample](./examples/hello-world.ncp) is included for device testing.
- The Android screen includes **Проверить NCP-пакет**. It opens the Android document picker, copies a selected archive to private cache with a 128 MiB input limit, validates it with the NCP reader, displays package metadata, and deletes the temporary copy. It does not install or activate the package.
- Run #156 has not yet been tested on the physical Samsung device.

See [device testing](./docs/DEVICE_TESTING.md) for installation and manual checks.

## Current implementation boundaries

- Minecraft package discovery, identity diagnostics, and launch-intent handoff are implemented.
- The reviewed Minecraft build allow-list is intentionally empty. Discovering or launching a game does not establish NeoMod compatibility.
- Minecraft PE Patched 1.26.13.1 (`com.mojang.minecraftpe`, versionCode `972601301`) is recorded as a candidate from the device report only. Its exported APK and signer identity have not been independently re-hashed in the current build environment, and no runtime compatibility probe has passed.
- NCP v1 manifest decoding, read-only ZIP validation, ZIP metadata checks, and a transactional data-only package store are implemented on the PR branch and pass the listed CI tests.
- The Android NCP picker currently validates packages only. The package store is not yet connected to the screen.
- The NCP dependency resolver's 18 self-tests passed in run #156. The earlier run #152 package-store fixture failure was corrected in commit `783707a6`; run #156 also confirms the package-store tests pass. The current branch head `b40401ee` changes only the search-state-limit test fixture to reduce its candidate count while still exercising the 10,000-state limit; CI run #157 is pending. The resolver caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. It is not connected to the package store or UI. NCP profile activation, runtime loading into Minecraft, and a version-specific game adapter remain unimplemented.
- The legacy `.nmod` reader is separate from NCP and must not be treated as an NCP reader.
- Offline/LAN multiplayer remains planned, not implemented.

No in-game mod injection, native patching, or Minecraft modification is claimed.
