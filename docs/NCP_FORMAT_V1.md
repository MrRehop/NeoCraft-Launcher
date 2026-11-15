# NeoCraft Package (NCP) format — version 1

Status: IMPLEMENTATION CONTRACT DRAFT — NCP v1 manifest decoder is implemented and self-tested; ZIP package inspection and payload inventory/hash verification remain pending.

## 1. Container

Version 1 uses a ZIP container with the file extension \`.ncp\`.

The archive must contain exactly one root entry named \`manifest.json\`. Content files are stored below the root \`content/\` directory. Unknown top-level entries are rejected in v1 so that package meaning is deterministic.

The archive must not contain:
- absolute paths, drive-letter paths, backslashes, or path traversal segments;
- duplicate paths (including duplicates after slash normalization);
- symbolic links or encrypted ZIP entries;
- nested archives intended for automatic execution;
- executable code that the importer attempts to run.

Limits are enforced before extraction. Initial implementation targets:
- archive size: 128 MiB;
- maximum entries: 2,048;
- maximum manifest size: 256 KiB;
- maximum JSON nesting depth: 64 object/array containers;
- maximum individual uncompressed entry: 32 MiB;
- maximum total uncompressed size: 256 MiB;
- maximum compression ratio: 100:1.

The shared platform-neutral JSON parser rejects manifest text over 256 KiB and nesting deeper than 64 containers before recursive parsing. This applies to both NCP and legacy NeoMod manifest decoding. These are NeoCraft v1 policy limits, not Minecraft limits. They may be revised only with tests and a format-version review.

## 2. Manifest encoding

\`manifest.json\` is UTF-8 JSON. A UTF-8 BOM is rejected. Duplicate JSON object keys are rejected. Unknown fields are rejected in v1 except fields explicitly placed under \`extensions\`.

Required top-level fields:
- \`format\`: exactly \`org.neocraft.ncp\`;
- \`formatVersion\`: exactly integer \`1\`;
- \`id\`: globally unique package identifier, lowercase ASCII;
- \`name\`: display name;
- \`publisher\`: publisher label;
- \`version\`: semantic version (SemVer 2.0.0);
- \`api\`: required NeoMod API version range;
- \`capabilities\`: explicit capability declarations;
- \`dependencies\`: mandatory package dependencies;
- \`optionalDependencies\`: optional integrations;
- \`conflicts\`: declared incompatible package IDs;
- \`content\`: content declarations;
- \`files\`: inventory and SHA-256 digest of every payload file;
- \`extensions\`: optional namespaced extension metadata.

Package IDs match \`^[a-z][a-z0-9_]{1,47}$\`. IDs are immutable once published. A package ID is not authenticated merely because it appears in a manifest; publisher identity and package signing are separate future trust features.

## 3. File inventory and integrity

Every file other than \`manifest.json\` must be listed exactly once in \`files\`. Every listed file must exist in the archive exactly once. No unlisted payload file is allowed.

Each file record contains:
- \`path\`: canonical archive-relative path beginning with \`content/\`;
- \`size\`: uncompressed byte length;
- \`sha256\`: lowercase 64-character SHA-256 digest of the uncompressed bytes.

The importer checks the declared size and digest while streaming the entry. It must not trust ZIP directory sizes alone.

The manifest is not included in its own file digest list. NCP v1 has no cryptographic publisher signature. A valid SHA-256 inventory detects accidental or malicious payload changes only when the manifest itself is trusted; it does not prove who published the package.

## 4. Namespaces

A content declaration has a local identifier. Its fully qualified ID is always derived as:

\`<package-id>:<local-id>\`

The package cannot choose another package's namespace. Local IDs match \`^[a-z][a-z0-9_]{0,63}$\`. Duplicate fully qualified IDs are rejected.

The v1 content type vocabulary is intentionally descriptive and does not imply runtime support. Initial types:
- \`resource\`
- \`block\`
- \`item\`
- \`entity\`
- \`recipe\`
- \`behavior\`

A package can pass NCP validation while its content remains unsupported by the installed Minecraft runtime. The UI must distinguish “package valid” from “content loadable”.

## 5. Dependencies and conflicts

A dependency record contains \`id\` and \`versionRange\`. Version ranges use a deliberately limited syntax in v1:
- exact: \`1.2.3\`
- inclusive lower bound: \`>=1.2.3\`
- bounded: \`>=1.2.3 <2.0.0\`

Only stable SemVer versions are accepted in v1; prerelease/build metadata are rejected for package and API versions until resolver semantics are implemented and tested.

Mandatory dependency cycles are invalid. Optional dependencies do not make a package invalid when absent. Conflicts are explicit package IDs; declaring a conflict is not proof that all undeclared packages are compatible.

## 6. Capabilities

V1 recognizes these capability identifiers:
- \`content.read\`
- \`content.register\`
- \`storage.profile\`
- \`network.client\`

Unknown capabilities are rejected. A declaration grants no permission by itself. Runtime capability enforcement must be implemented by a future adapter before any capability can be exercised.

## 7. Validation pipeline

Validation is read-only and has these ordered phases:
1. inspect archive metadata and enforce archive-level limits;
2. validate canonical paths, reject duplicates, traversal, symlinks and encrypted entries;
3. read and parse bounded UTF-8 manifest JSON, rejecting duplicate keys;
4. validate manifest schema and semantic constraints;
5. compare archive entries with the manifest file inventory;
6. stream payloads and verify sizes and SHA-256 digests;
7. validate internal package references and duplicate namespaced IDs;
8. return a structured report; do not extract or execute package content.

A failed validation must not install files, alter profiles, or change the active game.

## 8. Error model

Errors should have stable machine-readable codes and human-readable details. Initial codes:
- \`NCP_ARCHIVE_INVALID\`
- \`NCP_LIMIT_EXCEEDED\`
- \`NCP_PATH_UNSAFE\`
- \`NCP_DUPLICATE_ENTRY\`
- \`NCP_MANIFEST_MISSING\`
- \`NCP_MANIFEST_INVALID\`
- \`NCP_FORMAT_UNSUPPORTED\`
- \`NCP_ID_INVALID\`
- \`NCP_VERSION_INVALID\`
- \`NCP_DEPENDENCY_INVALID\`
- \`NCP_DEPENDENCY_CYCLE\`
- \`NCP_CONFLICT_DECLARED\`
- \`NCP_CONTENT_ID_DUPLICATE\`
- \`NCP_FILE_INVENTORY_MISMATCH\`
- \`NCP_HASH_MISMATCH\`
- \`NCP_CAPABILITY_UNKNOWN\`

## 9. Explicit non-goals for v1

NCP validation does not:
- install or activate a mod;
- execute scripts or native libraries;
- patch Minecraft APK/DEX/native libraries;
- create in-game blocks, items, entities, recipes or behaviors;
- establish publisher identity or package authenticity;
- prove compatibility with Minecraft 1.26.13.1;
- provide offline/LAN functionality.

These require separate reviewed components and tests.
