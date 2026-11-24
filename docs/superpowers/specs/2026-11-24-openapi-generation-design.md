# OpenAPI generation flow design

## Goal

After generating the full OpenAPI JSON document, automatically produce the
technical-support and observability projections from it.

## Design

Extend `generate_openapi` in `build-and-run.sh` to invoke, in order,
`./scripts/nodoTechnicalSupport_openapi.sh` and
`./scripts/observability_openapi.sh` with no arguments, after downloading
`openapi/openapi.json` and removing the temporary Docker container. Both
scripts use their repository-relative default source and output paths. The
existing `set -e` behavior stops the flow if the download or either projection
script fails; running projections after container removal avoids leaving the
container running when a projection fails.

## Alternatives

1. Invoke both scripts directly after generation. This is the smallest change
   and keeps the generation workflow visible in one place.
2. Add a separate wrapper function for projection generation. This adds
   indirection without reuse or additional behavior.
3. Run the scripts concurrently. This is unnecessary for two fast projections
   and departs from the requested sequence.

The selected approach is direct sequential invocation.

## Validation

- Inspect the resulting function to confirm both script calls follow the full
  OpenAPI download and preserve the requested order.
- Run shell syntax validation on `build-and-run.sh`.
