package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.PositionPaymentSnapshotDto;
import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PositionPaymentSnapshotService {

    @Inject
    NodeSnapshotRouter router;

    public PositionPaymentSnapshotDto getByPaymentToken(String paymentToken, String serviceIdentifier) {

        PositionPaymentSnapshotReader reader = router.resolve(serviceIdentifier);

        PositionPaymentSSEntity e = reader.findByPaymentToken(paymentToken)
                .orElseThrow(() -> new AppException(AppErrorCodeMessageEnum.NOT_FOUND,
                        "No snapshot row found for paymentToken %s", paymentToken));

        return PositionPaymentSnapshotDto.builder()
                .id(e.getId())
                .paFiscalCode(e.getPaFiscalCode())
                .noticeId(e.getNoticeId())
                .creditorReferenceId(e.getCreditorReferenceId())
                .paymentToken(e.getPaymentToken())
                .status(e.getStatus())
                .insertedTimestamp(e.getInsertedTimestamp())
                .updatedTimestamp(e.getUpdatedTimestamp())
                .fkPositionPayment(e.getFkPositionPayment())
                .insertedBy(e.getInsertedBy())
                .updatedBy(e.getUpdatedBY())
                .build();
    }
}