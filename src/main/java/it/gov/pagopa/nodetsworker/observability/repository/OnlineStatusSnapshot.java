package it.gov.pagopa.nodetsworker.observability.repository;

import java.time.LocalDateTime;

public record OnlineStatusSnapshot(
    String state,
    LocalDateTime insertedTimestamp,
    LocalDateTime updatedTimestamp) {
}
