#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MERGE_SCRIPT="$SCRIPT_DIR/merge_openapi.sh"
JQ="$(command -v jq)"
WORKSPACE="$SCRIPT_DIR/.test-merge-openapi.$$"
if ! mkdir "$WORKSPACE"; then
  printf 'FAIL: could not create test workspace %s\n' "$WORKSPACE" >&2
  exit 1
fi
trap 'rm -rf "$WORKSPACE"' EXIT

FIXTURES="$WORKSPACE/fixtures"
mkdir -p "$FIXTURES"
SOURCE_ONE="$FIXTURES/source-one.json"
SOURCE_TWO="$FIXTURES/source-two.json"
OUTPUT="$WORKSPACE/output/merged.json"
mkdir -p "$(dirname "$OUTPUT")"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$*"
}

write_fixtures() {
  cat >"$SOURCE_ONE" <<'JSON'
{
  "openapi": "3.0.3",
  "x-fixture-source-one": "retained",
  "info": {
    "title": "Source one",
    "version": "1.2.3",
    "description": "Description from source one"
  },
  "paths": {
    "/one": {"get": {"operationId": "getOne"}},
    "/shared": {"get": {"operationId": "getShared"}}
  },
  "components": {
    "schemas": {
      "OneSchema": {"type": "string"},
      "SharedSchema": {"type": "object", "properties": {"id": {"type": "string"}}},
      "SameSchema": {"type": "integer"},
      "ConflictSchema": {"type": "string"}
    },
    "responses": {
      "OneResponse": {"description": "one"},
      "SharedResponse": {"description": "shared"},
      "SameResponse": {"description": "identical"},
      "ConflictResponse": {"description": "source one"}
    },
    "parameters": {
      "OneParameter": {"name": "one", "in": "query", "schema": {"type": "string"}},
      "SameParameter": {"name": "same", "in": "query", "schema": {"type": "string"}},
      "ConflictParameter": {"name": "conflict", "in": "query", "schema": {"type": "string"}}
    },
    "examples": {
      "OneExample": {"value": "one"},
      "SameExample": {"value": "identical"},
      "ConflictExample": {"value": "source one"}
    },
    "requestBodies": {
      "OneRequestBody": {"description": "one", "content": {}},
      "SameRequestBody": {"description": "identical", "content": {}},
      "ConflictRequestBody": {"description": "source one", "content": {}}
    },
    "headers": {
      "OneHeader": {"description": "one", "schema": {"type": "string"}},
      "SameHeader": {"description": "identical", "schema": {"type": "string"}},
      "ConflictHeader": {"description": "source one", "schema": {"type": "string"}}
    },
    "securitySchemes": {
      "ApiKey": {"type": "apiKey", "in": "header", "name": "api-key"},
      "OneScheme": {"type": "http", "scheme": "bearer"},
      "SameScheme": {"type": "http", "scheme": "basic"},
      "ConflictScheme": {"type": "http", "scheme": "bearer"}
    },
    "links": {
      "OneLink": {"operationId": "getOne"},
      "SameLink": {"operationId": "same"},
      "ConflictLink": {"operationId": "sourceOne"}
    },
    "callbacks": {
      "OneCallback": {"{$request.body#/url}": {"post": {"responses": {"200": {"description": "ok"}}}}},
      "SameCallback": {"{$request.body#/url}": {"post": {"responses": {"200": {"description": "ok"}}}}},
      "ConflictCallback": {"{$request.body#/one}": {"post": {"responses": {"200": {"description": "one"}}}}}
    }
  }
}
JSON

  cat >"$SOURCE_TWO" <<'JSON'
{
  "openapi": "3.0.3",
  "info": {
    "title": "Source two",
    "version": "1.2.3"
  },
  "paths": {
    "/two": {"post": {"operationId": "postTwo"}},
    "/shared": {"get": {"operationId": "getShared"}}
  },
  "components": {
    "schemas": {
      "TwoSchema": {"type": "boolean"},
      "SharedSchema": {"type": "object", "properties": {"id": {"type": "string"}}},
      "SameSchema": {"type": "integer"},
      "ConflictSchema": {"type": "string"}
    },
    "responses": {
      "TwoResponse": {"description": "two"},
      "SharedResponse": {"description": "shared"},
      "SameResponse": {"description": "identical"},
      "ConflictResponse": {"description": "source one"}
    },
    "parameters": {
      "TwoParameter": {"name": "two", "in": "query", "schema": {"type": "string"}},
      "SameParameter": {"name": "same", "in": "query", "schema": {"type": "string"}},
      "ConflictParameter": {"name": "conflict", "in": "query", "schema": {"type": "string"}}
    },
    "examples": {
      "TwoExample": {"value": "two"},
      "SameExample": {"value": "identical"},
      "ConflictExample": {"value": "source one"}
    },
    "requestBodies": {
      "TwoRequestBody": {"description": "two", "content": {}},
      "SameRequestBody": {"description": "identical", "content": {}},
      "ConflictRequestBody": {"description": "source one", "content": {}}
    },
    "headers": {
      "TwoHeader": {"description": "two", "schema": {"type": "string"}},
      "SameHeader": {"description": "identical", "schema": {"type": "string"}},
      "ConflictHeader": {"description": "source one", "schema": {"type": "string"}}
    },
    "securitySchemes": {
      "ApiKey": {"type": "apiKey", "in": "header", "name": "api-key"},
      "TwoScheme": {"type": "oauth2", "flows": {}},
      "SameScheme": {"type": "http", "scheme": "basic"},
      "ConflictScheme": {"type": "http", "scheme": "bearer"},
      "DistinctScheme": {"type": "http", "scheme": "bearer"}
    },
    "links": {
      "TwoLink": {"operationId": "getTwo"},
      "SameLink": {"operationId": "same"},
      "ConflictLink": {"operationId": "sourceOne"}
    },
    "callbacks": {
      "TwoCallback": {"{$request.body#/url}": {"post": {"responses": {"200": {"description": "ok"}}}}},
      "SameCallback": {"{$request.body#/url}": {"post": {"responses": {"200": {"description": "ok"}}}}},
      "ConflictCallback": {"{$request.body#/one}": {"post": {"responses": {"200": {"description": "one"}}}}}
    }
  }
}
JSON
}

write_fixtures
"$JQ" -e . "$SOURCE_ONE" "$SOURCE_TWO" >/dev/null || fail "fixture JSON is malformed"

seed_output() {
  printf 'known output bytes\000with a NUL\n' >"$1"
  cp "$1" "$1.expected"
}

temp_pattern_for() {
  printf '%s.tmp.*' "$1"
}

assert_no_output_temps() {
  local output="$1"
  local pattern
  pattern="$(temp_pattern_for "$output")"
  if compgen -G "$pattern" >/dev/null; then
    fail "temporary output files remain for $output"
  fi
}

expect_failure() {
  local label="$1"
  local expected_diagnostic="$2"
  local output="$3"
  shift 3

  seed_output "$output"
  local stderr_file="$WORKSPACE/stderr.log"
  if "$@" >"$WORKSPACE/stdout.log" 2>"$stderr_file"; then
    fail "$label: expected nonzero exit"
  fi
  if ! grep -Eiq "$expected_diagnostic" "$stderr_file"; then
    printf 'stderr for %s:\n' "$label" >&2
    cat "$stderr_file" >&2
    fail "$label: expected diagnostic matching /$expected_diagnostic/"
  fi
  if ! cmp -s "$output.expected" "$output"; then
    fail "$label: output bytes changed"
  fi
  assert_no_output_temps "$output"
  pass "$label"
}

run_merge() {
  bash "$MERGE_SCRIPT" "$@"
}

assert_success_output() {
  local output="$1"
  local output_mode
  "$JQ" -e '
    .openapi == "3.0.3" and
    .info.version == "1.2.3" and
    .info.title == "Nodo Technical Support direct API" and
    .info.description == "Description from source one" and
    .["x-fixture-source-one"] == "retained" and
    (.paths | has("/one") and has("/two") and has("/shared")) and
    (.components.schemas | has("OneSchema") and has("TwoSchema") and has("SharedSchema") and has("SameSchema")) and
    (.components.responses | has("OneResponse") and has("TwoResponse") and has("SameResponse")) and
    (.components.parameters | has("OneParameter") and has("TwoParameter") and has("SameParameter")) and
    (.components.examples | has("OneExample") and has("TwoExample") and has("SameExample")) and
    (.components.requestBodies | has("OneRequestBody") and has("TwoRequestBody") and has("SameRequestBody")) and
    (.components.headers | has("OneHeader") and has("TwoHeader") and has("SameHeader")) and
    (.components.securitySchemes | has("ApiKey") and has("OneScheme") and has("TwoScheme") and has("DistinctScheme") and has("SameScheme")) and
    (.components.links | has("OneLink") and has("TwoLink") and has("SameLink")) and
    (.components.callbacks | has("OneCallback") and has("TwoCallback") and has("SameCallback")) and
    (.paths["/shared"] == {"get": {"operationId": "getShared"}}) and
    ([.paths | keys[] | select(. == "/shared")] | length) == 1 and
    ([.components.schemas | keys[] | select(. == "SameSchema")] | length) == 1 and
    ([.components.responses | keys[] | select(. == "SameResponse")] | length) == 1 and
    ([.components.parameters | keys[] | select(. == "SameParameter")] | length) == 1 and
    ([.components.examples | keys[] | select(. == "SameExample")] | length) == 1 and
    ([.components.requestBodies | keys[] | select(. == "SameRequestBody")] | length) == 1 and
    ([.components.headers | keys[] | select(. == "SameHeader")] | length) == 1 and
    ([.components.securitySchemes | keys[] | select(. == "ApiKey")] | length) == 1 and
    ([.components.securitySchemes | keys[] | select(. == "SameScheme")] | length) == 1 and
    ([.components.links | keys[] | select(. == "SameLink")] | length) == 1 and
    ([.components.callbacks | keys[] | select(. == "SameCallback")] | length) == 1 and
    .components.responses.ConflictResponse == {"description": "source one"} and
    .components.parameters.SameParameter == {"name": "same", "in": "query", "schema": {"type": "string"}} and
    .components.examples.SameExample == {"value": "identical"} and
    .components.requestBodies.SameRequestBody == {"description": "identical", "content": {}} and
    .components.headers.SameHeader == {"description": "identical", "schema": {"type": "string"}} and
    .components.securitySchemes.ApiKey == {"type": "apiKey", "in": "header", "name": "api-key"} and
    .components.links.SameLink == {"operationId": "same"} and
    .components.callbacks.SameCallback == {"{$request.body#/url}": {"post": {"responses": {"200": {"description": "ok"}}}}}
  ' "$output" >/dev/null || fail "successful merge output did not satisfy merge assertions"
  output_mode="$(stat -f '%Lp' "$output" 2>/dev/null || stat -c '%a' "$output" 2>/dev/null)" ||
    fail "could not inspect output file permissions"
  [[ "$output_mode" == 644 ]] || fail "successful merge output mode was $output_mode, expected 644"
}

printf 'Running merge success fixture...\n'
rm -f "$OUTPUT" "$OUTPUT.expected"
if ! (umask 077; run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$OUTPUT"); then
  fail "success fixture: merge command failed"
fi
assert_success_output "$OUTPUT"
pass "successfully merges paths and all component categories"
assert_no_output_temps "$OUTPUT"

SOURCE_THREE="$FIXTURES/source-three.json"
SOURCE_FOUR="$FIXTURES/source-four.json"
"$JQ" '.paths = {"/three": {"get": {"operationId": "getThree"}}} |
  .components = {"schemas": {"ThreeSchema": {"type": "number"}}}' "$SOURCE_TWO" >"$SOURCE_THREE"
"$JQ" '.paths = {"/four": {"get": {"operationId": "getFour"}}} |
  .components = {"schemas": {"FourSchema": {"type": "array"}}}' "$SOURCE_TWO" >"$SOURCE_FOUR"

if ! run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$SOURCE_THREE" "$SOURCE_FOUR" "$OUTPUT"; then
  fail "multiple sources: merge command failed"
fi
assert_success_output "$OUTPUT"
"$JQ" -e '.paths | has("/three") and has("/four")' "$OUTPUT" >/dev/null ||
  fail "multiple sources: missing later paths"
"$JQ" -e '.components.schemas | has("ThreeSchema") and has("FourSchema")' "$OUTPUT" >/dev/null ||
  fail "multiple sources: missing later components"
pass "merges four source arguments"

DEFAULT_REPO="$WORKSPACE/default-repo"
mkdir -p "$DEFAULT_REPO/scripts" "$DEFAULT_REPO/openapi"
cp "$MERGE_SCRIPT" "$DEFAULT_REPO/scripts/merge_openapi.sh"
cp "$SOURCE_ONE" "$DEFAULT_REPO/openapi/one.json"
cp "$SOURCE_TWO" "$DEFAULT_REPO/openapi/two.json"
cp "$SOURCE_THREE" "$DEFAULT_REPO/openapi/three.json"
cp "$SOURCE_FOUR" "$DEFAULT_REPO/openapi/four.json"
printf 'openapi/one.json\nopenapi/two.json\nopenapi/three.json\n' >"$DEFAULT_REPO/openapi/merge_sources.txt"
(cd "$WORKSPACE" && bash "$DEFAULT_REPO/scripts/merge_openapi.sh") ||
  fail "default source list: merge command failed"
"$JQ" -e '.paths | has("/one") and has("/two") and has("/three") and (has("/four") | not)' \
  "$DEFAULT_REPO/openapi/nodoTechnicalSupport_direct_openapi.json" >/dev/null ||
  fail "default source list: wrong paths"
printf 'openapi/four.json\n' >>"$DEFAULT_REPO/openapi/merge_sources.txt"
(cd "$WORKSPACE" && bash "$DEFAULT_REPO/scripts/merge_openapi.sh") ||
  fail "extended source list: merge command failed"
"$JQ" -e '.paths | has("/one") and has("/two") and has("/three") and has("/four")' \
  "$DEFAULT_REPO/openapi/nodoTechnicalSupport_direct_openapi.json" >/dev/null ||
  fail "extended source list: missing paths"
pass "uses and extends the default source list independently of the working directory"

printf 'Running failure fixtures...\n'
expect_failure "one argument" 'usage|argument' "$OUTPUT" run_merge "$SOURCE_ONE"
expect_failure "two arguments" 'usage|argument' "$OUTPUT" run_merge "$SOURCE_ONE" "$SOURCE_TWO"

DEFAULT_OUTPUT="$DEFAULT_REPO/openapi/nodoTechnicalSupport_direct_openapi.json"
printf '# only one source\n\nopenapi/one.json\n' >"$DEFAULT_REPO/openapi/merge_sources.txt"
expect_failure "too few default sources" 'source|at least two' "$DEFAULT_OUTPUT" \
  bash "$DEFAULT_REPO/scripts/merge_openapi.sh"
rm "$DEFAULT_REPO/openapi/merge_sources.txt"
expect_failure "missing source list" 'source list|missing' "$DEFAULT_OUTPUT" \
  bash "$DEFAULT_REPO/scripts/merge_openapi.sh"

"$JQ" '.paths["/two"].post.operationId = "conflicting"' "$SOURCE_THREE" >"$FIXTURES/third-conflict.json"
expect_failure "conflict in later source" 'conflict|path' "$OUTPUT" \
  run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$FIXTURES/third-conflict.json" "$OUTPUT"
"$JQ" '.components.schemas.SharedSchema = {"type": "boolean"}' "$SOURCE_THREE" >"$FIXTURES/third-component-conflict.json"
expect_failure "component conflict in later source" 'conflict|component' "$OUTPUT" \
  run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$FIXTURES/third-component-conflict.json" "$OUTPUT"
"$JQ" '.info.version = "different"' "$SOURCE_THREE" >"$FIXTURES/third-version.json"
expect_failure "version mismatch in later source" 'mismatch|differ|version' "$OUTPUT" \
  run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$FIXTURES/third-version.json" "$OUTPUT"
cp "$SOURCE_THREE" "$FIXTURES/third-multiple.json"
printf '\n{}\n' >>"$FIXTURES/third-multiple.json"
expect_failure "multiple documents in later source" 'exactly one|document' "$OUTPUT" \
  run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$FIXTURES/third-multiple.json" "$OUTPUT"
cp "$SOURCE_THREE" "$WORKSPACE/source-three.expected"
if run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$SOURCE_THREE" "$SOURCE_THREE" >"$WORKSPACE/stdout.log" 2>"$WORKSPACE/stderr.log"; then
  fail "later source output alias: expected nonzero exit"
fi
grep -Eiq 'same|alias|input|output|identif' "$WORKSPACE/stderr.log" ||
  fail "later source output alias: missing diagnostic"
cmp -s "$WORKSPACE/source-three.expected" "$SOURCE_THREE" ||
  fail "later source output alias: source bytes changed"
pass "rejects a later source as output"

REAL_REPO="$WORKSPACE/real-repo"
mkdir -p "$REAL_REPO/scripts" "$REAL_REPO/openapi"
cp "$MERGE_SCRIPT" "$REAL_REPO/scripts/merge_openapi.sh"
cp "$SCRIPT_DIR/../openapi/merge_sources.txt" "$REAL_REPO/openapi/merge_sources.txt"
while IFS= read -r source || [[ -n "$source" ]]; do
  [[ -z "$source" || "$source" == \#* ]] && continue
  mkdir -p "$REAL_REPO/$(dirname "$source")"
  cp "$SCRIPT_DIR/../$source" "$REAL_REPO/$source"
done <"$REAL_REPO/openapi/merge_sources.txt"
bash "$REAL_REPO/scripts/merge_openapi.sh" || fail "actual source list: merge command failed"
"$JQ" -e '.paths | has("/checkPosition") and has("/paymentstatus") and has("/verifyPaymentNotice")' \
  "$REAL_REPO/openapi/nodoTechnicalSupport_direct_openapi.json" >/dev/null ||
  fail "actual source list: expected API paths missing"
pass "merges the configured direct APIs"

make_case_sources() {
  local case_dir="$1"
  mkdir -p "$case_dir"
  cp "$SOURCE_ONE" "$case_dir/one.json"
  cp "$SOURCE_TWO" "$case_dir/two.json"
}

assert_failure_with_sources() {
  local label="$1"
  local diagnostic="$2"
  local source_one="$3"
  local source_two="$4"
  local output="$5"
  expect_failure "$label" "$diagnostic" "$output" run_merge "$source_one" "$source_two" "$output"
}

CASE_DIR="$WORKSPACE/cases"
make_case_sources "$CASE_DIR"
"$JQ" '."paths"."/shared".get.operationId = "conflictingShared"' "$CASE_DIR/two.json" >"$CASE_DIR/two.changed.json"
mv "$CASE_DIR/two.changed.json" "$CASE_DIR/two.json"
assert_failure_with_sources "conflicting path" 'conflict|path' "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/path-conflict.json"

for category in schemas responses parameters examples requestBodies headers securitySchemes links callbacks; do
  make_case_sources "$CASE_DIR"
  case "$category" in
    schemas) conflict_key=ConflictSchema ;;
    responses) conflict_key=ConflictResponse ;;
    parameters) conflict_key=ConflictParameter ;;
    examples) conflict_key=ConflictExample ;;
    requestBodies) conflict_key=ConflictRequestBody ;;
    headers) conflict_key=ConflictHeader ;;
    securitySchemes) conflict_key=ConflictScheme ;;
    links) conflict_key=ConflictLink ;;
    callbacks) conflict_key=ConflictCallback ;;
  esac
  "$JQ" --arg category "$category" --arg key "$conflict_key" '
    .components[$category][$key] = {conflicting: true}
  ' "$CASE_DIR/two.json" >"$CASE_DIR/two.changed.json"
  mv "$CASE_DIR/two.changed.json" "$CASE_DIR/two.json"
  assert_failure_with_sources "conflicting $category definition" 'conflict|different|same key|component' \
    "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/$category-conflict.json"
done

set_json_value() {
  local file="$1"
  local key="$2"
  local value_type="$3"
  local value="${4-}"
  case "$value_type" in
    missing)
      "$JQ" "del(.$key)" "$file" >"$file.changed"
      ;;
    null)
      "$JQ" ".$key = null" "$file" >"$file.changed"
      ;;
    empty)
      "$JQ" ".$key = \"\"" "$file" >"$file.changed"
      ;;
    string)
      "$JQ" --arg value "$value" ".$key = \$value" "$file" >"$file.changed"
      ;;
    number)
      "$JQ" ".$key = 42" "$file" >"$file.changed"
      ;;
    *)
      fail "unknown JSON value type: $value_type"
      ;;
  esac
  mv "$file.changed" "$file"
}

for field in openapi info.version; do
  for position in one two; do
    make_case_sources "$CASE_DIR"
    if [[ "$position" == one ]]; then
      file="$CASE_DIR/one.json"
    else
      file="$CASE_DIR/two.json"
    fi
    set_json_value "$file" "$field" string "different-version"
    assert_failure_with_sources "mismatched $field in source $position" 'mismatch|differ|version|openapi' \
      "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/${field//./-}-$position-mismatch.json"

    for kind in missing null empty number; do
      make_case_sources "$CASE_DIR"
      if [[ "$position" == one ]]; then
        file="$CASE_DIR/one.json"
      else
        file="$CASE_DIR/two.json"
      fi
      set_json_value "$file" "$field" "$kind"
      assert_failure_with_sources "$kind $field in source $position" 'missing|invalid|required|version|openapi' \
        "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/${field//./-}-$position-$kind.json"
    done
  done
done

for position in one two; do
  make_case_sources "$CASE_DIR"
  if [[ "$position" == one ]]; then
    rm "$CASE_DIR/one.json"
  else
    rm "$CASE_DIR/two.json"
  fi
  assert_failure_with_sources "missing source $position" 'input|file|source|no such' \
    "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/missing-source-$position.json"

  make_case_sources "$CASE_DIR"
  printf '{ malformed json\n' >"$CASE_DIR/$position.json"
  assert_failure_with_sources "malformed source $position" 'json|parse|valid|invalid' \
    "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/malformed-source-$position.json"

  make_case_sources "$CASE_DIR"
  printf '\n{}\n' >>"$CASE_DIR/$position.json"
  assert_failure_with_sources "multiple JSON documents in source $position" 'multiple|exactly one|document' \
    "$CASE_DIR/one.json" "$CASE_DIR/two.json" "$WORKSPACE/output/multiple-documents-$position.json"
done

printf 'known directory sentinel\000bytes\n' >"$WORKSPACE/output-directory-sentinel"
mkdir -p "$WORKSPACE/directory-output"
cp "$WORKSPACE/output-directory-sentinel" "$WORKSPACE/directory-output/sentinel"
if run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$WORKSPACE/directory-output" >"$WORKSPACE/stdout.log" 2>"$WORKSPACE/stderr.log"; then
  fail "directory output: expected nonzero exit"
fi
grep -Eiq 'directory|output|file' "$WORKSPACE/stderr.log" || fail "directory output: missing diagnostic"
cmp -s "$WORKSPACE/output-directory-sentinel" "$WORKSPACE/directory-output/sentinel" ||
  fail "directory output: sentinel bytes changed"
assert_no_output_temps "$WORKSPACE/directory-output/merged.json"
pass "rejects directory output without changing its sentinel"

SHIM_DIR="$WORKSPACE/shims"
mkdir -p "$SHIM_DIR"
for utility in bash dirname touch mv realpath readlink cat rm mkdir cp cmp find grep basename awk sed tr cut sort; do
  utility_path="$(command -v "$utility")"
  ln -s "$utility_path" "$SHIM_DIR/$utility"
done

expect_failure "missing jq" 'jq|dependenc|command' "$OUTPUT" \
  env PATH="$SHIM_DIR" bash "$MERGE_SCRIPT" "$SOURCE_ONE" "$SOURCE_TWO" "$OUTPUT"

RACE_SHIM="$WORKSPACE/race-shim"
RACE_OUTPUT="$WORKSPACE/output/symlink-race.json"
RACE_SENTINEL="$WORKSPACE/symlink-race-sentinel"
mkdir -p "$RACE_SHIM"
for utility in bash dirname mv realpath readlink cat rm mkdir cp cmp find grep basename jq awk sed tr cut sort ln stat; do
  ln -s "$(command -v "$utility")" "$RACE_SHIM/$utility"
done
RACE_HOOK="$WORKSPACE/race-hook.sh"
RACE_HOOK_LOG="$WORKSPACE/race-hook.log"
cat >"$RACE_HOOK" <<'SH'
race_inject_competing_symlink() {
  if [[ "$BASH_COMMAND" == *candidate* ]]; then
    trap - DEBUG
    if [[ "$BASH_COMMAND" == *'exec 3>'* && -z "${RACE_HOOK_RAN:-}" ]]; then
      if ln -s "$RACE_SENTINEL" "$candidate"; then
        printf '%s\n' "$candidate" >"$RACE_HOOK_LOG"
        RACE_HOOK_RAN=1
        export RACE_HOOK_RAN
      else
        printf 'could not inject symlink at %s\n' "$candidate" >"$RACE_HOOK_LOG"
      fi
    fi
    trap race_inject_competing_symlink DEBUG
  fi
}
trap race_inject_competing_symlink DEBUG
SH
printf 'symlink race sentinel bytes\n' >"$RACE_SENTINEL"
cp "$RACE_SENTINEL" "$RACE_SENTINEL.expected"
if ! env PATH="$RACE_SHIM" BASH_ENV="$RACE_HOOK" RACE_SENTINEL="$RACE_SENTINEL" \
  RACE_HOOK_LOG="$RACE_HOOK_LOG" bash "$MERGE_SCRIPT" \
  "$SOURCE_ONE" "$SOURCE_TWO" "$RACE_OUTPUT" >"$WORKSPACE/stdout.log" 2>"$WORKSPACE/stderr.log"; then
  cat "$WORKSPACE/stderr.log" >&2
  fail "symlink race fixture: merge command failed"
fi
[[ -s "$RACE_HOOK_LOG" ]] || fail "symlink race fixture: competing-symlink hook did not run"
RACE_CANDIDATE="$(cat "$RACE_HOOK_LOG")"
cmp -s "$RACE_SENTINEL.expected" "$RACE_SENTINEL" ||
  fail "temporary-file creation followed a symlink and changed the sentinel"
[[ -L "$RACE_CANDIDATE" ]] || fail "symlink race fixture: injected candidate was not retained for retry"
[[ "$(readlink "$RACE_CANDIDATE")" == "$RACE_SENTINEL" ]] ||
  fail "symlink race fixture: injected candidate did not target the sentinel"
rm "$RACE_CANDIDATE"
assert_success_output "$RACE_OUTPUT"
assert_no_output_temps "$RACE_OUTPUT"
pass "temporary-file creation does not follow a competing symlink"

READONLY_DIR="$WORKSPACE/read-only-output"
mkdir -p "$READONLY_DIR"
READONLY_OUTPUT="$READONLY_DIR/merged.json"
seed_output "$READONLY_OUTPUT"
chmod 500 "$READONLY_DIR"
if run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$READONLY_OUTPUT" >"$WORKSPACE/stdout.log" 2>"$WORKSPACE/stderr.log"; then
  chmod 700 "$READONLY_DIR"
  fail "temporary output creation failure: expected nonzero exit"
fi
chmod 700 "$READONLY_DIR"
grep -Eiq 'temp|create|output' "$WORKSPACE/stderr.log" ||
  fail "temporary output creation failure: missing diagnostic"
cmp -s "$READONLY_OUTPUT.expected" "$READONLY_OUTPUT" ||
  fail "temporary output creation failure: output bytes changed"
assert_no_output_temps "$READONLY_OUTPUT"
pass "temporary output creation failure leaves existing output intact"

FAILING_MV="$WORKSPACE/failing-mv"
mkdir -p "$FAILING_MV"
for utility in bash dirname touch realpath readlink cat rm mkdir cp cmp find grep basename jq awk sed tr cut sort; do
  ln -s "$(command -v "$utility")" "$FAILING_MV/$utility"
done
cat >"$FAILING_MV/mv" <<'SH'
#!/usr/bin/env bash
echo "mv deliberately failed" >&2
exit 1
SH
chmod +x "$FAILING_MV/mv"
expect_failure "output replacement failure" 'replace|output|mv|write' "$OUTPUT" \
  env PATH="$FAILING_MV" bash "$MERGE_SCRIPT" "$SOURCE_ONE" "$SOURCE_TWO" "$OUTPUT"

alias_failure() {
  local label="$1"
  local output_alias="$2"
  local source="$3"
  local source_copy="$4"
  local stderr_file="$WORKSPACE/stderr.log"
  if run_merge "$SOURCE_ONE" "$SOURCE_TWO" "$output_alias" >"$WORKSPACE/stdout.log" 2>"$stderr_file"; then
    fail "$label: expected nonzero exit"
  fi
  grep -Eiq 'same|alias|input|output|identif' "$stderr_file" || fail "$label: missing alias diagnostic"
  cmp -s "$source_copy" "$source" || fail "$label: source bytes changed"
  assert_no_output_temps "$output_alias"
  pass "$label"
}

cp "$SOURCE_ONE" "$WORKSPACE/source-one.expected"
cp "$SOURCE_TWO" "$WORKSPACE/source-two.expected"
(
  cd "$WORKSPACE"
  alias_failure "relative source one output" "fixtures/source-one.json" "$SOURCE_ONE" "$WORKSPACE/source-one.expected"
  alias_failure "absolute source one output" "$SOURCE_ONE" "$SOURCE_ONE" "$WORKSPACE/source-one.expected"
  alias_failure "relative source two output" "fixtures/source-two.json" "$SOURCE_TWO" "$WORKSPACE/source-two.expected"
  alias_failure "absolute source two output" "$SOURCE_TWO" "$SOURCE_TWO" "$WORKSPACE/source-two.expected"
)

ln -s "$SOURCE_ONE" "$WORKSPACE/source-one-symlink.json"
ln "$SOURCE_TWO" "$WORKSPACE/source-two-hardlink.json"
alias_failure "symlink source one output" "$WORKSPACE/source-one-symlink.json" "$SOURCE_ONE" "$WORKSPACE/source-one.expected"
alias_failure "hard-link source two output" "$WORKSPACE/source-two-hardlink.json" "$SOURCE_TWO" "$WORKSPACE/source-two.expected"

assert_check_position_openapi3() {
  local document="$1"
  local label="$2"
  "$JQ" -e '
    (.paths["/checkPosition"].post) as $operation |
    .openapi == "3.0.1" and
    $operation.requestBody.required == true and
    $operation.requestBody.content["application/json"].schema["$ref"] == "#/components/schemas/CheckPosition" and
    (($operation | has("produces")) | not) and
    (all(($operation.parameters // [])[]; .in != "body")) and
    (all(["200", "400", "404", "408", "422"][];
      . as $status |
      ($operation.responses[$status] | has("schema") | not) and
      ($operation.responses[$status].content["application/json"].schema["$ref"] ==
        (if $status == "200" then "#/components/schemas/CheckPositionResponse"
         else "#/components/schemas/Error" end))
    )) and
    $operation.responses["200"].description == "successful operation" and
    $operation.responses["400"].description == "Bad Request" and
    $operation.responses["404"].description == "Not found" and
    $operation.responses["408"].description == "Request Timeout" and
    $operation.responses["422"].description == "Unprocessable entry"
  ' "$document" >/dev/null || fail "$label does not use the expected OpenAPI 3 request/response shapes"
  pass "$label uses OpenAPI 3 request/response shapes"
}

for document in \
  "$SCRIPT_DIR/../openapi/checkPosition_openapi.json" \
  "$SCRIPT_DIR/../openapi/nodoTechnicalSupport_direct_openapi.json"; do
  assert_check_position_openapi3 "$document" "$(basename "$document")"
done

printf 'All merge OpenAPI regression tests passed.\n'
