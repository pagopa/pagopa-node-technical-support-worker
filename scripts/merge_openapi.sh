#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'Usage: %s [<source-one.json> <source-two.json> <output.json>]\n' "$0" >&2
}

fail() {
  printf 'merge_openapi: %s\n' "$*" >&2
  exit 1
}

if ! command -v jq >/dev/null 2>&1; then
  fail "jq is required but was not found in PATH"
fi

case "$#" in
  0)
    script_dir="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    repo_root="$(cd "$script_dir/.." && pwd -P)"
    source_one="$repo_root/openapi/checkPosition_openapi.json"
    source_two="$repo_root/openapi/paymentStatus_openapi.json"
    requested_output="$repo_root/openapi/nodoTechnicalSupport_direct_openapi.json"
    ;;
  3)
    source_one="$1"
    source_two="$2"
    requested_output="$3"
    ;;
  *)
    usage
    fail "expected either zero arguments or exactly three paths"
    ;;
esac

for source in "$source_one" "$source_two"; do
  if [[ ! -f "$source" || ! -r "$source" ]]; then
    fail "input source is missing, not a regular file, or unreadable: $source"
  fi
done

source_one_real="$(realpath "$source_one")" ||
  fail "could not resolve input source path: $source_one"
source_two_real="$(realpath "$source_two")" ||
  fail "could not resolve input source path: $source_two"

if [[ -z "$requested_output" || -d "$requested_output" ]]; then
  fail "output must be a file path, not a directory"
fi
output_dir="$(dirname "$requested_output")"
output_name="$(basename "$requested_output")"
if [[ ! -d "$output_dir" ]]; then
  fail "output directory does not exist: $output_dir"
fi
output_dir_real="$(cd -P "$output_dir" 2>/dev/null && pwd)" ||
  fail "could not resolve output directory: $output_dir"
output_path="$output_dir_real/$output_name"

if [[ -e "$output_path" || -L "$output_path" ]]; then
  output_real="$(realpath "$output_path" 2>/dev/null)" ||
    fail "could not resolve existing output path: $requested_output"
  if [[ "$output_real" == "$source_one_real" || "$output_real" == "$source_two_real" ]] ||
    [[ "$output_path" -ef "$source_one" || "$output_path" -ef "$source_two" ]]; then
    fail "output aliases an input source; refusing to replace it"
  fi
fi

temp_output=''
cleanup() {
  if [[ -n "$temp_output" ]]; then
    rm -f "$temp_output" 2>/dev/null || true
  fi
}
trap cleanup EXIT

for attempt in {1..20}; do
  candidate="$output_path.tmp.$$.${RANDOM}.${attempt}"
  if [[ -e "$candidate" || -L "$candidate" ]]; then
    continue
  fi
  if touch "$candidate" 2>/dev/null; then
    temp_output="$candidate"
    break
  fi
  temp_output="$candidate"
  fail "could not create temporary output file in $output_dir_real"
done
if [[ -z "$temp_output" || ! -f "$temp_output" ]]; then
  fail "could not create a unique temporary output file in $output_dir_real"
fi

if ! jq -n --slurpfile one "$source_one_real" --slurpfile two "$source_two_real" '
  def validate($document; $label):
    if ($document | type) != "object" then
      error("\($label) must be a JSON object")
    elif ($document.openapi | type) != "string" or ($document.openapi | length) == 0 then
      error("\($label) must have a nonempty string openapi field")
    elif ($document.info | type) != "object" then
      error("\($label) must have an info object")
    elif ($document.info.version | type) != "string" or ($document.info.version | length) == 0 then
      error("\($label) must have a nonempty string info.version field")
    elif ($document.paths | type) != "object" then
      error("\($label) must have a paths object")
    elif (($document.components // {}) | type) != "object" then
      error("\($label) components must be an object")
    elif any((($document.components // {}) | to_entries[]); (.value | type) != "object") then
      error("\($label) component categories must be objects")
    else
      true
    end;

  def merge_named($label; $left; $right):
    reduce ((($left | keys) + ($right | keys)) | unique)[] as $key
      ({};
       if ($left | has($key)) and ($right | has($key)) then
         if $left[$key] == $right[$key] then
           .[$key] = $left[$key]
         else
           error("conflicting definitions for \($label) key \"\($key)\"")
         end
       elif $left | has($key) then
         .[$key] = $left[$key]
       else
         .[$key] = $right[$key]
       end);

  $one[0] as $source_one |
  $two[0] as $source_two |
  validate($source_one; "source one") |
  validate($source_two; "source two") |
  if $source_one.openapi != $source_two.openapi then
    error("openapi versions do not match")
  elif $source_one.info.version != $source_two.info.version then
    error("info.version values do not match")
  else
    ($source_one.components // {}) as $components_one |
    ($source_two.components // {}) as $components_two |
    merge_named("path"; $source_one.paths; $source_two.paths) as $paths |
    (reduce ((($components_one | keys) + ($components_two | keys)) | unique)[] as $category
      ({};
       .[$category] = merge_named(
         "component category \"\($category)\"";
         ($components_one[$category] // {});
         ($components_two[$category] // {})
       ))) as $components |
    $source_one + {
      info: ($source_one.info + {title: "Nodo Technical Support direct API"}),
      paths: $paths,
      components: $components
    }
  end
' >"$temp_output"; then
  fail "could not validate or merge the OpenAPI inputs"
fi

if ! mv -f "$temp_output" "$output_path"; then
  fail "could not replace output file: $requested_output"
fi
temp_output=''
