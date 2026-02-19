package it.gov.pagopa.nodetsworker.repository;

import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;

import java.util.Optional;

public interface PositionPaymentSnapshotReader {
    Optional<PositionPaymentSSEntity> findByPaymentToken(String paymentToken);
}