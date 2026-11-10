# Node tech support API

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-node-technical-support-worker&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-node-technical-support-worker)


---
## API Documentation 📖
See the [OpenAPI 3 here.](https://editor.swagger.io/?url=https://raw.githubusercontent.com/pagopa/pagopa-node-technical-support-worker/main/openapi/openapi.json)

---
## Technology Stack
- Maven
- Java 17
- Quarkus
---

## Observability RE ingestion

The optional Event Hubs consumer admits only `CAMBIO_STATO` events with a `payment_`,
`position_` or `receipt_recipient_` status. It validates the suffix against the Nodo's documented,
entity-specific enums; unsupported aliases and missing/invalid correlation keys are recorded as
technical anomalies without changing the projection or persisting a payment token in the anomaly
record. Interface, RPT, and cart events remain outside this projection.

`payment_*` changes only the `paymentToken` attempt, `position_*` only the logical
`(paFiscalCode, noticeNumber)` timeline, and `receipt_recipient_*` only the receipt recipient — a
third entity keyed by the logical pair plus the `stazione_recipient` from `info` (the payment token
travels in the receipt but does not identify the recipient), with its own 4-state FSM, retention,
`PaymentDetail.recipients[]` projection and `recipient` aggregates. An attempt can therefore be visible before
its position state; its logical `state` is `null` and `timelineIncomplete` remains explicit
until a position event is observed. The source `tipoEvento` is preserved as the optional
timeline `trigger` on REST snapshots and SSE deltas, including inferred entries and indexed
time corrections; no trigger is inferred from a state name.

The snapshot contract requires `recipients` (including an empty array),
`recipientStates` on overview/creditor detail, and `matrices.recipient`. Recipients
have exactly `recipientId`, nullable `stazioneRecipient`, `paFiscalCode`, `noticeNumber`,
`state`, `timelineIncomplete`, `hasAnomaly`, `timeline`, and `projectionRevision`.
They have no attempt association, primary role, token, or warning. Aggregate transition
and duration rows use `entity: recipient`. The independent catalogs remain 5 logical,
18 attempt, and 4 recipient states.

All timelines preserve persisted transition sequence, including retry visits and null
or nonmonotonic dates. `transitionAt` and the original `trigger` are nullable;
`timestampBasis` is `SOURCE_EVENT`, `SOURCE_SYNC`, `INFERRED`, or `ANOMALY`.
Attempt/recipient notifications never advance the logical position. Attempt sync without
a logical source snapshot leaves logical state null and its timeline incomplete.
Late recipient source events can replace a uniquely identifiable inferred occurrence
in place, correcting its timestamp, basis, and trigger without regressing current state.
The outbox publishes a complete recipient upsert with no appended transition; repeated
occurrences are not matched heuristically.
`PAYMENT_ACCEPTED` is not `PAID`, and `NOTIFIED` does not imply a PA business outcome of OK.
Only current attempt states `PAYMENT_UNKNOWN` and `PAYMENT_SEND_ERROR` produce warnings.
An observed attempt send after `NOTICE_STORED` is retained with incomplete-history
provenance; it does not establish a generic FSM edge or invent a late-RPT trigger.

Recipient retention is independent: the first known recipient event time plus 60 days
(first technical observation when its event time is absent). An earlier accepted event
can shorten that deadline; later events never extend it. Expiring a recipient removes only
that recipient while retained attempts remain. Expiry of the last retained attempt caps
parent availability and also removes its recipients. A recipient observed before any
attempt remains available until its own deadline. Cleanup atomically increments payment
and aggregate revisions and writes independent removal IDs and `deleted` to the outbox.
Live streams receive removals; replay is invalidated at that publication boundary so a
reconnect must refresh instead of restoring expired histories.

`payment.delta.changes` supports optional `recipientChanges` and `removedRecipientIds`,
independently of `attemptChanges`. Each recipient change contains `recipientId` plus
appended `transitions`, a complete `upsert`, or a `patch.timelineIncomplete`.
Aggregate deltas include signed `recipientStates` and `matrices.recipient` differences,
with `durationSnapshot` as an authoritative replacement, even when empty.
The generated JSON/YAML OpenAPI describes these SSE data envelopes and strict snapshots;
missing required recipient support must not be interpreted as an empty success.

Inbox deduplication prefers RE `uniqueId`. When absent, it uses a namespaced SHA-256 digest of
the Event Hubs `(eventHub, partitionId, sequenceNumber)` transport key. Inbox, projection,
revision, and outbox writes share the ingestion transaction; the transport checkpoints only
after that transaction commits.

## Primitive calls per `serviceIdentifier`

The dashboard shows, per `serviceIdentifier`, how many primitive requests were sent, how many got a
response and how many are warnings, with the detail per primitive (`tipoEvento`, original name) (spec §3.3.2 in `pagopa-nodo-obervability`). The overview snapshot
exposes one row per (`serviceIdentifier`, `primitive`, `direction`) as `Overview.primitives` and the SSE `aggregate.delta` as `changes.primitives`.

* **Source.** The RE interface events (`categoriaEvento = INTERFACCIA`, `sottoTipoEvento` `REQ`/`RESP`)
  that the state-change projection drops. `PrimitiveIngestionService` pairs them by
  (`sessionId`, `tipoEvento`) in the transient `primitive_call` table; only per-minute counters
  (`primitive_count`: requests, responses, warnings per service, primitive and direction) are kept, plus one
  `primitive_warning` row per warning (see "Warning detail"). No payload and no payment key are stored.
  `sessionId` lives in the work table only until the call is settled or `pending-ttl-seconds` (300) elapses:
  this is a deliberate, documented exception to the "no `sessionId`" rule, limited to pairing.
* **Direction.** `direction` is `INBOUND` when the RE `erogatore` of the `REQ` is `NodoDeiPagamentiSPC` (others call the Nodo),
  `OUTBOUND` when the `fruitore` is (the Nodo calls a PA or PSP) and `UNKNOWN` otherwise; it is never derived from the
  primitive name (`PrimitiveDirection`). Counters recorded before migration `0008` stay `UNKNOWN`.
* **Warning.** A call is a warning, exactly once, when its response arrives more than
  `observability.primitives.response-budget-seconds` (10) after the request, or when no response has
  arrived once that budget has elapsed since both the request time and its ingestion
  (`PrimitiveTimeoutSweeper`, every `sweep-interval`). A late response is still counted as a response.
  Calls are attributed to the minute of their request.
* **Warning detail.** `GET /api/overview/primitive-warnings?primitive=<tipoEvento>` lists the calls behind a
  `warnings` count, newest first and cursor-paginated: `calledAt` (UTC request time), `psp`, `canale`,
  `paFiscalCode` (`idDominio`) and `stazione`, each `null` when the RE event did not carry it. It accepts
  the overview window (`from`, `to`, `live`) and the `serviceIdentifier`/`psp`/`canale`/`stazione`
  filters (they can be combined, as it is a list), applies the same minute attribution and caller
  restriction as the counters, so the list size equals the `warnings` of the matching row. A row is written
  to `primitive_warning` (migration `0007`) exactly when a call becomes a warning, with the request
  attributes held in `primitive_call` (`pa_fiscal_code` was added there for this). No payload, token,
  notice number or `sessionId` is stored; the rows follow `observability.retention.days`. Not published
  via SSE: the console reads the list on demand. Cursors expire (`410`) on every aggregate revision change.
* **Publication.** Every counted event bumps the aggregate revision and writes an aggregate-only
  outbox row (`outbox_event.primitive_changes`, signed differences) that `OutboxPublisher` coalesces.
* **Filters.** `serviceIdentifier`, `psp`, `canale` and `stazione` (and `~unknown`) apply. State, anomaly
  and creditor filters do not (counters carry no attempt state or creditor), and a creditor-restricted
  caller gets no primitives. `creditor`/`station` snapshots omit the field; their deltas carry `[]`.
* **Rolling window.** `live=true` (overview only, REST and SSE) keeps the window open on the right for the
  primitive counters, so requests after `to` count in the snapshot and in the SSE deltas; the frontend sends it for
  preset ranges. Without it the window is the closed `[from, to)`.
* **Retention.** Counters follow `observability.retention.days`, swept with the projection.
* **Volume.** REQ/RESP outnumber state changes and each one writes an inbox row (idempotent redelivery)
  and an outbox row; size the inbox and the Event Hubs consumer accordingly.

## Read-only Check DB

`GET /api/attempts/{attemptId}/check-db` implements the console's manual **Check DB**
action against the real Nodo Online JDBC datasources. It is separate from
`POST /internal/observability/sync/*`: it never reconciles or writes the projection,
timelines, warnings, audit records or outbox.

The worker loads the retained attempt by its indexed ID, respecting the caller's
creditor scope. It derives PA/NAV, token and `serviceIdentifier` server-side; no query
parameters, raw tokens or datasource overrides are accepted. The existing
`NodeSnapshotRouter` selects `online-ndp001` or `online-ndp004` from the configured
service identifiers, with its existing trim/case-insensitive matching.

The source reads share one read-only `REPEATABLE_READ` transaction:

| Entity | Table | Bound keys |
| --- | --- | --- |
| Logical payment | `online.position_status_snapshot` | `pa_fiscal_code`, `notice_id` |
| Attempt | `online.position_payment_status_snapshot` | `pa_fiscal_code`, `notice_id`, `payment_token` |

Each query has a five-second timeout and reads at most two rows. No matching row
is returned as an explicit, independent `null` state. Duplicate rows (even with
identical states), null statuses and states outside the entity's enum fail explicitly:
unlike authoritative synchronization, Check DB does not pick a “latest” row.
Ensure the source tables have indexes covering these keys; the worker does not
run source migrations or fall back to another database.

The response contains `source: "NODO_ONLINE"`, the attempt/payment identifiers,
`serviceIdentifier`, `checkedAt` (UTC milliseconds), the captured
`projectionRevision`, `observed: { logicalState, attemptState }` and
`database: { logicalState, attemptState }`. The projection is checked again after
the source read for retention and identity changes. Concurrent state changes do
not rewrite the captured observation; the console marks the old revision as stale.
The two database systems do not share a distributed transaction, and state
equality is not proof of a positive payment outcome.

| HTTP | Meaning |
| --- | --- |
| `400 INVALID_REQUEST` | Query parameters supplied |
| `404 NOT_FOUND` | Attempt absent, outside scope or expired, including during the check |
| `422 DB_SERVICE_MISSING` | Attempt has no service identifier |
| `422 DB_SERVICE_UNCONFIGURED` | Service identifier is not mapped to a source |
| `500 INTERNAL_ERROR` | Ambiguous/invalid source snapshot, incomplete observed state/token, identity changed during read, or internal error |
| `503 DB_UNAVAILABLE` | Source connection/query failure or timeout; never a partial result |

The existing implicit reader identity remains unchanged; no token-reveal capability
is required. Responses have `Cache-Control: no-store` and contain no raw token,
SQL, connection string, payload or additional payment data. Database exception
messages are not logged because they may include bound values.

Runtime configuration uses `ONLINE_NDP001_IDENTIFIER` (default `ndp001`) and
`ONLINE_NDP004_IDENTIFIER` (default `ndp004prod`), plus
`ONLINE_NDP001_DATASOURCE_URL`, `ONLINE_NDP001_DATASOURCE_USERNAME`,
`ONLINE_NDP001_DATASOURCE_PASSWORD`, and the corresponding
`ONLINE_NDP004_DATASOURCE_*` variables. Use read-only database roles with `SELECT`
on both snapshot tables. These are the existing datasource bindings; Check DB
adds no new credentials or connections. Deployment connectivity/permissions must
be verified in the target environment; automated tests use isolated H2 databases
and JDBC mocks, not a production Nodo Online instance.

## Cache version of NDP001 and NDP004

`GET /api/nodo-monitoring/cache/version` calls the Nodo monitoring API
(`{NODO_MONITORING_URL}/monitoring/v1/cache/version`) once per node, in parallel,
selecting the instance with the `X-Ndp-Target` header and authenticating with
`Ocp-Apim-Subscription-Key`. The two results are aggregated in one response:

```json
{
  "checkedAt": "2026-11-04T12:00:00.000Z",
  "nodes": [
    { "node": "NDP001", "target": "NDP001DEV", "outcome": "OK", "dbStatus": "UP",
      "cacheId": "…", "version": "3.42.1", "environment": "DEV", "serviceIdentifier": "NDP001DEV" },
    { "node": "NDP004", "target": "NDP004IT", "outcome": "UNAVAILABLE", "dbStatus": null,
      "cacheId": null, "version": null, "environment": null, "serviceIdentifier": null }
  ]
}
```

A node never fails the whole response: `outcome` is `OK`, `UNCONFIGURED` (no
subscription key, no call made) or `UNAVAILABLE` (HTTP error, connection failure or
timeout), with null cache fields when not `OK`. Query parameters are rejected with
`400 INVALID_REQUEST`; responses have `Cache-Control: no-store`. The subscription key
is never logged or returned.

| Variable | Default |
| --- | --- |
| `NODO_MONITORING_URL` | `https://api.dev.platform.pagopa.it/nodo-monitoring` |
| `NODO_MONITORING_NDP001_TARGET` | `NDP001DEV` |
| `NODO_MONITORING_NDP004_TARGET` | `NDP004IT` |
| `NODO_MONITORING_SUBSCRIPTION_KEY` | empty (shared by both nodes) |
| `NODO_MONITORING_NDP001_SUBSCRIPTION_KEY` / `NODO_MONITORING_NDP004_SUBSCRIPTION_KEY` | override of the shared key |

## Scheduled station maintenances

`GET /api/apiconfig/maintenances` serves the station maintenances starting in `[now, now + 7 days]`
from an in-memory cache. The cache calls apiconfig
(`{APICONFIG_URL}/brokers/station-maintenances/aggregated?startDateTimeAfter=…&endDateTimeBefore=…`)
at startup, lazily on first use if still empty, and every day at midnight
(`observability.maintenance.cron`, zone `Europe/Rome`). If a refresh fails the previous data is
kept and returned with `stale: true`; if apiconfig never answered the endpoint returns
`503 MAINTENANCE_UNAVAILABLE`. Only the first upstream page is cached (a warning is logged when
`total_pages > 1`). The subscription key, when set, is never logged or returned.

| Variable | Default |
| --- | --- |
| `APICONFIG_URL` | `https://api.dev.platform.pagopa.it/apiconfig/auth/api/v1` |
| `APICONFIG_SUBSCRIPTION_KEY` | empty (header omitted) |

Timeouts: 2 s connect, 5 s read per call (`quarkus.rest-client.nodo-monitoring.*`),
6 s overall per node (`observability.monitoring.timeout-seconds`).

## Read-only RE attempt diagnostics (disabled by default)

`POST /api/attempts/{attemptId}/re-events` accepts **only** a JSON body
`{"from":"2026-10-23T10:00:00Z","to":"2026-10-23T11:00:00Z","cursor":null}`.
The explicit UTC `Z` interval is half-open `[from,to)`, nonempty and at most 24 hours.
There is no server-invented timestamp, automatic fan-out, token query parameter,
alternative correlation or unbounded scan. The retained attempt repository supplies
PA/NAV/token and expiry, respecting the existing caller scope. Projection/FSM,
warnings, timelines, outbox and database audit tables are never modified.

**Position scope.** An optional `"scope":"POSITION"` drops the `paymentToken` key: the
query is the **AND** of `idDominio` and `noticeNumber` only, so calls carrying no token
(`verifyPaymentNotice`, `paVerifyPaymentNotice`) are returned together with the events of
every token of the pair. The attempt is still the anchor that resolves PA/NAV and
authorizes the caller; the response carries the warning `POSITION_SCOPE`, and cursors are
bound to the scope. The default scope `ATTEMPT` is unchanged.

**Time zone.** The RE stores `insertedTimestamp` and `dataOraEvento` as `Europe/Rome`
wall-clock time without offset (ADX shows them as datetimes with a misleading `Z`). The
UTC window and seek key are converted to Rome local time before querying, and both
timestamps are returned as real UTC instants (`ReTime`; same IANA rules and overlap policy
as the ingestion: first occurrence in the autumn overlap).

The separate query-only Kusto SDK client uses Azure AD application credentials
(client ID, client secret and tenant ID), matching `pagopa-stand-in-manager`, and
the parameterized **AND** of `idDominio`, `noticeNumber`, `paymentToken` (no token in
position scope), plus the `insertedTimestamp` bounds, converted to Rome local time. There are **no** primitive, component,
category, `esito`, status or FSM filters. Event source scalars and nulls are preserved;
unknown states and timestamp strings (including nanoseconds) are not normalized.
Every event retains all 33 API contract fields and the three flat metadata properties
`payloadDecoded`, `payloadOmitted`, `payloadSourceBytes`; the JSON/YAML OpenAPI
lists them. The reviewed ADX table provides 21 of those fields. The 12 absent
fields (`componente`, `tipoVersamento`, `fruitore`, `erogatore`,
`parametriSpecificiInterfaccia`, `esito`, `info`, `businessProcess`, `fruitoreDescr`,
`erogatoreDescr`, `standIn`, `version`) are explicitly projected as `dynamic(null)`
and returned as JSON null, not read from nonexistent source columns. This is a
fixed schema mapping, not automatic tolerance for other missing columns.
Plain decoded text is never XML/HTML-parsed or decompressed.

The response is
`{attemptId,source:"RE",retrievedAt,window:{from,to},coverage,events,nextCursor,truncated,warnings}`.
`coverage` is `WINDOW_ONLY`, `RETENTION_CLIPPED` or `LIMIT_REACHED` — **never full history**.
The actual window is clipped to server now and configured source retention.
An empty retained window has no warning; partial retention has `RETENTION_CLIPPED`;
an entirely unavailable old interval has `OUTSIDE_RETENTION` and no source query
(its response window remains the requested interval, with no claimed intersection).
Entirely future intervals similarly have `OUTSIDE_AVAILABLE_WINDOW`.
`WINDOW_CLIPPED_TO_NOW` makes upper-bound clipping explicit.

For pagination, resend the **original requested from/to**, not the clipped response
window. Signed opaque seek cursors freeze the effective window and ingestion
watermark, bind caller/attempt/original range/configuration/limits, and expire
within 300 seconds from the first request (no sliding extension). Ordering uses
`insertedTimestamp` and an explicitly configured, verified stable technical row ID,
**not** an assumed-unique RE `uniqueId`. Missing IDs, duplicate seek keys (including
the lookahead row) or unordered rows fail rather than silently lose/duplicate events.
The consultation returns at most 1000 events; reaching that limit with more data
sets `coverage: LIMIT_REACHED`, `truncated: true`, no cursor and
`CONSULTATION_ROW_LIMIT`. Response-byte paging adds `RESPONSE_BYTE_LIMIT`, preserves
seek continuity and returns a next cursor without claiming final truncation.

Defaults/hard upper bounds: 15-second query deadline, 100 events/page, 1000
events/consultation, **5 MiB serialized JSON per response**, 256 KiB decoded payload.
Original Base64 is retained unless oversized: then `payload: null`,
`payloadOmitted: true`, `payloadDecoded.status: TOO_LARGE` and original UTF-8 source
byte count are explicit. Canonical padded Base64 is strictly validated before
strict UTF-8 decoding; invalid values remain in the original field with
`INVALID_BASE64`/`INVALID_UTF8` and null decoded text. Null payload is `ABSENT`.
Decoding has a size preallocation guard. ADX has a separate 64 MiB bounded source
result ceiling; exceeding it, missing expected result columns, non-scalar fields or a single event
too large for a response returns 502, never a partial success.

### Activation gate (not verified against real ADX)

Leave `observability.re.enabled=false` until **all** checks below are independently
reviewed for the target environment. Boolean properties are operator assertions,
not automatic verification. No real ADX connectivity, credentials, schema,
retention, timezone, source uniqueness or personal-data review was performed here.

1. Set server-only `observability.re.endpoint`, `.database`, `.table`,
   `.row-id-column` and the **actual** `.retention-days`; no retention is assumed
   (default `0` blocks activation). Endpoint must be an HTTPS cluster root; database,
   table and column identifiers match `[A-Za-z_][A-Za-z0-9_]{0,127}`.
   Connection environment variables are `RE_DATA_EXPLORER_URL`,
   `RE_DATA_EXPLORER_CLIENT_ID`, `RE_DATA_EXPLORER_CLIENT_SECRET`,
   `RE_DATA_EXPLORER_TENANT_ID` and `RE_DATA_EXPLORER_DB_NAME` (default `re`).
   Credentials bind to `observability.re.client-id`, `.client-secret` and
   `.tenant-id`; missing or blank credentials block activation with 503.
   Legacy `OBSERVABILITY_RE_ENDPOINT` and `OBSERVABILITY_RE_DATABASE` remain
   fallbacks when the corresponding `DATA_EXPLORER_*` variables are absent.
   Source-specific environment placeholders are `OBSERVABILITY_RE_TABLE`,
   `OBSERVABILITY_RE_ROW_ID_COLUMN`, `OBSERVABILITY_RE_RETENTION_DAYS`.
2. Grant the configured Azure AD application only database **Viewer**
   access to the reviewed RE source. Helm dev/uat/prod use the same cluster,
   client ID, tenant ID and database bindings as `pagopa-stand-in-manager`;
   `DATA_EXPLORER_CLIENT_SECRET` references `dataexplorer-client-secret` in each
   environment's existing Key Vault. Ensure that secret exists and the application
   has the required read permissions before deployment; never commit its value.
   No ingestion/management client is added. The SDK uses `executeToJsonResult` with fixed, declared-query
   KQL (routed to the V2 query endpoint) and `request_readonly`; no caller commands
   or management execution are accepted. SDK 5.0.2's parsed result ignores V2
   completion frames, so the worker first validates the raw V2 response: exactly
   one primary table and one successful final `DataSetCompletion` are mandatory.
   Failed/cancelled, missing, malformed or ambiguous completion, error arrays and
   unsupported progressive fragments fail 502; explicit
   `E_QUERY_EXECUTION_TIMEOUT` fails 504. No failed result can become an empty success.
3. Verify the 21 source column names/types, the 12 explicit null projections and
   correlation semantics; verify
   `insertedTimestamp` is the intended window column (Rome wall-clock time, converted by
   `ReTime`), the source
   retention definition/horizon, and ingestion-time policy. Independently confirm
   immutable stable technical row IDs, ordinal sort semantics, and ingestion
   watermark behavior under concurrent/late ingestion and strong consistency.
   A verified watermark must exclude subsequently visible new rows; the code
   does not claim that an unreviewed `ingestion_time()` policy provides this.
4. Explicitly set `.schema-verified`, `.timezone-verified`, `.retention-verified`,
   `.ordering-verified`, `.watermark-verified` and `.personal-data-approved` to
   `true` only after the associated reviews. Payload/`info`/interface fields can
   contain personal data; approve the expanded diagnostic visibility and the
   absence of IAM in this phase before enabling.
5. Confirm infrastructure/APIM/access logs and tracing **do not capture request
   bodies, response bodies, query values or source parameters**. Generic worker
   request/response logging is bypassed for this route. Application audit logs
   contain generated correlation ID, duration, HTTP outcome and event count only.
   Automatic HTTP sampling drops this diagnostic route, including rejected query
   values, while preserving the configured sampler for other normal routes.
   Request logging and sampling share conservative path normalization for matrix
   parameters and percent-encoded paths/query delimiters. The request logger uses
   the already-decoded `UriInfo.getPath()` and preserves literal `?`/`#` in path
   parameters; only raw telemetry targets undergo query/fragment stripping.
   Sampling conservatively checks both raw and decoded representations.
   Malformed or excessively
   nested encodings suppress telemetry rather than risk recording secret values.
   Kusto SDK tracing is disabled; Kusto/auth/Apache wire/header logging is off.
   All HTTP responses, including errors, are `Cache-Control: no-store`.
6. Enable `.enabled` (or `OBSERVABILITY_RE_DIAGNOSTICS_ENABLED=true`) last.
   Limits can be lowered, never raised past the documented hard bounds.

Admission defaults to four concurrent reads and 60 requests/minute, fail-fast
without queuing. These quotas and the bounded 2048-entry cursor store are
**per process**; apply a shared APIM quota and sticky routing for multi-replica
deployments. Cursors deliberately store only short-lived seek metadata, not source
keys, tokens or event/payload data; restart, replica change or expiry yields 400.
The snapshot freezes an ingestion watermark, **not** a materialized source snapshot:
retention deletion/source mutations can still remove rows. This must be included
in the watermark/retention review. Socket/server deadlines and future cancellation
bound the request and interrupt supported work; Java SDK authentication/HTTP may
finish independently if it ignores interruption, with the bounded executor still
preventing unbounded work. Client disconnect cancellation is platform-dependent
and cannot guarantee immediate ADX cancellation.

| HTTP | Meaning |
| --- | --- |
| `400` | Invalid body/query/UTC interval or invalid, tampered, misbound, expired cursor |
| `404` | Attempt absent/outside caller scope; already purged expiry rows also appear absent |
| `410` | Attempt expiry reached while its repository row is still available |
| `429` | Rate/concurrency/cursor admission or source throttling |
| `502` | ADX/query/schema/ordering/correlation error; sanitized, never raw SDK detail |
| `503` | Disabled or missing/unverified deployment configuration |
| `504` | Query deadline/socket timeout or interrupted consultation |

Synthetic validation (no credentials/connections to ADX):
`./mvnw -q -Dtest=RePayloadTest,ReDiagnosticsTest,ReKustoSourceTest,ReTelemetryPrivacyTest,ReRequestLoggingTest,ReDisabledResourceTest,ReEventsResourceTest test`.
REST tests use isolated H2 projection data and a mocked SDK boundary; SDK tests use
synthetic result tables, including nanoseconds/scalars, cancellation and failures.

## Develop Locally 💻

### Prerequisites
- git
- maven
- jdk-11

### Setup
With skdman,in terminal:

`sdk install java 17.0.7-graal`

`sdk use java 17.0.7-graal`

or download **java 17.0.7-graal** and set as java home

### Build & Run (docker)
Build the image with

`./build-and-run.sh build`

Needed env variables:
```
COSMOS_BIZ_ENDPOINT
COSMOS_BIZ_KEY
COSMOS_NEG_BIZ_ENDPOINT
COSMOS_NEG_BIZ_KEY
RE_TABLE_STORAGE_CONN_STRING
RE_COSMOS_CONNECTION_STRING
ONLINE_NDP001_DATASOURCE_URL
ONLINE_NDP001_DATASOURCE_USERNAME
ONLINE_NDP001_DATASOURCE_PASSWORD
ONLINE_NDP004_DATASOURCE_URL
ONLINE_NDP004_DATASOURCE_USERNAME
ONLINE_NDP004_DATASOURCE_PASSWORD
```

`serviceIdentifier=ndp001` selects the `online-ndp001` datasource, configured
with `ONLINE_NDP001_DATASOURCE_*`; `serviceIdentifier=ndp004prod` selects
`online-ndp004`, configured with `ONLINE_NDP004_DATASOURCE_*`. Matching
trims whitespace and ignores case. Both datasource accounts must be read-only
database roles with `SELECT` access to the `online` schema. The worker enables
`default_transaction_read_only` for each connection and does not run Hibernate
or Liquibase migrations against the source database. Supply all six variables
in the runtime environment before deploying.
Run application with

`./build-and-run.sh run`

### Generate openapi file
Generate openapi.json file in **./openapi/openapi.json**

`./build-and-run.sh generate_openapi`

### Run the project

Run in development mode with command
`quarkus dev`

### Quarkus Profiles

`dev` active in development

`test` active in tests

`openapi` active only for openapi generation

`prod` default for run

### Testing 🧪

#### Unit testing

To run the **Junit** tests:

`mvn clean verify`

The startup validation check uses Quarkus's Failsafe test extension and is
skipped by the default `skipITs` setting. Run it explicitly with:

`./mvnw -q -Dtest=NodeSnapshotRouterTest -DskipITs=false -Dit.test=NodeSnapshotRouterStartupIT,NodeSnapshotRouterMissingNdp001MappingStartupIT,NodeSnapshotRouterMissingNdp004MappingStartupIT verify`

---

## Contributors 👥
Made with ❤️ by PagoPa S.p.A.

### Mainteiners
See `CODEOWNERS` file
