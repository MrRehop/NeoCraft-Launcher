# NeoCraft Launcher

NeoCraft is an open-source Android launcher project for Minecraft PE Patched, with the NeoMod modification system and planned offline/LAN support.

## Source snapshot and build

The Stage 9 source snapshot is available in [`NeoCraft-NeoMod-Stage9-clean.zip`](./NeoCraft-NeoMod-Stage9-clean.zip). The Android workflow extracts it and applies the reviewed integration patches from [`patches/`](./patches).

The repository-level GitHub Actions workflow at [`.github/workflows/android-apk.yml`](./.github/workflows/android-apk.yml) builds a debug APK and uploads it as the `neocraft-debug-apk` artifact.

## Current verified status

- Latest successful Android debug build: [workflow run #54](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/43724339536), completed on 2026-11-15 at 14:27 UTC.
- Run #53 uploaded `neocraft-debug-apk`: an 886,438-byte ZIP containing an 893,460-byte `app-debug.apk`. The artifact expires on 2027-02-13 at 14:26 UTC.
- Verified SHA-256: artifact ZIP `c90a8580b72d1ef23c6ad4bb7b2cd9507cce2c09fd86c3860964fea74862365c`; APK `31d5c996cf4da5664f34a60221d30f9a1d35338e45e094aa77f216c78c023a1c`.
- The APK manifest identifies the launcher as `org.neocraft.launcher`, version `0.1.0` (versionCode `1`), minSdk `26`, targetSdk `35`.
- The latest build moves APK/DEX inspection off the UI thread, applies explicit DEX inspection limits, includes the NCP v1 manifest decoder, and runs the core and NCP self-tests before building.
- The shared JSON parser now rejects manifest text over 256 KiB and nesting deeper than 64 containers.
- An earlier physical-device test on 2026-11-15 reported successful Minecraft discovery and launch-intent handoff. Run #53 itself has not yet been retested on the device.
- The reviewed game-build allow-list is intentionally empty. Detecting or launching Minecraft does not mean NeoMod compatibility is confirmed.
- NCP v1 manifest decoding is implemented and self-tested; ZIP package inspection and payload inventory/hash verification remain pending. No in-game mod injection or resource loading is claimed.
- Offline/LAN multiplayer remains planned, not implemented.

## Device test

See [`docs/DEVICE_TESTING.md`](./docs/DEVICE_TESTING.md) for downloading the latest debug artifact, installing it on Android, and sending the diagnostic report back for review.
