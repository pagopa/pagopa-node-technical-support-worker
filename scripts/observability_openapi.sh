#!/usr/bin/env bash
# Extracts from the worker OpenAPI the Nodo Observability operations, i.e. the ones tagged
# "Observability" (SSE /api/events included), together with the schemas they reference.
set -euo pipefail

usage() {
  printf 'Usage: %s [<source.json> <output.json>]\n' "$0" >&2
}

fail() {
  printf 'observability_openapi: %s\n' "$*" >&2
  exit 1
}

if ! command -v jq >/dev/null 2>&1; then
  fail "jq is required but was not found in PATH"
fi

case "$#" in
  0)
    script_dir="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    repo_root="$(cd "$script_dir/.." && pwd -P)"
    source="$repo_root/openapi/openapi.json"
    requested_output="$repo_root/openapi/observability_openapi.json"
    ;;
  2)
    source="$1"
    requested_output="$2"
    ;;
  *)
    usage
    fail "expected either zero arguments or exactly two paths"
    ;;
esac

if [[ ! -f "$source" || ! -r "$source" ]]; then
  fail "input source is missing, not a regular file, or unreadable: $source"
fi
if [[ -z "$requested_output" || -d "$requested_output" ]]; then
  fail "output must be a file path, not a directory"
fi
output_dir="$(dirname "$requested_output")"
if [[ ! -d "$output_dir" ]]; then
  fail "output directory does not exist: $output_dir"
fi
output_path="$(cd -P "$output_dir" && pwd)/$(basename "$requested_output")"
if [[ "$output_path" -ef "$source" ]]; then
  fail "output aliases the input source; refusing to replace it"
fi

temp_output="$(mktemp "$output_path.tmp.XXXXXX")" ||
  fail "could not create a temporary output file next to $requested_output"
trap 'rm -f "$temp_output"' EXIT

if ! jq --arg tag "Observability" '
  def operations: ["get", "put", "post", "delete", "options", "head", "patch", "trace"];
  def refs: [.. | objects | .["$ref"]? // empty | select(startswith("#/components/schemas/"))
    | ltrimstr("#/components/schemas/")] | unique;

  if (.paths | type) != "object" then error("source must have a paths object") else . end |
  . as $doc |
  .components.schemas as $schemas |

  (.paths
    | with_entries(
        .value |= with_entries(select((.key | IN(operations[]) | not)
          or ((.value.tags // []) | index($tag))))
        | select(.value | keys | any(IN(operations[]))))) as $paths |
  if ($paths | length) == 0 then error("no operation is tagged \"\($tag)\"") else . end |

  # Schemas reachable from the kept operations, following nested $refs up to a fixpoint.
  { todo: ($paths | refs), seen: [] }
  | until(.todo | length == 0;
      .seen += .todo
      | .seen as $seen
      | .todo = ([.todo[] | $schemas[.] // error("missing schema \(.)") | refs[]] | unique - $seen))
  | .seen as $used |

  $doc
  | .info.title = "Nodo Observability - API ${service}"
  | .servers = [{ url: "${host}/nodo/observability/v1" }]
  | .tags = [{ name: $tag, description: "Nodo Observability operations" }]
  | .paths = $paths
  | .components.schemas = ($schemas | with_entries(select(.key | IN($used[]))))
' "$source" >"$temp_output"; then
  fail "could not extract the observability operations"
fi

chmod 644 "$temp_output"
mv -f "$temp_output" "$output_path" || fail "could not replace output file: $requested_output"
trap - EXIT
