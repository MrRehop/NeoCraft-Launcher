# Minecraft PE Patched 1.26.13.1 — static build review

Status: IDENTIFIED CANDIDATE; NOT REVIEWED FOR NEOMOD RUNTIME COMPATIBILITY

This record captures the user-provided NeoCraft diagnostic report. It is evidence about the APK structure and identifiers, not proof that NeoMod can load content into this game build.

## 1. Build identity from the diagnostic report

- Android package: `com.mojang.minecraftpe`
- Version name: `1.26.13.1`
- Version code: `972601301`
- Signer certificate SHA-256: `b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890`
- `base.apk` SHA-256: `6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916`

The report says that no reviewed build record matches the package name and version code. The reviewed-build allow-list must therefore remain unchanged until the review and runtime-probe requirements are met.

## 2. Reported APK structure

The report lists five DEX files: `classes.dex` through `classes5.dex`.

Reported native libraries include both `arm64-v8a` and `armeabi-v7a` variants of `libminecraftpe.so`, plus supporting libraries such as `libPlayFabMultiplayer.so`, `libHttpClient.Android.so`, and `libfmod.so`.

The Android manifest report lists `com.mojang.minecraftpe.MainActivity` as an activity and the package's `ImportService` as a service. It also lists Wi-Fi/network permissions including `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_NETWORK_STATE`, and `INTERNET`. These permissions are only declared capabilities; they do not prove that LAN discovery or multiplayer works.

## 3. Patch-related DEX observations

The diagnostic report attributes 83 classes to `classes5.dex`, including:
- 81 classes in package `amogus`;
- `com.modscraft.mcpepatch.KillerApplication`;
- `com.modscraft.mcpepatch.Patches`.

It reports methods on `KillerApplication` including `<clinit>` and `<init>`, and methods on `Patches` including `<clinit>` and `patch`.

The report also states that the manifest's Application class is the Android default (`android.app.Application`). This means the class name `KillerApplication` alone is not evidence that Android instantiates it as the app's Application class.

The report does **not** provide a call graph, complete disassembly, string/reference analysis, or runtime trace proving when or whether `Patches.patch()` is invoked. It also does not prove that this Java patch code modifies or hooks `libminecraftpe.so`. Those questions remain open.

## 4. What can and cannot be concluded

Supported by the supplied report:
- the APK identifies as package `com.mojang.minecraftpe`, version `1.26.13.1`, version code `972601301`;
- the report records the signer and base APK digests above;
- the report lists patch-related Java classes in `classes5.dex`;
- the report lists native Minecraft libraries for ARM64 and ARMv7;
- the report lists Wi-Fi and network permissions.

Not established:
- the APK's full patch execution path;
- whether `Patches.patch()` is invoked, and from which caller;
- whether the patch uses reflection, JNI, native symbol lookup, or binary modification;
- whether any stable NeoMod extension interface exists;
- whether NCP packages can safely map to game content;
- whether offline/LAN multiplayer works with NeoCraft or NeoMod;
- runtime compatibility of this exact build.

No compatibility allow-list entry or runtime adapter support claim should be added on the basis of this report alone.

## 5. Next static-analysis evidence needed

To continue responsibly, inspect the actual DEX bytecode or a complete disassembly for:
1. all call sites referencing `com.modscraft.mcpepatch.Patches.patch`;
2. class initializers and application/activity lifecycle code that may trigger the patch;
3. strings, reflection targets, method/field descriptors, and JNI declarations used by the patch;
4. any references to `System.loadLibrary`, `Runtime.load`, native methods, or `libminecraftpe.so`;
5. patch error handling and whether it changes files or process memory.

A method body excerpt without its caller is insufficient to establish the execution path. Any native analysis must be read-only and performed on a legally obtained local copy; do not alter the user's installed game during this review.

## 6. NeoCraft architecture consequence

Keep the version-specific integration behind a separate adapter boundary:

`Minecraft build identity -> reviewed adapter -> NeoMod runtime capability interface`

The generic NCP parser, package store, dependency resolver, and profile store must remain independent of this build-specific investigation. Do not add hard-coded offsets, invoke undocumented patch methods, or activate packages until the adapter's capabilities and a harmless runtime probe have been independently verified.
