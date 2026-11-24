#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -P "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if ! command -v jq >/dev/null 2>&1; then
  printf 'test_build_and_run_generate_openapi: jq is required\n' >&2
  exit 1
fi

fixture_root="$(mktemp -d)"
trap 'rm -rf -- "$fixture_root"' EXIT

create_fixture() {
  local fixture="$1"
  mkdir -p "$fixture/bin" "$fixture/openapi" "$fixture/scripts"
  cp "$repo_root/build-and-run.sh" "$fixture/build-and-run.sh"
  cp "$repo_root/openapi/openapi.json" "$fixture/openapi-source.json"
  cp "$repo_root/scripts/nodoTechnicalSupport_openapi.sh" \
    "$fixture/scripts/nodoTechnicalSupport_openapi.real.sh"
  cp "$repo_root/scripts/nodoTechnicalSupport_openapi.py" "$fixture/scripts/"
  cp "$repo_root/scripts/observability_openapi.sh" \
    "$fixture/scripts/observability_openapi.real.sh"

  cat >"$fixture/mvnw" <<'EOF'
#!/usr/bin/env bash
printf 'test-version\n'
EOF
  cat >"$fixture/bin/docker" <<'EOF'
#!/usr/bin/env bash
if [[ "${1:-}" == "rm" ]]; then
  printf 'docker-rm\n' >> "$EVENT_LOG"
fi
EOF
  cat >"$fixture/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'download\n' >> "$EVENT_LOG"
cat "$OPENAPI_SOURCE"
EOF
  cat >"$fixture/bin/sleep" <<'EOF'
#!/usr/bin/env bash
:
EOF
  cat >"$fixture/scripts/nodoTechnicalSupport_openapi.sh" <<'EOF'
#!/usr/bin/env bash
printf 'technical-support:%s\n' "$#" >> "$EVENT_LOG"
if [[ "${FAIL_PROJECTION:-}" == "technical-support" ]]; then
  exit 23
fi
exec bash "$(dirname "${BASH_SOURCE[0]}")/nodoTechnicalSupport_openapi.real.sh" "$@"
EOF
  cat >"$fixture/scripts/observability_openapi.sh" <<'EOF'
#!/usr/bin/env bash
printf 'observability:%s\n' "$#" >> "$EVENT_LOG"
if [[ "${FAIL_PROJECTION:-}" == "observability" ]]; then
  exit 24
fi
exec bash "$(dirname "${BASH_SOURCE[0]}")/observability_openapi.real.sh" "$@"
EOF
  chmod +x "$fixture/mvnw" "$fixture/bin/"* "$fixture/scripts/"*.sh
}

run_generation() {
  local fixture="$1"
  local fail_projection="${2:-}"
  (
    cd "$fixture"
    export EVENT_LOG="$fixture/events"
    export OPENAPI_SOURCE="$fixture/openapi-source.json"
    export PATH="$fixture/bin:$PATH"
    if [[ -n "$fail_projection" ]]; then
      export FAIL_PROJECTION="$fail_projection"
    else
      unset FAIL_PROJECTION
    fi
    bash ./build-and-run.sh generate_openapi
  )
}

success_fixture="$fixture_root/success"
create_fixture "$success_fixture"
run_generation "$success_fixture"
cat >"$fixture_root/expected-success-events" <<'EOF'
download
docker-rm
technical-support:0
observability:0
EOF
if ! cmp -s "$fixture_root/expected-success-events" "$success_fixture/events"; then
  printf 'generation events were not in the expected order:\n' >&2
  cat "$success_fixture/events" >&2
  exit 1
fi
test -s "$success_fixture/openapi/nodoTechnicalSupport.openapi.json"
test -s "$success_fixture/openapi/observability_openapi.json"

failure_fixture="$fixture_root/failure"
create_fixture "$failure_fixture"
if run_generation "$failure_fixture" technical-support; then
  printf 'generation succeeded despite a projection failure\n' >&2
  exit 1
fi
cat >"$fixture_root/expected-failure-events" <<'EOF'
download
docker-rm
technical-support:0
EOF
if ! cmp -s "$fixture_root/expected-failure-events" "$failure_fixture/events"; then
  printf 'generation did not stop after the failed projection:\n' >&2
  cat "$failure_fixture/events" >&2
  exit 1
fi

printf 'OpenAPI generation flow checks passed\n'
