# NeoMod architecture baseline

Status: NCP PACKAGE CORE IMPLEMENTED ON PR BRANCH; ANDROID VALIDATION UI BUILDS; RUNTIME NOT IMPLEMENTED

This document records the current architectural direction for NeoCraft. Successful package validation and Android compilation do not prove Minecraft runtime integration.

## 1. Product boundary

NeoCraft launches the user's already-installed Minecraft Bedrock package. It does not bundle a separate Minecraft distribution and does not replace the game with a fork.

NeoMod is a separate modification platform. It must not depend on importing `.mcpack` or `.mcaddon` as its native package format.

The launcher, package manager, compatibility services, and Minecraft-specific runtime adapter must remain separate components.

## 2. Native package format

The native package extension is `.ncp` (NeoCraft Package).

An NCP package describes content and its requirements. The package format must be independent of any particular Minecraft build and independent of the implementation language used by a mod.

**NCP v1 container:** ZIP with the `.ncp` extension, root `manifest.json`, and payload under `content/`. The contract is documented in [NCP_FORMAT_V1.md](NCP_FORMAT_V1.md).

The PR #1 branch supplies:
- a strict NCP v1 manifest decoder and stable version-range evaluator;
- a read-only ZIP validator and ZIP metadata guard;
- exact payload inventory, uncompressed-size, and SHA-256 verification;
- a transactional data-only package store with staging, re-verification, atomic commit, listing, tamper detection, and uninstall;
- Android NCP UI actions for read-only validation, explicit data-only installation, installed-package listing, and uninstall. The Android UI is compile-verified by CI run #212; physical-device verification is still pending.

These changes are delivered through the CI source overlay on `feature/ncp-package-reader`; they are not yet merged into `main`.

## 3. Namespaced registry

Every registered object must have a fully qualified identifier:

`<package-id>:<local-id>`

For example:
- `copper_expansion:copper_ingot`
- `uranium_expansion:uranium_ingot`

The registry must reject duplicate fully qualified identifiers. Equal local IDs in different package namespaces are not collisions.

A namespace prevents accidental identifier collisions; it does not by itself isolate executable code or guarantee that two mods are behaviorally compatible.

## 4. Dependencies and shared libraries

Dependencies must be explicit and version-constrained.

A shared library is resolved once to a single compatible version for a selected profile. If two packages require incompatible versions, the resolver must report an unsatisfied dependency rather than silently choosing one.

Optional integrations must be declared separately from mandatory dependencies.

The NCP v1 manifest decoder and version-range evaluator validate dependency declarations and range semantics. A platform-neutral dependency resolver has now been added on the PR branch: it accepts requested ranges and caller-supplied installed manifests, backtracks across candidate versions, checks API compatibility and declared conflicts, rejects mandatory cycles, and returns dependency-first order. It caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. Optional dependencies are not auto-selected. It also caps requested packages at 256 and distinct dependency constraint IDs at 4,096. The new `NcpInstalledPackagePlanResolver` bridge accepts a verified store snapshot, runs the resolver, and maps the dependency-first result back to installed package records. Its seven self-tests and the resolver's 18 self-tests passed in CI run #212.

## 5. Conflict model

NeoMod must distinguish at least these cases:

1. Identifier collision — two packages claim the same fully qualified ID.
2. Resource collision — packages target the same exclusive resource slot.
3. Dependency conflict — version constraints cannot be satisfied together.
4. Capability conflict — packages request incompatible or unavailable runtime capabilities.
5. Declared incompatibility — a package explicitly declares that it cannot coexist with another package/version.
6. Unknown interaction — the system has insufficient metadata to prove compatibility.

The system must not label two packages "compatible" merely because their IDs differ. Namespacing avoids one class of collision, not all behavioral conflicts.

Any override or cross-package interaction must be explicit, attributable, and deterministic. Silent last-write-wins behavior is prohibited.

## 6. Profiles and isolation

A profile is an immutable, ordered selection of package IDs and resolved versions, plus the target Minecraft build identity and NeoMod API/runtime version.

The NCP package store stores verified package payloads by ID and version. `NcpProfileStore` separately persists validated profile-selection metadata; neither store activates packages or changes Minecraft files.

For the Android profile-selection flow, `NcpProfileStore.inspect(profileId)` returns structurally validated metadata before dependency resolution. The caller must resolve the saved requested package ranges against the current verified package store. `load(profileId, plan)` checks the saved resolved package list against that plan; the overload `load(profileId, plan, currentBuild)` additionally compares the saved game identity with the currently installed build using `NcpProfileGameBuildMatcher`. Use the three-argument overload before presenting a profile as eligible for any future activation flow. Metadata inspection alone is not activation or compatibility proof.

Installing or updating a package must not mutate another profile.

Before activation, NeoMod must resolve dependencies, validate package integrity, detect known conflicts, and produce a human-readable plan. Failed validation must leave the active profile unchanged.

## 7. Runtime boundary

Minecraft Bedrock does not natively understand NCP. The launcher cannot create in-game items, blocks, entities, recipes, or behaviors merely by storing files in a directory.

Those features require a compatible runtime integration that can register and execute content against the exact installed game build. That integration is a separate, version-specific adapter behind a stable NeoMod interface.

The core package parser, dependency resolver, registry model, and profile manager must not contain hard-coded offsets or game-build-specific native hooks.

A game build is not supported until its identity has been reviewed and a harmless runtime probe has passed on that exact build. Package discovery or successful launcher-side validation is not proof of in-game loading.

## 8. Security requirements

- Reject path traversal and absolute paths in package entries.
- Enforce package size, entry count, nesting, and decompression limits.
- Verify hashes before activation.
- Treat package metadata and scripts as untrusted input.
- Do not execute package code during import or validation.
- Require explicit capabilities for access to files, network, or other privileged services.
- Keep validation, storage, and activation as separate operations.
- Preserve rollback to the previously active profile after failed activation.

## 9. Compatibility and evolution

The NeoMod API and NCP format have independent version numbers.

The core must expose capability negotiation rather than relying only on a single version string.

Breaking changes require a new major API version and an explicit migration path. Deprecated APIs must have a documented support window. Runtime adapters may be updated for a new Minecraft build without forcing a redesign of the package format or dependency resolver.


## 10. Profile snapshot model

The PR branch adds a platform-neutral `NcpProfileSnapshot` metadata model and validator. A snapshot records a stable profile ID, display name, exact Minecraft package/versionCode/APK SHA-256 identity, NeoMod API version, requested package ranges, and the dependency-first resolved package ID/version/order list. Validation compares the resolved list against a previously verified `NcpInstalledPackagePlan` and rejects incomplete game fingerprints, invalid identifiers, duplicate requests, malformed version ranges, and mismatched package order or versions. `NcpProfileStore` persists these snapshots in a private `.ncp-profile` text format with strict UTF-8/base64 decoding, size/record limits, symbolic-link checks, fsync, and same-directory atomic replacement. Save/load require a verified package plan; listing performs structural validation; delete is profile-ID scoped. The store revalidates its profile root before listing, load, and delete. A separate `NcpProfileGameBuildMatcher` compares a saved profile with the currently installed game identity by Android package name, PackageManager versionCode, and APK SHA-256. The three-argument `load(profileId, plan, currentBuild)` path now composes package-plan validation with this exact-build check. This remains metadata-only: it does not persist package payloads, activate profiles, or modify Minecraft.\n\n`NcpProfileComposer` adds a platform-neutral profile-creation step. Given a draft and a `NcpPackageStore`, it obtains a fresh integrity-checked installed-package listing, resolves the draft's requested package ranges, records the exact selected versions in dependency-first order, validates the resulting snapshot, and returns both the snapshot and the plan for an explicit `NcpProfileStore.save()` call. Optional dependencies remain resolver notices and are not silently selected. The caller must supply a current `NcpGameBuildIdentity` obtained from Android package discovery; composition does not itself establish game compatibility or activation eligibility. Eight composer self-tests cover dependency ordering, selected versions, missing dependencies, duplicate requests, invalid identity, and optional-dependency behavior. The Android profile browser still has no create/edit flow. CI run #212 on 2026-11-26 passed all 13 snapshot/matcher tests and all 16 profile-store tests, including the four new three-argument load integration cases.

## 11. Current implementation status

The repository contains launcher-side Minecraft discovery and diagnostics, a reviewed-build allow-list that remains empty, and a legacy `.nmod` package reader/manager from the Stage 9 source archive. The legacy `.nmod` reader is not an NCP reader and must not be treated as accepting `.ncp` files.

The PR #1 branch adds the NCP v1 manifest decoder, version-range evaluator, ZIP metadata guard, read-only package validator, transactional data-only package store, Android NCP validation/install/manage UI, dependency resolver, installed-package plan bridge, profile snapshot validator, and metadata-only profile store.

GitHub Actions [run #212](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45836074866), commit `dd2c0dd286eb9fab3ee66d8753bc0ad92369e2e5`, completed successfully on 2026-11-25. Its job logs confirm 12 self-test groups passed (150/150 cases):
- NeoMod core: 14/14
- legacy package reader: 14/14
- package manager: 11/11
- game-build identity: 9/9
- NCP manifest: 14/14
- NCP version range: 12/12
- NCP package reader: 12/12
- NCP package store: 10/10
- NCP dependency resolver: 18/18
- NCP installed-package plan: 7/7
- NCP profile snapshot/game-build matcher: 13/13
- NCP profile store: 16/16

The Android `:app:assembleDebug` task also succeeded and uploaded `neocraft-debug-apk` (artifact ZIP 950,953 bytes; SHA-256 of the artifact ZIP `a904e7ff10e660920c129862e23df12ca362c0c9ec217e0d4d32ec5f2a1450ab`). This verifies the current PR-branch source at commit `1bb191c`, including the three-argument profile-load overload and its four integration tests.

Run #202 includes the profile-root revalidation and its symlink regression test. Run #208 includes `NcpProfileGameBuildMatcher`, `NcpProfileStore.load(profileId, plan, currentBuild)`, and the integration tests for exact identity plus package, versionCode, and fingerprint mismatch rejection.

The Minecraft PE 1.26.13.1 identity report is recorded as a discovered candidate. It is not reviewed or enabled: the APK/signing identity has not been independently re-hashed in the current build environment, and no NeoMod compatibility probe has passed. `ReviewedGameBuilds` must remain empty for this candidate.

The following remain unimplemented or unverified:
- NCP publisher signature/trust model;
- profile creation, editing, package selection, dependency re-resolution in the Android UI, and activation flow (the current Android UI lists/inspects metadata and supports confirmed deletion of one profile metadata file);
- NCP namespaced content registry;
- NCP conflict engine;
- profile activation and rollback;
- version-specific Minecraft runtime adapter and in-game NeoMod loading;
- runtime compatibility with Minecraft 1.26.13.1;
- offline/LAN support.

The existing Android NCP package-management UI and read-only NeoMod profile browser have compile verification in run #212. The browser lists structurally validated profiles, displays saved snapshots through inspect(), and supports explicit confirmation-based deletion of one profile metadata file through delete(profileId). It deliberately does not create/edit profiles, resolve dependencies again, compare the snapshot with the currently installed Minecraft build, uninstall NCP packages, or activate anything. Physical-device testing is still pending. The NCP package store and profile store are data-only: they do not execute package code, activate mods, or prove in-game compatibility. Offline/LAN must be treated as unimplemented until a working transport/session design and device-to-device test demonstrate it.

This status must be updated only when code and tests demonstrate the feature.


## 12. Archived Minecraft candidate APK: static identity evidence

The Library artifact com.mojang.minecraftpe-972601301.apk (452,964,617 bytes) was independently read from the Library and analyzed in the build container on 2026-11-26.

- APK SHA-256: 6a50dea2136f8620a6d72a00f9b117f87dc1dd3f4839b8363c520d8e79d8a916.
- Decoded AndroidManifest.xml: package com.mojang.minecraftpe, versionName 1.26.13.1, manifest versionCode 972601301.
- APK Signing Block contains v2 and v3 signer records. The DER certificate SHA-256 extracted from both records is b369922ee3e720f359b9eec13af554dc87a0ec09f3ac49e7e123bc94fb985890, matching the fingerprint previously reported from the device screen.
- The Stage10 device-evidence archive contains a user-provided NeoCraft diagnostic report dated 2026-11-15 that records PackageManager versionCode 972601301 and the same signer/base.apk digests as this archived APK. It was not freshly re-collected in this run. The separately mentioned number 792601301 differs from both sources and must not be treated as versionCode.
- This is static evidence for the archived file only. A prior user-provided on-device PackageManager diagnostic matches this file, but the installed identity has not been freshly re-collected during this run. The APK signature was not cryptographically re-verified in this analysis, and no NeoMod runtime compatibility probe has passed.

Keep this build out of ReviewedGameBuilds until the installed device identity and a harmless version-specific runtime probe are both verified.
