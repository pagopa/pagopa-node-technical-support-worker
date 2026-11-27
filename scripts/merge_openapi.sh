#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf 'Usage: %s [<source.json> <source.json> ... <output.json>]\n' "$0" >&2
}

fail() {
  printf 'merge_openapi: %s\n' "$*" >&2
  exit 1
}

if ! command -v jq >/dev/null 2>&1; then
  fail "jq is required but was not found in PATH"
fi

sources=()
if [[ "$#" -eq 0 ]]; then
  script_dir="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  repo_root="$(cd "$script_dir/.." && pwd -P)"
  source_list="$repo_root/openapi/merge_direct_sources.txt"
  [[ -f "$source_list" && -r "$source_list" ]] ||
    fail "source list is missing or unreadable: $source_list"
  while IFS= read -r source || [[ -n "$source" ]]; do
    [[ -z "$source" || "$source" == \#* ]] && continue
    sources+=("$repo_root/$source")
  done <"$source_list"
  requested_output="$repo_root/openapi/nodoTechnicalSupport_direct_openapi.json"
elif [[ "$#" -ge 3 ]]; then
  while [[ "$#" -gt 1 ]]; do
    sources+=("$1")
    shift
  done
  requested_output="$1"
else
  usage
  fail "expected either zero arguments or at least two input paths and an output path"
fi

[[ "${#sources[@]}" -ge 2 ]] || fail "source list must contain at least two input paths"
source_paths=()
jq_args=()
source_refs=''
index=0
for source in "${sources[@]}"; do
  if [[ ! -f "$source" || ! -r "$source" ]]; then
    fail "input source is missing, not a regular file, or unreadable: $source"
  fi
  source_real="$(realpath "$source")" ||
    fail "could not resolve input source path: $source"
  source_paths+=("$source_real")
  jq_args+=(--slurpfile "source$index" "$source_real")
  if [[ -n "$source_refs" ]]; then
    source_refs+=', '
  fi
  source_refs+="\$source$index"
  index=$((index + 1))
done

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
  for source in "${source_paths[@]}"; do
    if [[ "$output_real" == "$source" || "$output_path" -ef "$source" ]]; then
      fail "output aliases an input source; refusing to replace it"
    fi
  done
fi

temp_output=''
temp_fd_open=false
cleanup() {
  if [[ "$temp_fd_open" == true ]]; then
    exec 3>&-
  fi
  if [[ -n "$temp_output" ]]; then
    rm -f "$temp_output" 2>/dev/null || true
  fi
}
trap cleanup EXIT

previous_umask="$(umask)"
noclobber_was_set=false
if [[ -o noclobber ]]; then
  noclobber_was_set=true
else
  set -o noclobber
fi
umask 022
for attempt in {1..20}; do
  candidate="$output_path.tmp.$$.${RANDOM}.${attempt}"
  if exec 3>"$candidate"; then
    temp_output="$candidate"
    temp_fd_open=true
    break
  fi
done
if [[ "$noclobber_was_set" == false ]]; then
  set +o noclobber
fi
umask "$previous_umask"
if [[ -z "$temp_output" ]]; then
  fail "could not create a unique temporary output file in $output_dir_real"
fi

if ! jq -n "${jq_args[@]}" "[$source_refs]"' |
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

  [to_entries[] |
    if (.value | length) != 1 then
      error("source \(.key + 1) must contain exactly one JSON document")
    else
      .value[0] as $document |
      validate($document; "source \(.key + 1)") |
      $document
    end] as $documents |
  $documents[0] as $base |
  reduce $documents[1:][] as $source
    ($base;
    if $base.openapi != $source.openapi then
      error("openapi versions do not match")
    elif $base.info.version != $source.info.version then
      error("info.version values do not match")
    else
      (.components // {}) as $components_one |
      ($source.components // {}) as $components_two |
      merge_named("path"; .paths; $source.paths) as $paths |
      (reduce ((($components_one | keys) + ($components_two | keys)) | unique)[] as $category
        ({};
         .[$category] = merge_named(
           "component category \"\($category)\"";
           ($components_one[$category] // {});
           ($components_two[$category] // {})
         ))) as $components |
      . + {
        paths: $paths,
        components: $components
      }
    end) |
  .info.title = "Nodo Technical Support direct API"
' >&3; then
  exec 3>&-
  temp_fd_open=false
  fail "could not validate or merge the OpenAPI inputs"
fi
exec 3>&-
temp_fd_open=false

if ! mv -f "$temp_output" "$output_path"; then
  fail "could not replace output file: $requested_output"
fi
temp_output=''
