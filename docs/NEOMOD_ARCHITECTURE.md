# NeoMod architecture baseline

Status: DESIGN DRAFT — NOT IMPLEMENTED

This document records the current architectural direction for NeoCraft. It is a design baseline, not a claim that Minecraft runtime integration already exists.

## 1. Product boundary

NeoCraft launches the user's already-installed Minecraft Bedrock package. It does not bundle a separate Minecraft distribution and does not replace the game with a fork.

NeoMod is a separate modification platform. It must not depend on importing `.mcpack` or `.mcaddon` as its native package format.

The launcher, package manager, compatibility services, and Minecraft-specific runtime adapter must remain separate components.

## 2. Native package format

The native package extension is `.ncp` (NeoCraft Package).

An NCP package describes content and its requirements. The package format must be independent of any particular Minecraft build and independent of the implementation language used by a mod.

**NCP v1 container decision:** ZIP with the `.ncp` extension, root `manifest.json`, and payload under `content/`. The initial contract is documented in [NCP_FORMAT_V1.md](NCP_FORMAT_V1.md), with a JSON Schema and example manifest. A strict NCP v1 manifest decoder is now supplied through the CI source overlay and has dedicated JVM self-tests. The ZIP package reader, exact archive/file-inventory comparison, streaming SHA-256 verification, and NCP installation path remain unimplemented. Publisher signing remains outside v1.

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

The repository currently contains launcher-side Minecraft discovery and diagnostics, a reviewed-build allow-list that remains empty, a legacy `.nmod` package reader/manager from the Stage 9 source archive, a separate NCP v1 manifest decoder supplied by the CI overlay, and a CI debug-APK build. The legacy `.nmod` reader is not an NCP reader and must not be treated as accepting `.ncp` files.

The following are not yet implemented or verified:

- NCP v1 manifest decoder (implemented and self-tested); ZIP archive inspection, exact file-inventory checks, streaming size/SHA-256 verification, and NCP installation path not yet implemented;
- package signature/trust model;
- dependency resolver;
- namespaced content registry;
- conflict engine;
- profile activation and rollback;
- in-game NeoMod runtime;
- runtime compatibility with Minecraft 1.26.13.1;
- offline/LAN support.

This status must be updated only when code and tests demonstrate the feature.
