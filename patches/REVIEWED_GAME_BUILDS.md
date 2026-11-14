# Reviewed Minecraft build registry

NeoCraft recognizes a game build only when its package name, version code,
current signing-certificate SHA-256 set, and any configured required artifact
digests exactly match a reviewed record in `neomod-core`.

## Current state

The allow-list is intentionally empty. The installed game observed during the
first device test was discovered successfully, but discovery is not proof that
the build is compatible with NeoMod.

The version name and version code shown by Android are useful diagnostic
evidence. The signing-certificate digest identifies the signer; it is not an
APK file digest and does not prove that game internals match a supported layout.

## Admission requirements

Before adding a record:

1. Confirm the exact package name and version code from the installed package.
2. Obtain and independently verify the current signing-certificate SHA-256.
3. Inspect the actual APK/split artifacts and record digests when compatibility
   depends on exact artifact contents.
4. Test the intended NeoMod integration against that exact build.
5. Add a unique record to `ReviewedGameBuilds` only after the evidence and
   test results have been reviewed.

Unknown, incomplete, or mismatched evidence must remain unsupported. Do not
add a wildcard, version-name-only, or partial-identity fallback.
