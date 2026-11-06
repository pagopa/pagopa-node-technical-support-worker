package it.gov.pagopa.nodetsworker.observability.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Upstream {@code /cache/version} payload of a single node. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CacheVersion(
    String dbStatus, String cacheId, String version, String environment, String serviceIdentifier) {
}
