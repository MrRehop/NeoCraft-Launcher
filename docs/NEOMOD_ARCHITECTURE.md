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
- an Android document picker that validates a selected `.ncp` archive without installing it.

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

The NCP v1 manifest decoder and version-range evaluator validate dependency declarations and range semantics. A platform-neutral dependency resolver has now been added on the PR branch: it accepts requested ranges and caller-supplied installed manifests, backtracks across candidate versions, checks API compatibility and declared conflicts, rejects mandatory cycles, and returns dependency-first order. It does not download/install packages, create profiles, or activate content. Optional dependencies are not auto-selected. Its self-tests are wired into GitHub Actions but have not yet passed a run.

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

GitHub Actions [run #123](https://github.com/MrRehop/NeoCraft-Launcher/actions/runs/44330927382), commit `08e0438bdb2f40338a947534c7dea8fb4f36ef71`, completed successfully on 2026-11-18. It passed:
- NeoMod core: 14/14;
- legacy package reader: 14/14;
- package manager: 11/11;
- game-build identity: 9/9;
- NCP manifest: 14/14;
- NCP package reader: 12/12;
- NCP package store: 9/9.

The Android `:app:assembleDebug` task also succeeded and uploaded `neocraft-debug-apk`. The checked-in Hello World NCP sample passed the NCP reader self-test in run #123. The Android picker is compile-verified but has not yet been manually exercised on the Samsung device.

The Minecraft PE 1.26.13.1 identity report is recorded as a discovered candidate. It is not reviewed or enabled: the original APK/signing identity has not been independently re-hashed in the current build environment, and no NeoMod compatibility probe has passed. `ReviewedGameBuilds` must remain empty for this candidate.

The following remain unimplemented or unverified:
- NCP publisher signature/trust model;
- NCP dependency resolver;
- NCP namespaced content registry;
- NCP conflict engine;
- NCP profile activation and rollback;
- in-game NeoMod runtime;
- runtime compatibility with Minecraft 1.26.13.1;
- offline/LAN support.

The NCP package store is data-only. The Android picker currently validates a selected archive only. Neither component loads content into Minecraft, resolves or activates dependency graphs, enforces runtime capabilities, or executes package code.

The version-range evaluator passed the later Android CI build run #127 (commit `356cee0e23b99409b81c6e4d14d4a3d742a0ead8`); run #129 (commit `aec0aae43771ddf8f216caff8187b11195d455cd`) also completed successfully and uploaded a Debug APK. The dependency resolver and its self-tests were added after those runs and are not yet CI-verified.\n\nThis status must be updated only when code and tests demonstrate the feature.
