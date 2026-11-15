# NeoCraft Launcher

NeoCraft is an open-source Android launcher project for Minecraft PE Patched, with the NeoMod modification system and planned offline/LAN support.

## Source snapshot and build

The Stage 9 source snapshot is available in [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android workflow extracts it and applies the reviewed integration patches from [`patches/`](./patches).

The repository-level GitHub Actions workflow at [`.github/workflows/android-apk.yml`](./.github/workflows/android-apk.yml) builds a debug APK and uploads it as the `neocraft-debug-apk` artifact.

## Current verified status

- Latest successful Android debug build: [workflow run #55](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43724522041), completed on 2026-11-15 at 14:29 UTC.
- Run #55 uploaded `neocraft-debug-apk`: an 886,438-byte ZIP containing an 893,464-byte `app-debug.apk`. The artifact expires on 2027-02-13 at 14:28 UTC.
- Verified SHA-256: artifact ZIP `2b789057ef41b15f4e51e803350c2d4545a3d41a00ee6d1e746e82488c7da838`; APK `28fc0987bca1e83306990f3bb4226ba9907e3aaeba50f18847db493ce21098b4`.
- The APK manifest identifies the launcher as `org.neocraft.launcher`, version `0.1.0` (versionCode `1`), minSdk `26`, targetSdk `35`.
- The latest build moves APK/DEX inspection off the UI thread, applies explicit DEX inspection limits, includes the NCP v1 manifest decoder, and runs the core and NCP self-tests before building.
- The shared JSON parser now rejects manifest text over 256 KiB and nesting deeper than 64 containers.
- An earlier physical-device test on 2026-11-15 reported successful Minecraft discovery and launch-intent handoff. Run #55 itself has not yet been retested on the device.
- The reviewed game-build allow-list is intentionally empty. Detecting or launching Minecraft does not mean NeoMod compatibility is confirmed.
- NCP v1 manifest decoding is implemented and self-tested; ZIP package inspection and payload inventory/hash verification remain pending. No in-game mod injection or resource loading is claimed.
- Offline/LAN multiplayer remains planned, not implemented.

## Device test

See [`docs/DEVICE_TESTING.md`](./docs/DEVICE_TESTING.md) for downloading the latest debug artifact, installing it on Android, and sending the diagnostic report back for review.
