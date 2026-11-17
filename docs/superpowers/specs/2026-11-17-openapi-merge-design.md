# OpenAPI merge script design

## Goal

Provide a repeatable way to combine the direct `checkPosition` and
`paymentStatus` OpenAPI documents into
`openapi/nodoTechnicalSupport_direct_openapi.json`.

## Design

Add `scripts/merge_openapi.sh`, using Bash and `jq` to read the two existing
JSON files and write a merged OpenAPI document. With no arguments, use the
two requested source files and output path; optionally accept all three paths
(first source, second source, output) to support isolated fixture-based tests.
Resolve paths before writing and reject an output path that refers to an input
through a relative/absolute spelling, symlink, or hard link.
Merge `paths` and each component category (including `schemas` and
`securitySchemes`) individually. Identical definitions with the same key can
be deduplicated, while conflicting definitions with the same path or component
key must produce a non-zero exit.

Use `checkPosition` as the base for other top-level and `info` fields, but
replace its title with `Nodo Technical Support direct API`. Preserve the common
`openapi` and `info.version` values; fail explicitly if either source disagrees
on these version fields. The source documents remain unchanged. Missing or
invalid input files, an unavailable `jq`, and write failures must be reported
to stderr with a non-zero exit. Write to a temporary file in the output
directory and replace the output only after a successful merge, so failures
preserve any existing output and do not leave partial JSON or temporary files.

## Alternatives

1. Bash and `jq`: small, fits the existing scripts directory, and is suitable
   for JSON map merging.
2. Python standard library: avoids third-party modules, but adds a Python
   runtime dependency and a separate script implementation.
3. An OpenAPI-specific merge dependency: offers broader merge semantics but is
   unnecessary for the current documents and adds dependency management.

The selected approach is Bash and `jq`.

## Validation

- The output parses as JSON.
- It has the unified title and retains the common `openapi` and
  `info.version` values.
- It contains both source paths and all source schemas/security schemes.
- The identical `ApiKey` security scheme appears only once.
- Fixture-based tests confirm conflicting duplicate paths/components and
  mismatched version fields fail explicitly, clean up temporary files, and
  preserve an existing output.
- An output path that aliases either input is rejected.
- Missing/invalid inputs, missing `jq`, and an unwritable output are reported
  to stderr with a non-zero exit; failures preserve existing output and clean
  up temporary files.
- The two input documents are not modified.
