package it.gov.pagopa.nodetsworker.services;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import it.gov.pagopa.nodetsworker.entities.PositionPayment;
import it.gov.pagopa.nodetsworker.entities.PositionService;
import it.gov.pagopa.nodetsworker.entities.RPT;
import it.gov.pagopa.nodetsworker.exceptions.AppError;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.TransactionResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.Transaction;

import javax.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkerService {

    @ConfigProperty(name = "node.identifier", defaultValue = "")
    String nodeId;

    public List<TransactionResponse> getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, Date dateFrom, Date dateTo) {
        // if payments > 0 it is a new model payment
        List<PositionPayment> payments = PositionPayment.find("organizationFiscalCode = :organizationFiscalCode and noticeNumber = :noticeNumber",
                Parameters.with("organizationFiscalCode", organizationFiscalCode).and("noticeNumber", noticeNumber).map()
        ).list();
        boolean newModelPayment = payments.size() > 0;

        List<RPT> rptList = new ArrayList<>();
        if (payments.isEmpty()) {
            rptList = RPT.find("organizationFiscalCode = :organizationFiscalCode and iuv = :iuv",
                    Parameters.with("organizationFiscalCode", organizationFiscalCode).and("iuv", noticeNumber.substring(3)).map()
            ).list();

            rptList.stream().forEach(rpt -> {
                payments.addAll(PositionPayment.find("organizationFiscalCode = :organizationFiscalCode and rptId = :rptId",
                        Parameters.with("organizationFiscalCode", organizationFiscalCode).and("rptId", rpt.getId()).map()
                ).list());
            });
        }

        Optional<PositionService> optPositionService = PositionService.find("organizationFiscalCode = :organizationFiscalCode and noticeNumber = :noticeNumber",
                Parameters.with("organizationFiscalCode", organizationFiscalCode).and("noticeNumber", noticeNumber).map()
        ).firstResultOptional();

        if (optPositionService.isEmpty()) {
            throw new AppException(AppError.POSITION_SERVICE_NOT_FOUND, organizationFiscalCode, noticeNumber);
        }
        PositionService positionService = optPositionService.get();
        List<TransactionResponse> transactionResponseList = payments.stream().map(payment -> {
            TransactionResponse transactionResponse = TransactionResponse.builder()
                    .organizationFiscalCode(positionService.getOrganizationFiscalCode())
                    .organizationName(positionService.getOrganizationName())
                    .noticeNumber(positionService.getNoticeNumber())
                    .creditorReferenceId(payment.getCreditorReferenceId())
                    .paymentToken(payment.getPaymentToken())
                    .pspId(payment.getPspId())
                    .brokerPspId(payment.getBrokerPspId())
                    .channelId(payment.getChannelId())
                    .outcome(payment.getOutcome())
                    .transferDate(payment.getTransferDate())
                    .payerId(payment.getPayerId())
                    .applicationDate(payment.getApplicationDate())
                    .insertedTimestamp(payment.getInsertedTimestamp())
                    .updatedTimestamp(payment.getUpdatedTimestamp())
                    .nodeId(nodeId)
                    .build();
            if (!newModelPayment) {
                // TODO
                transactionResponse.setIsOldModelPayment(true);
                transactionResponse.setCcp(null);
                transactionResponse.setBic(null);
                transactionResponse.setPaymentRequestTimestamp(null);
                transactionResponse.setRevokeRequest(null);
            }
            return transactionResponse;
        }).collect(Collectors.toList());

        return transactionResponseList;
    }
}
