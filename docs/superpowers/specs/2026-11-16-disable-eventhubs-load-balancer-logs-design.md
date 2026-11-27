# Disable Event Hubs load balancer logs

## Goal

Suppress the recurring informational messages emitted by
`com.azure.messaging.eventhubs.PartitionBasedLoadBalancer`, without reducing
other application or Azure SDK logging.

## Design

Set the exact Quarkus logger category for
`com.azure.messaging.eventhubs.PartitionBasedLoadBalancer` to `OFF` in the
shared `src/main/resources/application.properties`. This targets the logger
shown in the reported log entries across application profiles and does not
change the root log level or other Event Hubs categories.

The alternatives are suppressing the entire Event Hubs package, which would
hide unrelated diagnostics, or adding appender-level message filtering, which
is more complex and brittle.

## Verification

Verify the configuration property is present and run the project's focused
configuration/build validation, if available. No application behavior or
logging outside this category should change.
