# Online datasource enablement design

## Goal

Allow NDP001 and NDP004 Online JDBC datasources to be independently disabled so
Quarkus does not initialize their connections when they are not needed.

## Configuration and runtime behavior

- Add `ONLINE_NDP001_DATASOURCE_ENABLED` and
  `ONLINE_NDP004_DATASOURCE_ENABLED`, each defaulting to `false`.
- Map each flag to the corresponding named Quarkus datasource activation setting.
- Keep the two datasource lifecycles independent.
- Set both flags explicitly to `true` in the existing dev, UAT, and production
  Helm values, preserving the behavior of deployments that already configure
  these datasources.
- A request or synchronization that requires a disabled datasource must fail
  explicitly with a datasource-disabled error; it must not attempt to open a
  connection or report a successful empty result.
- When enabled, datasource routing, read-only behavior, and query semantics
  remain unchanged.

## Application integration

The named datasource injections in `NodeSnapshotRouter` and the NDP001
`OnlineStatusRepository` must tolerate datasource beans being unavailable when
their datasource is disabled. Guard access using the corresponding enablement
configuration, and only resolve/use the datasource on enabled paths. Preserve
the current configured service-identifier routing and ensure a disabled node
does not prevent application startup.

The disabled error must be consistent across source-reader and Check DB paths.
It should be distinguishable from an unknown service identifier and from a
database connectivity failure. The implementation should use the existing
error-response conventions and document the new response code where applicable.

## Documentation and tests

Update the README runtime configuration table with both flags and their
`false` defaults. Add explicit `true` values to the dev, UAT, and production
Helm configurations.

Add focused coverage for independent enablement, startup with a disabled
datasource and no datasource credentials/URL, and explicit rejection when a
disabled datasource is requested. Keep existing H2 test-profile datasource
behavior enabled where existing tests depend on it. Run the focused tests and
the relevant Maven verification command after implementation.

## Scope

This change applies only to the two Online source datasources. The offline
datasource and observability projection datasource, their initialization, and
their runtime behavior are unchanged.
