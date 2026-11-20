--liquibase formatted sql

-- ======================================================================================
-- Direction of a primitive call relative to the Nodo (spec §3.3.2, "Direzione"): INBOUND when the Nodo is
-- the RE erogatore, OUTBOUND when it is the fruitore, UNKNOWN otherwise. It becomes a dimension of the
-- per-minute counters. Rows counted before this change cannot be reclassified and stay UNKNOWN.
-- ======================================================================================

-- changeset pagopa:0008-01-primitive-call-direction
ALTER TABLE observability.primitive_call ADD COLUMN IF NOT EXISTS direction varchar(16) NOT NULL DEFAULT 'UNKNOWN';
-- rollback ALTER TABLE observability.primitive_call DROP COLUMN IF EXISTS direction;

-- changeset pagopa:0008-02-primitive-count-direction
ALTER TABLE observability.primitive_count ADD COLUMN IF NOT EXISTS direction varchar(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE observability.primitive_count DROP CONSTRAINT uk_primitive_count;
ALTER TABLE observability.primitive_count ADD CONSTRAINT uk_primitive_count
    UNIQUE (bucket_start, service_identifier, primitive, direction, psp, canale, stazione);
-- rollback ALTER TABLE observability.primitive_count DROP CONSTRAINT uk_primitive_count;
-- rollback ALTER TABLE observability.primitive_count ADD CONSTRAINT uk_primitive_count UNIQUE (bucket_start, service_identifier, primitive, psp, canale, stazione);
-- rollback ALTER TABLE observability.primitive_count DROP COLUMN IF EXISTS direction;
