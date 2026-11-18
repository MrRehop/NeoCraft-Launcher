# NeoMod architecture baseline

Status: DESIGN DRAFT — RUNTIME NOT IMPLEMENTED; PACKAGE CORE IN REVIEW

This document records the current architectural direction for NeoCraft. It is a design baseline, not a claim that Minecraft runtime integration already exists.

## 1. Product boundary

NeoCraft launches the user's already-installed Minecraft Bedrock package. It does not bundle a separate Minecraft distribution and does not replace the game with a fork.

NeoMod is a separate modification platform. It must not depend on importing `.mcpack` or `.mcaddon` as its native package format.

The launcher, package manager, compatibility services, and Minecraft-specific runtime adapter must remain separate components.

## 2. Native package format

The native package extension is `.ncp` (NeoCraft Package).

An NCP package describes content and its requirements. The package format must be independent of any particular Minecraft build and independent of the implementation language used by a mod.

**NCP v1 container decision:** ZIP with the `.ncp` extension, root `manifest.json`, and payload under `content/`. The initial contract is documented in [NCP_FORMAT_V1.md](NCP_FORMAT_V1.md), with a JSON Schema and example manifest. The PR #1 branch supplies a strict manifest decoder, read-only ZIP reader and metadata guard, and transactional data-only package store through the CI source overlay. The implementation and self-tests are in review; the latest recorded CI run failed in the package-store tamper-test fixture, so this branch state is not yet fully verified. Publisher signing remains outside v1.

Every package must contain a manifest with at least:

- globally unique package ID;
- human-readable name and publisher;
- package semantic version;
- NCP format version;
- required NeoMod API version or range;
- declared capabilities;
- dependency declarations with version ranges;
- declared conflicts and replacement/override intent;
- namespaced content identifiers;
- integrity metadata.

A package must never be allowed to silently claim another publisher's package ID.

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
- Keep install, validation, and activation as separate operations.
- Preserve rollback to the previously active profile after failed activation.

## 9. Compatibility and evolution

The NeoMod API and NCP format have independent version numbers.

The core must expose capability negotiation rather than relying only on a single version string.

Breaking changes require a new major API version and an explicit migration path. Deprecated APIs must have a documented support window. Runtime adapters may be updated for a new Minecraft build without forcing a redesign of the package format or dependency resolver.

## 10. Current implementation status

The repository contains launcher-side Minecraft discovery and diagnostics, a reviewed-build allow-list that remains empty, and a legacy `.nmod` package reader/manager from the Stage 9 source archive. The legacy `.nmod` reader is not an NCP reader and must not be treated as accepting `.ncp` files.

The PR #1 branch additionally supplies an NCP v1 manifest decoder, ZIP metadata guard and read-only package validator, and transactional local package store. These are added through the CI source overlay rather than committed into the Stage 9 archive. Their latest recorded workflow run (#101, commit `a91db8e5`) passed 48/48 core tests, 14/14 NCP manifest tests and 11/11 package-reader tests, but failed in the package-store tamper self-test. The fixture changed the payload length, so the store correctly reported a size mismatch before the expected hash mismatch. The fixture has been corrected in commit `ad1372b0`; a CI run on that correction is pending, and run #101 did not produce an APK.

The Minecraft PE 1.26.13.1 identity report is recorded as a discovered candidate in `patches/candidate-builds/`. It is not reviewed or enabled: the reported package, signer and APK digest have not been independently rechecked from an exported APK, and no NeoMod compatibility probe has passed. `ReviewedGameBuilds` must remain empty for this candidate.

The following remain unimplemented or unverified:

- package signature/trust model;
- dependency resolver for NCP;
- namespaced content registry for NCP;
- conflict engine for NCP;
- NCP profile activation and rollback;
- in-game NeoMod runtime;
- runtime compatibility with Minecraft 1.26.13.1;
- offline/LAN support.

The NCP package store is data-only. It does not load content into Minecraft, resolve or activate dependency graphs, enforce runtime capabilities, execute package code, or provide rollback of a running game.

This status must be updated only when code and tests demonstrate the feature.
