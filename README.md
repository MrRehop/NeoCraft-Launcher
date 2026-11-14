# NeoCraft Launcher

NeoCraft is an open-source Android launcher project for Minecraft PE Patched, with the NeoMod modification system and planned offline/LAN support.

## Source snapshot and build

The Stage 9 source snapshot is available in [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android workflow extracts it and applies the reviewed integration patches from [`patches/`](./patches).

The repository-level GitHub Actions workflow at [`.github/workflows/android-apk.yml`](./.github/workflows/android-apk.yml) builds a debug APK and uploads it as the `neocraft-debug-apk` artifact.

## Current verified status

- Latest successful Android debug build: [workflow run #32](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43607196864), completed on 2026-11-14.
- The run uploaded `neocraft-debug-apk` (871,746-byte ZIP artifact; expires 2027-02-12).
- The latest build moves APK/DEX inspection off the UI thread and applies explicit DEX inspection limits.
- PackageManager signer retrieval includes the API 23–27 legacy flag path.
- The APK has not yet been installed or runtime-tested on a physical device.
- The reviewed game-build allow-list is intentionally empty. Detecting Minecraft does not mean NeoMod compatibility is confirmed.
- NeoMod package validation and launcher-side management are in development; no in-game mod injection or resource loading is claimed.
- Offline/LAN multiplayer remains planned, not implemented.

## Device test

See [`docs/DEVICE_TESTING.md`](./docs/DEVICE_TESTING.md) for downloading the latest debug artifact, installing it on Android, and sending the diagnostic report back for review.
