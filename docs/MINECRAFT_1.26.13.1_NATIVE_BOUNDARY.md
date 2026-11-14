# Minecraft 1.26.13.1: confirmed patch call and native boundary

Status: STATIC ANALYSIS — NO NEO MOD RUNTIME PROBE YET

Target: `com.mojang.minecraftpe`, version code `972601301`, base APK SHA-256 `6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916`.

## Confirmed call path

The cross-DEX instruction scan found exactly one direct call reference to `Patches.patch(Context)` in the inspected DEX set:

- caller: `com.mojang.minecraftpe.MainActivity.onCreate(Bundle)`;
- opcode: `invoke-static`;
- instruction offset in the method: 141 code units;
- call position: after `setVolumeControlStream(...)`, before `FMOD.init(Context)`.

This confirms that the method is wired into the Java startup sequence in this APK. It does not prove that the call completes successfully on a live device.

## Manifest and KillerApplication

The decoded manifest's `<application>` element has no `android:name` attribute. It declares `androidx.core.app.CoreComponentFactory` as `appComponentFactory`, and `com.mojang.minecraftpe.MainActivity` as the main activity.

Across all five DEX files, the `KillerApplication.<init>()` method is defined in `classes5.dex`, but the scan found no invocation of that constructor and no type instruction referencing the class from another class. The manifest does not declare it as the Application class.

Therefore there is no identified normal Android startup path for `KillerApplication`. Its static initializer must not be assumed to execute. A reflective/native path is theoretically possible but has not been observed.

## Native library inventory

The ARM64 `libminecraftpe.so` was inspected as an ELF shared object. It exports `JNI_OnLoad` and numerous named JNI entry points for Minecraft Android classes, including methods on `MainActivity`, `WorldRecovery`, `NetworkMonitor`, input, webview, and store components.

The exported names confirm a Java/native boundary used by the game. They do not by themselves expose a supported third-party mod API. No NeoMod registration function or documented extension ABI has been identified from this symbol inventory.

The library also depends on Android graphics/system libraries and bundled game services such as FMOD, PlayFab multiplayer, HttpClient, and MAE SDK. These dependencies are not mod APIs.

## Implications

The most concrete behavior exposed by the ModsCraft patch layer is the Java startup call that updates Minecraft's external server list and options files. This is a possible reference for NeoCraft's launcher-side server/profile management, not a route to register arbitrary Bedrock content.

The native library has a JNI boundary, but exported JNI functions are game-owned Android bridge methods. NeoCraft must not hook arbitrary native addresses or treat JNI_OnLoad as a plugin interface.

## Next proof required

1. Recover exact branch and transformation behavior of `Patches.patch(Context)`.
2. Test the startup patch on a controlled device/profile and capture before/after file state.
3. Identify a legitimate, stable content extension mechanism supported by the installed game or a runtime that can be integrated without replacing the installed game.
4. If no such extension point exists, keep NeoMod's package manager and profiles useful independently while explicitly marking in-game content injection as unsupported for this build.

No compatibility allow-list entry is justified yet.
