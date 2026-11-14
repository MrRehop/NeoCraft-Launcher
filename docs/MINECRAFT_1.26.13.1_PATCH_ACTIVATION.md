# Minecraft 1.26.13.1 patch activation findings

Status: STATIC CALL PATH CONFIRMED — RUNTIME EFFECT STILL REQUIRES DEVICE TEST

Target APK:
- Package: `com.mojang.minecraftpe`
- Version: `1.26.13.1`
- Version code: `972601301`
- Base APK SHA-256: `6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916`

## 1. Manifest: KillerApplication is not the declared Application class

The binary AndroidManifest.xml was decoded from the exact exported APK.

The `<application>` element declares:
- `android:appComponentFactory="androidx.core.app.CoreComponentFactory"`
- no `android:name` attribute.

The manifest declares `com.mojang.minecraftpe.MainActivity` as the game activity. It does not declare `com.modscraft.mcpepatch.KillerApplication` as the Application class.

A scan of the five DEX files found the `KillerApplication.<init>()` method only in `classes5.dex`, and found no DEX invocation of that constructor and no DEX type instruction referring to the KillerApplication class outside its own definition. This makes manifest-based automatic instantiation unsupported by the evidence. An exotic reflective or native instantiation path is not ruled out, but no such path has been identified.

Consequently, the reflective signature/package-info code in `KillerApplication.<clinit>` must not be treated as active merely because it is packaged in the APK.

## 2. Patches.patch(Context) has a confirmed Java call site

A cross-DEX instruction scan found a direct `invoke-static` reference to:

`com.modscraft.mcpepatch.Patches.patch(android.content.Context)`

The caller is:

`com.mojang.minecraftpe.MainActivity.onCreate(android.os.Bundle)`

The call occurs after `setVolumeControlStream(...)` and before `org.fmod.FMOD.init(Context)` in the inspected method's instruction sequence.

This is stronger evidence than mere class presence: the app's Java startup method contains an explicit call to the patch method. It still does not prove the call succeeds on a running device, because runtime exceptions, filesystem restrictions, or Android-version behavior can affect the result.

## 3. What Patches.patch does according to static references

The method obtains the Minecraft app files directory and its parent, then resolves these relative paths:

- `games/com.mojang/minecraftpe/external_servers.txt`
- `games/com.mojang/minecraftpe/options.txt`

It reads text through buffered readers, performs collection and regular-expression operations, deletes/recreates files, and writes text through buffered writers.

The DEX includes:
- regular expression `^\\d+`;
- token `server`;
- option key `has_dismissed_new_player_flow` with value `1`;
- collection/ordering operations using `LinkedHashSet`, `LinkedHashMap`, and lists.

This strongly indicates Android-side maintenance of the external server list and options file. It is not evidence of registration of Bedrock items, blocks, entities, recipes, or arbitrary mod code.

The exact branch conditions and complete transformation semantics remain to be reconstructed before reproducing any behavior.

## 4. Implications for NeoCraft

Two distinct components must not be conflated:

1. The ModsCraft patch call is genuinely referenced from Minecraft's Java startup path and appears focused on Minecraft data files.
2. The `KillerApplication` signature-reflection class is present in DEX but is not declared as the manifest Application class and has no constructor call found by the static scan.

The first may be useful as a behavioral reference for server-list/options integration. It does not provide a demonstrated general-purpose Bedrock mod-loading API. The second is not currently a reliable integration point.

NeoCraft should continue to launch the user's installed Minecraft package and keep the NeoMod core independent of undocumented ModsCraft behavior. A version-specific adapter must be justified by a harmless probe against this exact APK.

## 5. Remaining work

1. Reconstruct the exact file transformation and error-handling behavior of `Patches.patch(Context)`.
2. Inspect the helper methods directly invoked by the patch routine to understand parsing, path resolution, and logging.
3. Verify at runtime that the call executes successfully and observe the before/after contents of the two files in a controlled test profile.
4. Inspect native integration only if a concrete native extension point is identified; do not infer one from the presence of `libminecraftpe.so`.
5. Keep Minecraft 1.26.13.1 outside the NeoMod compatibility allow-list until the intended NeoMod runtime probe passes.

## Evidence limits

The manifest and DEX scan establish declarations and static call references in this APK. They do not prove successful runtime execution, exact filesystem outcomes, native ABI compatibility, or in-game mod loading.
