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
- Android NCP UI actions for read-only validation, explicit data-only installation, installed-package listing, and uninstall. The Android UI is compile-verified by CI run #186; physical-device verification is still pending.

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

The NCP v1 manifest decoder and version-range evaluator validate dependency declarations and range semantics. A platform-neutral dependency resolver has now been added on the PR branch: it accepts requested ranges and caller-supplied installed manifests, backtracks across candidate versions, checks API compatibility and declared conflicts, rejects mandatory cycles, and returns dependency-first order. It caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. Optional dependencies are not auto-selected. Its 18 self-tests are wired into GitHub Actions but have not yet passed a run. It also caps requested packages at 256 and distinct dependency constraint IDs at 4,096. The new `NcpInstalledPackagePlanResolver` bridge accepts a verified store snapshot, runs the resolver, and maps the dependency-first result back to installed package records. Its seven self-tests and the resolver's 18 self-tests passed in CI run #186.

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

For the Android profile-selection flow, `NcpProfileStore.inspect(profileId)` returns structurally validated metadata before dependency resolution. The caller must resolve the saved requested package ranges against the current verified package store, compare the saved game identity with the currently installed build using `NcpProfileGameBuildMatcher`, and validate the resolved list against the resulting plan before presenting the profile as activation-eligible. The existing `load(profileId, plan)` remains the strict plan-matching read path. Metadata inspection alone is not activation or compatibility proof.

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

The PR branch adds a platform-neutral `NcpProfileSnapshot` metadata model and validator. A snapshot records a stable profile ID, display name, exact Minecraft package/versionCode/APK SHA-256 identity, NeoMod API version, requested package ranges, and the dependency-first resolved package ID/version/order list. Validation compares the resolved list against a previously verified `NcpInstalledPackagePlan` and rejects incomplete game fingerprints, invalid identifiers, duplicate requests, malformed version ranges, and mismatched package order or versions. `NcpProfileStore` persists these snapshots in a private `.ncp-profile` text format with strict UTF-8/base64 decoding, size/record limits, symbolic-link checks, fsync, and same-directory atomic replacement. Save/load require a verified package plan; listing performs structural validation; delete is profile-ID scoped. The store revalidates its profile root before listing, load, and delete. A separate `NcpProfileGameBuildMatcher` now compares a saved profile with the currently installed game identity by Android package name, PackageManager versionCode, and APK SHA-256; callers must run this check before offering activation because a valid profile and dependency plan alone do not prove the installed game build matches. This remains metadata-only: it does not persist package payloads, activate profiles, or modify Minecraft. CI #186 on 2026-11-23 passed the earlier 8 snapshot and 9 profile-store tests. The current branch source now contains 13 snapshot/matcher cases and 10 profile-store cases, including five new exact-build/mismatch tests and a profile-root symlink replacement regression. These follow-up tests and the matcher were added after CI #186 and are awaiting a new successful workflow.

## 11. Current implementation status

The repository contains launcher-side Minecraft discovery and diagnostics, a reviewed-build allow-list that remains empty, and a legacy `.nmod` package reader/manager from the Stage 9 source archive. The legacy `.nmod` reader is not an NCP reader and must not be treated as accepting `.ncp` files.

The PR #1 branch adds the NCP v1 manifest decoder, version-range evaluator, ZIP metadata guard, read-only package validator, transactional data-only package store, Android NCP validation/install/manage UI, dependency resolver, installed-package plan bridge, profile snapshot validator, and metadata-only profile store.

GitHub Actions [run #186](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/45116369676), commit `ddfc2ffaef00b5aca2349ba2d4ed7e1b2b3a4ef6`, completed successfully on 2026-11-23. It passed:
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
- NCP profile snapshot: 8/8
- NCP profile store: 9/9

The Android `:app:assembleDebug` task also succeeded and uploaded `neocraft-debug-apk` (artifact ZIP 944,954 bytes; artifact SHA-256 `54ee07abfc32f7230ffc768ce92d813409a6edd743459b7a792ae90245782d37`). The artifact expires on 2027-02-21 at 05:13 UTC. This CI result verifies the code at commit `ddfc2ffa`; it does not include the latest profile-root revalidation and regression-test commits.

The follow-up calls `ensureRoot()` before profile `load` and `delete`, matching the checks in save/list. A regression test creates a valid profile outside the store, replaces the profile directory with a symlink, and verifies that load/delete reject the redirected root without touching the external file. A later follow-up adds `NcpProfileGameBuildMatcher` and five snapshot tests for exact package/versionCode/SHA-256 matching and mismatch rejection. The root-symlink guard, its regression test, the matcher, and its five tests are newer than CI #186 and remain unverified until a new workflow completes.

The Minecraft PE 1.26.13.1 identity report is recorded as a discovered candidate. It is not reviewed or enabled: the APK/signing identity has not been independently re-hashed in the current build environment, and no NeoMod compatibility probe has passed. `ReviewedGameBuilds` must remain empty for this candidate.

The following remain unimplemented or unverified:
- NCP publisher signature/trust model;
- profile UI integration and profile selection flow;
- NCP namespaced content registry;
- NCP conflict engine;
- profile activation and rollback;
- version-specific Minecraft runtime adapter and in-game NeoMod loading;
- runtime compatibility with Minecraft 1.26.13.1;
- offline/LAN support.

The Android UI has compile verification but still needs physical-device testing. The NCP package store and profile store are data-only: they do not execute package code, activate mods, or prove in-game compatibility. Offline/LAN must be treated as unimplemented until a working transport/session design and device-to-device test demonstrate it.

This status must be updated only when code and tests demonstrate the feature.
