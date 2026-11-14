# NeoCraft Launcher

NeoCraft is an open-source Android launcher project for Minecraft PE Patched, with the NeoMod modification system and planned offline/LAN support.

## Source snapshot and build

The Stage 9 source snapshot is available in [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android workflow extracts it and applies the reviewed integration patches from [`patches/`](./patches).

The repository-level GitHub Actions workflow at [`.github/workflows/android-apk.yml`](./.github/workflows/android-apk.yml) builds a debug APK and uploads it as the `neocraft-debug-apk` artifact.

## Current verified status

- The Android debug APK build completed successfully in workflow run [#27](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43575756710) on 2026-11-14.
- The run uploaded the `neocraft-debug-apk` artifact (869,744 bytes; artifact expiry: 2027-02-12).
- The APK has not yet been installed or runtime-tested on a physical device.
- The reviewed game-build allow-list is intentionally empty. Detecting Minecraft does not mean NeoMod compatibility is confirmed.
- NeoMod package validation and launcher-side management are in development; no in-game mod injection or resource loading is claimed.
- Offline/LAN multiplayer remains planned, not implemented.

## Device test

See [`docs/DEVICE_TESTING.md`](./docs/DEVICE_TESTING.md) for downloading the debug artifact, installing it on Android, and sending the diagnostic report back for review.
