# Candidate Minecraft build evidence

Status: DISCOVERED — NOT REVIEWED — NOT ENABLED FOR NEOMOD

This record captures a device-reported identity for investigation. It is not an
entry in the runtime allow-list and must not be used to enable mod capabilities.

## Reported package identity

- Package: `com.mojang.minecraftpe`
- Version name: `1.26.13.1`
- Version code: `972601301`
- Current signer certificate SHA-256:
  `b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890`
- Installed artifact: `base.apk`
- `base.apk` SHA-256:
  `6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916`
- Additional split APKs: none reported by the diagnostic output

## What this proves

The NeoCraft Android diagnostic code was able to read the installed package
identity and calculate a SHA-256 digest for the readable `base.apk` file.
The digest can identify this exact APK byte sequence.

## What this does not prove

- It does not prove that this build's internal game APIs or native interfaces
  match any NeoMod adapter.
- It does not prove that mod installation or loading works.
- It does not prove offline/LAN functionality.
- It does not establish that no split APKs exist beyond those exposed to the
  diagnostic code.
- It is a device-reported observation and has not yet been independently
  reproduced from an exported APK.

## Review work still required

1. Export or otherwise obtain the exact installed APK for offline inspection.
2. Inspect its manifest, DEX inventory, native libraries and relevant assets.
3. Identify the intended NeoMod integration points and compare them with the
   exact binary.
4. Implement and test a harmless compatibility probe before considering any
   runtime adapter.
5. Only after successful review, create a `SupportedGameBuild` record with the
   exact package, version code, signer and required artifact digest(s).

Until then, keep `ReviewedGameBuilds` empty for this candidate.
