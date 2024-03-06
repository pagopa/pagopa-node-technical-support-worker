package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.repository.model.PositionPaymentStatusSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;

@ApplicationScoped
@Transactional
public class SnapshotService {

    @ConfigProperty(name = "date-range-limit")
    Integer dateRangeLimit;

    public void getPosPaymentStatusSnapshot(String paFiscalCode, String noticeNumber,
                                            String paymentToken, LocalDate dateFrom, LocalDate dateTo,
                                            long pageNumber, long pageSize) {
        PositionPaymentStatusSnapshot.count("paFiscalCode = ?1", paFiscalCode);
    }

}
