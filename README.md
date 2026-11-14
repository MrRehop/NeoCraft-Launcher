# NeoCraft Launcher

NeoCraft is an open-source Android launcher project for Minecraft PE Patched, with the NeoMod modification system and planned offline/LAN support.

## Source snapshot

The Stage 9 source snapshot is available in [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). It contains the Gradle project under the `NeoCraft/` directory.

## Build

The repository-level GitHub Actions workflow at [`.github/workflows/android-apk.yml`](./.github/workflows/android-apk.yml) builds the debug APK from the `NeoCraft/` project directory and uploads it as an artifact when successful.

## Current status

- Stage 9 source snapshot is uploaded.
- Android APK CI workflow is configured.
- No APK build is claimed until GitHub Actions completes successfully.
- Offline/LAN multiplayer remains planned, not implemented.
