# Minecraft 1.26.13.1 patch-layer investigation

Status: STATIC DEX FINDINGS — RUNTIME ACTIVATION NOT PROVEN

Target observed in the NeoCraft device report:

- Package: `com.mojang.minecraftpe`
- Version code: `972601301`
- Base APK SHA-256: `6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916`
- DEX containing the patch layer: `classes5.dex`

This note records static observations from the exported APK. It does not establish that the patch code is executed on device.

## 1. DEX inventory

The diagnostic report identifies five DEX files. The target patch classes are in `classes5.dex`:

- `com.modscraft.mcpepatch.KillerApplication`
- `com.modscraft.mcpepatch.Patches`
- 81 classes under `amogus.*`

The class names alone do not establish the purpose of the obfuscated helpers.

## 2. Patches.patch(Context)

The method is public static and has a substantial DEX body (854 code units in the inspected file). Its referenced APIs and strings show file-oriented behavior:

- obtains `Context.getFilesDir()` and its parent;
- works with the Minecraft paths `games/com.mojang/minecraftpe/external_servers.txt` and `games/com.mojang/minecraftpe/options.txt`;
- reads text files using buffered input;
- uses regular expressions, including the pattern `^\\d+`;
- manipulates collections and writes files back;
- contains the option key `has_dismissed_new_player_flow`;
- contains the token `server` and external server-list-related strings.

The static evidence is consistent with modification/normalization of Minecraft options and external server-list data. The exact transformations and their conditions have not yet been fully reconstructed.

Important: finding this method in DEX does not prove that any class calls it, or that it runs during Minecraft startup. A complete cross-DEX call-site scan and manifest/application-component inspection are still required.

## 3. KillerApplication

Its constructor calls `android.app.Application.<init>()`.

Its static initializer contains reflective access to Android package-manager internals, including:

- `android.app.AppGlobals` and `getInitialPackage`;
- `android.content.pm.PackageInfo` creator/cache-related fields;
- `android.os.Parcel` and `Parcelable.Creator`;
- construction of an `android.content.pm.Signature` from an embedded Base64 certificate whose subject strings identify Mojang AB;
- writes to reflected fields and clearing of a map.

This is strong evidence that the class is designed to affect package-info/signature deserialization or lookup behavior. It is not yet proof of the exact runtime effect, and it is not proof that the class is instantiated. The manifest's `application android:name` and any other instantiation path must be checked.

## 4. What this means for NeoCraft

The inspected patch layer is not evidence of a general-purpose Bedrock mod loader. The observed `Patches.patch(Context)` behavior concerns Android-side files/settings, not registration of new Bedrock blocks, items, entities, recipes, or script APIs.

Do not treat this layer as the NeoMod runtime or assume that its presence makes arbitrary NCP content loadable.

It may still provide useful compatibility clues for launcher integration, particularly around the game's server-list/options files. NeoCraft must not depend on undocumented patch behavior without proving it on the exact APK.

## 5. Remaining verification tasks

1. Decode the binary Android manifest and verify the declared Application class.
2. Scan all DEX files for references to `Patches.patch(Context)` and `KillerApplication`.
3. Reconstruct the full control flow of `Patches.patch(Context)`, including file paths, conditions, and write behavior.
4. Inspect the helper methods in `amogus.*` that are directly called by the two target classes.
5. Inspect native library exports and initialization only if a concrete runtime hook candidate is identified.
6. Design a harmless runtime probe. Do not enable the build in `ReviewedGameBuilds` until the probe passes against this exact package identity and artifact digest.

## 6. Evidence boundary

Static DEX references establish that code and strings are present in the inspected APK. They do not establish execution order, successful reflection on the user's Android version, native compatibility, or in-game mod loading. Those claims require runtime evidence.
