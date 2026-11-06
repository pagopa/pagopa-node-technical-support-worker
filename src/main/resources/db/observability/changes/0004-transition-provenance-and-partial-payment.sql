--liquibase formatted sql

-- Allow an attempt timeline to arrive before its independent position timeline.
-- changeset pagopa:0004-01-partial-payment
ALTER TABLE observability.payment ALTER COLUMN current_state DROP NOT NULL;

-- Preserve the source-provided application trigger on both independent timelines.
-- changeset pagopa:0004-02-logical-trigger
ALTER TABLE observability.payment_transition ADD COLUMN IF NOT EXISTS application_trigger varchar(512);
-- rollback ALTER TABLE observability.payment_transition DROP COLUMN IF EXISTS application_trigger;

-- changeset pagopa:0004-03-attempt-trigger
ALTER TABLE observability.attempt_transition ADD COLUMN IF NOT EXISTS application_trigger varchar(512);
-- rollback ALTER TABLE observability.attempt_transition DROP COLUMN IF EXISTS application_trigger;

-- changeset pagopa:0004-04-event-hub-key
ALTER TABLE observability.ingestion_inbox ALTER COLUMN event_hub TYPE varchar(512);
-- rollback ALTER TABLE observability.ingestion_inbox ALTER COLUMN event_hub TYPE varchar(128);
