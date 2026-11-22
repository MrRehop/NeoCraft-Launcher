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
- Android NCP UI actions for read-only validation, explicit data-only installation, installed-package listing, and uninstall. This integration is newer than CI run #157 and still awaits CI and physical-device verification.

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

The NCP v1 manifest decoder and version-range evaluator validate dependency declarations and range semantics. A platform-neutral dependency resolver has now been added on the PR branch: it accepts requested ranges and caller-supplied installed manifests, backtracks across candidate versions, checks API compatibility and declared conflicts, rejects mandatory cycles, and returns dependency-first order. It caps candidate manifests at 4,096, selected packages at 256, and search states at 10,000. Optional dependencies are not auto-selected. Its 18 self-tests are wired into GitHub Actions but have not yet passed a run. It also caps requested packages at 256 and distinct dependency constraint IDs at 4,096. The new `NcpInstalledPackagePlanResolver` bridge accepts a verified store snapshot, runs the resolver, and maps the dependency-first result back to installed package records; it has six self-tests wired into CI, but this addition is not yet verified by a CI run.

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

The NCP package store currently stores packages by ID and version. It is not yet an NCP profile manager and does not activate packages.

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

## 10. Current implementation status

The repository contains launcher-side Minecraft discovery and diagnostics, a reviewed-build allow-list that remains empty, and a legacy `.nmod` package reader/manager from the Stage 9 source archive. The legacy `.nmod` reader is not an NCP reader and must not be treated as accepting `.ncp` files.

The PR #1 branch adds the NCP v1 manifest decoder, stable version-range evaluator, ZIP metadata guard, read-only package validator, transactional data-only package store, and Android NCP validation picker through the CI source overlay.

GitHub Actions [run #157](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44866864793), commit `b40401ee5076752dd138fa2beda84b0a1636e2a9`, completed successfully on 2026-11-21. It passed:
- NeoMod core: 14/14
- legacy package reader: 14/14
- package manager: 11/11
- game-build identity: 9/9
- NCP manifest: 14/14
- NCP version range: 12/12
- NCP package reader: 12/12
- NCP package store: 10/10
- NCP dependency resolver: 18/18

The Android `:app:assembleDebug` task also succeeded and uploaded `neocraft-debug-apk`. The run #157 artifact ZIP is 925,935 bytes with SHA-256 `9d14a50fdf2e0a32129b762f08e5b288ef9dd97ab7d2a1602be1cc8dd293262c`. It expires on 2027-02-19 at 04:35:36 UTC. The Android picker is compile-verified but has not yet been manually exercised on the Samsung device.

The Minecraft PE 1.26.13.1 identity report is recorded as a discovered candidate. It is not reviewed or enabled: the original APK/signing identity has not been independently re-hashed in the current build environment, and no NeoMod compatibility probe has passed. `ReviewedGameBuilds` must remain empty for this candidate.

The following remain unimplemented or unverified:
- NCP publisher signature/trust model;
- integration of the NCP dependency resolver with the package store and profile UI;
- NCP namespaced content registry;
- NCP conflict engine;
- NCP profile activation and rollback;
- in-game NeoMod runtime;
- runtime compatibility with Minecraft 1.26.13.1;
- offline/LAN support.

The NCP package store is data-only. The Android UI now has validation, data-only install, list, and uninstall actions, but the integration is pending CI and physical-device verification; run #157 predates these UI changes. The dependency resolver's 18 self-tests passed in run #157. The new installed-package plan bridge maps resolver output back to verified store records; its tests are wired into the workflow but not yet CI-verified. It is not yet surfaced in Android profile management. The earlier run #152 package-store fixture failure was corrected in commit `783707a6`; the package-store suite passed 10/10 in run #156. CI run #157 on commit `b40401ee` passed. Neither the store nor resolver activates content, enforces runtime capabilities, or executes package code.\n\nThis status must be updated only when code and tests demonstrate the feature.
