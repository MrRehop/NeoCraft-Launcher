# Candidate game build: Minecraft PE Patched 1.26.13.1

**Status: identified on device; NeoMod compatibility is not approved.**

This record preserves the diagnostic evidence supplied from the user's Android device. It is a candidate identity record, not a reviewed compatibility entry.

## Package identity

| Field | Value |
|---|---|
| Package name | `com.mojang.minecraftpe` |
| Version name | `1.26.13.1` |
| Android `versionCode` | `972601301` |
| Current signer certificate SHA-256 | `b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890` |
| Installed `base.apk` SHA-256 | `6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916` |

The `versionCode` above is the value reported by Android PackageManager through NeoCraft's device diagnostic. It is not inferred from the version name.

## APK structure reported by NeoCraft

- One inspected APK entry: `base.apk`.
- DEX entries: `classes.dex`, `classes2.dex`, `classes3.dex`, `classes4.dex`, `classes5.dex`.
- Native libraries are present for both `arm64-v8a` and `armeabi-v7a`, including `libminecraftpe.so`.
- The manifest inventory reports `com.mojang.minecraftpe.MainActivity` and `com.mojang.minecraftpe.ImportService`.
- `classes2.dex` contains Mojang package classes including `com.mojang.minecraftpe.MainActivity`.
- `classes5.dex` contains `com.modscraft.mcpepatch.KillerApplication` and `com.modscraft.mcpepatch.Patches`.
- The diagnostic scanner identified a static initializer and a `Patches.patch` method in the latter class.

The structural scan establishes that these class and method names are present in the reported DEX inventory. It does **not** establish the patch method's complete behavior, the exact targets it modifies, or whether it exposes a stable extension API.

## Reported Android permissions

The diagnostic report listed network state/Wi-Fi access, multicast change, Internet, foreground service, notifications, external storage, vibration, wake lock, billing/license, Firebase messaging, and a Mojang dynamic-receiver permission. This is an inventory only; it is not a statement that NeoCraft needs or should request these permissions.

## Compatibility decision

**Do not add this build to `ReviewedGameBuilds.registry` yet.**

The device report itself says:

```text
NeoMod compatibility: НЕ ПОДТВЕРЖДЕНА:
No reviewed build record matches packageName and versionCode
```

The repository's reviewed-build allow-list remains intentionally empty. Matching package name, version code, signer, or APK digest is necessary identity evidence, but is not sufficient proof that NeoMod can safely load content into this game build.

## Remaining review work

1. Independently hash the uploaded original APK and compare its digest with the device-reported `base.apk` digest. The uploaded file and installed base APK must not be assumed identical solely because their reported sizes match.
2. Inspect the exact DEX bytecode for `com.modscraft.mcpepatch.Patches`, including its call sites, reflection, class loading, and native/JNI interactions.
3. Identify which game assets, Java classes, native libraries, or runtime hooks the patcher touches.
4. Define a narrow, versioned NeoMod adapter contract only after those targets are understood.
5. Test a harmless sample package and verify clean failure/rollback before considering any compatibility approval.

## Evidence provenance and limits

- Primary evidence: the NeoCraft diagnostic report pasted by the user in the development conversation.
- The original APK `minecraft-26-13.apk` is present in the user's ChatGPT Library (452,964,617 bytes; uploaded 2026-11-14). Its bytes were not independently re-hashed as part of this documentation change.
- DEX class/method names and APK inventory in this record are transcribed from the device report; no decompilation or bytecode control-flow analysis is claimed here.
- No game modification, injection, resource loading, offline play, or LAN play has been demonstrated by this evidence.
