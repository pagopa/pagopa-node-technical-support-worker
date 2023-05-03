package it.gov.pagopa.nodetsworker.services;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Parameters;
import it.gov.pagopa.nodetsworker.entities.PositionPayment;
import it.gov.pagopa.nodetsworker.entities.PositionPaymentStatusSnapshot;
import it.gov.pagopa.nodetsworker.entities.PositionService;
import it.gov.pagopa.nodetsworker.entities.RPT;
import it.gov.pagopa.nodetsworker.entities.RT;
import it.gov.pagopa.nodetsworker.entities.StatiRPTSnapshot;
import it.gov.pagopa.nodetsworker.exceptions.AppError;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.models.TransactionResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkerService {

    @ConfigProperty(name = "node.identifier", defaultValue = "")
    String nodeId;

    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {
        verifyDate(dateFrom, dateTo);

        // if payments > 0 it is a new payment model
        List<PositionPayment> payments = getPaymentPositionListByNoticeNumber(organizationFiscalCode, noticeNumber, dateFrom, dateTo);

        List<PaymentInfo> paymentInfoList;
        if (!payments.isEmpty()) {
            paymentInfoList = enrichPaymentPositionList(payments);
        }
        else {
            int beginIndex = noticeNumber.startsWith("0") || noticeNumber.startsWith("3") ? 3 : 1;
            List<RPT> rptList = getRPTList(organizationFiscalCode, noticeNumber.substring(beginIndex));
            paymentInfoList = enrichRPTList(rptList);
        }

        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .paymentInfoList(paymentInfoList)
                .build();
    }

    public TransactionResponse getInfoByIUV(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {
        verifyDate(dateFrom, dateTo);

        // if payments > 0 it is a new model payment
        List<PositionPayment> payments = getPaymentPositionListByIUV(organizationFiscalCode, iuv, dateFrom, dateTo);

        List<PaymentInfo> paymentInfoList;
        if (!payments.isEmpty()) {
            paymentInfoList = enrichPaymentPositionList(payments);
        }
        else {
            List<RPT> rptList = getRPTList(organizationFiscalCode, iuv);
            paymentInfoList = enrichRPTList(rptList);
        }

        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .paymentInfoList(paymentInfoList)
                .build();
    }

    /**
     * Check dates validity
     * @param dateFrom
     * @param dateTo
     */
    private void verifyDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo != null || dateFrom != null && dateTo == null) {
            throw new AppException(AppError.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from and date to must be both defined");
        }
        else if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new AppException(AppError.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from must be before date to");
        }
    }


    /**
     * Get payment position list by notice number
     * @param organizationFiscalCode
     * @param noticeNumber
     * @param dateFrom
     * @param dateTo
     * @return payment position list
     */
    private List<PositionPayment> getPaymentPositionListByNoticeNumber(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {
        Parameters parameters = Parameters.with("organizationFiscalCode", organizationFiscalCode).and("noticeNumber", noticeNumber);
        String query = "organizationFiscalCode = :organizationFiscalCode and noticeNumber = :noticeNumber";

        return getPaymentPositionList(query, parameters, dateFrom, dateTo);
    }

    /**
     * Get payment position list by iuv
     * @param organizationFiscalCode
     * @param iuv
     * @param dateFrom
     * @param dateTo
     * @return
     */
    private List<PositionPayment> getPaymentPositionListByIUV(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {
        Parameters parameters = Parameters.with("organizationFiscalCode", organizationFiscalCode).and("iuv", "%" + iuv);
        String query = "organizationFiscalCode = :organizationFiscalCode and noticeNumber like :iuv";

        return getPaymentPositionList(query, parameters, dateFrom, dateTo);
    }

    private List<PositionPayment> getPaymentPositionList(String query, Parameters parameters, LocalDate dateFrom, LocalDate dateTo) {
        List<PositionPayment> payments;
        if (dateFrom != null && dateTo != null) {
            parameters.and("dateFrom", dateFrom.atStartOfDay()).and("dateTo", dateTo.atTime(23, 59, 59));
            query += " and insertedTimestamp >= :dateFrom and insertedTimestamp <= :dateTo";
        }

        payments = PositionPayment.find(query, parameters.map()).list();

        return payments;
    }

    /**
     * Add status to payment
     * @param payments
     * @return payment info list
     */
    private List<PaymentInfo> enrichPaymentPositionList(List<PositionPayment> payments) {
        return payments.stream().map(payment -> {
            Optional<PositionPaymentStatusSnapshot> optPaymentStatus = PositionPaymentStatusSnapshot.find("fkPositionPayment = :positionPaymentId", Parameters.with("positionPaymentId", payment.getId()).map()).singleResultOptional();
            String status = optPaymentStatus.map(PositionPaymentStatusSnapshot::getStatus).orElse(null);

            PaymentInfo paymentInfo = PaymentInfo.builder()
                    .organizationFiscalCode(payment.getOrganizationFiscalCode())
                    .noticeNumber(payment.getNoticeNumber())
                    .paymentToken(payment.getPaymentToken())
                    .pspId(payment.getPspId())
                    .brokerPspId(payment.getBrokerPspId())
                    .channelId(payment.getChannelId())
                    .outcome(payment.getOutcome())
                    .status(status)
                    .insertedTimestamp(payment.getInsertedTimestamp())
                    .updatedTimestamp(payment.getUpdatedTimestamp())
                    .isOldModelPayment(false)
                    .build();

            optPaymentStatus.ifPresent(positionPaymentStatusSnapshot -> paymentInfo.setStatus(positionPaymentStatusSnapshot.getStatus()));

            return paymentInfo;
        }).collect(Collectors.toList());
    }

    /**
     * Add status to rpt
     * @param rptList
     * @return
     */
    private List<PaymentInfo> enrichRPTList(List<RPT> rptList) {

        return rptList.stream().map(rpt -> {

            Optional<RT> optRT = StatiRPTSnapshot.find("organizationFiscalCode = :organizationFiscalCode and iuv = :iuv and ccp = :ccp",
                    Parameters.with("organizationFiscalCode", rpt.getOrganizationFiscalCode())
                            .and("iuv", rpt.getIuv())
                            .and("ccp", rpt.getCcp())
                            .map()
            ).singleResultOptional();
            String outcome = null;
            if (optRT.isPresent()) {
                RT rt = optRT.get();
                if (rt.getOutcome().equals("ESEGUITO")) {
                    outcome = "OK";
                }
                else if (rt.getOutcome().equals("NON_ESEGUITO")) {
                    outcome = "KO";
                }
            }

            Optional<StatiRPTSnapshot> optRPTStatus = StatiRPTSnapshot.find("id.organizationFiscalCode = :organizationFiscalCode and id.iuv = :iuv and id.ccp = :ccp",
                    Parameters.with("organizationFiscalCode", rpt.getOrganizationFiscalCode())
                            .and("iuv", rpt.getIuv())
                            .and("ccp", rpt.getCcp())
                            .map()
            ).singleResultOptional();
            String status = optRPTStatus.map(StatiRPTSnapshot::getStatus).orElse(null);

            return PaymentInfo.builder()
                    .organizationFiscalCode(rpt.getOrganizationFiscalCode())
                    .noticeNumber(rpt.getIuv())
                    .paymentToken(rpt.getCcp())
                    .pspId(rpt.getPspId())
                    .brokerPspId(rpt.getBrokerPspId())
                    .channelId(rpt.getChannelId())
                    .outcome(outcome)
                    .status(status)
                    .insertedTimestamp(rpt.getInsertedTimestamp())
                    .updatedTimestamp(rpt.getUpdatedTimestamp())
                    .isOldModelPayment(false)
                    .build();
        }).collect(Collectors.toList());
    }


    /**
     * Retrieve RPT list by organizationFiscalCode e IUV
     * @param organizationFiscalCode
     * @param iuv
     * @return rpt list
     */
    private List<RPT> getRPTList(String organizationFiscalCode, String iuv) {
        return RPT.find("organizationFiscalCode = :organizationFiscalCode and iuv = :iuv",
                Parameters.with("organizationFiscalCode", organizationFiscalCode).and("iuv", iuv).map()
        ).list();
    }

}
