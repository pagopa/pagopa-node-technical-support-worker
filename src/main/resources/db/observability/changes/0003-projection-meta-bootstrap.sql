--liquibase formatted sql

-- The projection writer locks this singleton before payment rows to serialize revision and
-- outbox sequence allocation, including concurrent creation of a previously absent payment.
-- changeset pagopa:0003-01-projection-meta-bootstrap
INSERT INTO observability.projection_meta (
    id,
    aggregate_revision,
    last_sequence,
    freshness_status,
    freshness_lag_seconds,
    freshness_gaps_detected,
    last_processed_at,
    last_published_at
)
VALUES (1, 1, 0, 'CURRENT', 0, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
-- rollback DELETE FROM observability.projection_meta WHERE id = 1 AND aggregate_revision = 1 AND last_sequence = 0;
