--liquibase formatted sql

-- ======================================================================================
-- Windowed projection reads (overview, creditor detail): the payments with activity in [from, to) are
-- selected in the database from the dated transitions and the attempt starts, instead of loading the
-- whole retention in memory.
-- ======================================================================================

-- changeset pagopa:0009-01-payment-transition-at
CREATE INDEX IF NOT EXISTS idx_payment_transition_at ON observability.payment_transition (transition_at);
-- rollback DROP INDEX IF EXISTS observability.idx_payment_transition_at;

-- changeset pagopa:0009-02-attempt-transition-at
CREATE INDEX IF NOT EXISTS idx_attempt_transition_at ON observability.attempt_transition (transition_at);
-- rollback DROP INDEX IF EXISTS observability.idx_attempt_transition_at;

-- changeset pagopa:0009-03-recipient-transition-at
CREATE INDEX IF NOT EXISTS idx_recipient_transition_at ON observability.recipient_transition (transition_at);
-- rollback DROP INDEX IF EXISTS observability.idx_recipient_transition_at;

-- changeset pagopa:0009-04-attempt-started-at
CREATE INDEX IF NOT EXISTS idx_attempt_started_at ON observability.attempt (attempt_started_at);
-- rollback DROP INDEX IF EXISTS observability.idx_attempt_started_at;
