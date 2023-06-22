package it.gov.pagopa.nodetsworker.services;

import io.quarkus.panache.common.Parameters;
import it.gov.pagopa.nodetsworker.entities.PositionPayment;
import it.gov.pagopa.nodetsworker.entities.PositionPaymentStatusSnapshot;
import it.gov.pagopa.nodetsworker.entities.RPT;
import it.gov.pagopa.nodetsworker.entities.RT;
import it.gov.pagopa.nodetsworker.entities.StatiRPTSnapshot;
import it.gov.pagopa.nodetsworker.exceptions.AppError;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.PaymentAttemptInfo;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.models.RPTAttemptInfo;
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

    String DATE_QUERY = " and insertedTimestamp >= :dateFrom and insertedTimestamp <= :dateTo";

    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

        // if payments > 0 it is a new payment model
        List<PositionPayment> payments = getPaymentPositionListByNoticeNumber(organizationFiscalCode, noticeNumber, dateRequest.getFrom(), dateRequest.getTo());

        List<PaymentInfo> paymentInfoList;
        if (!payments.isEmpty()) {
            paymentInfoList = enrichPaymentPositionList(payments, dateRequest.getFrom(), dateRequest.getTo());
        }
        else {
            int beginIndex = noticeNumber.startsWith("0") ? 3 : 1;
            List<RPT> rptList = getRPTList(organizationFiscalCode, noticeNumber.substring(beginIndex), dateRequest.getFrom(), dateRequest.getTo());
            paymentInfoList = enrichRPTList(rptList, dateRequest.getFrom(), dateRequest.getTo());
        }

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(paymentInfoList)
                .build();
    }

    public TransactionResponse getInfoByIUV(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

        List<PositionPayment> payments = getPaymentPositionListByIUV(organizationFiscalCode, iuv, dateRequest.getFrom(), dateRequest.getTo());

        // if payments > 0 it is a new model payment
        List<PaymentInfo> paymentInfoList;
        if (!payments.isEmpty()) {
            paymentInfoList = enrichPaymentPositionList(payments, dateRequest.getFrom(), dateRequest.getTo());
        }
        else {
            List<RPT> rptList = getRPTList(organizationFiscalCode, iuv, dateRequest.getFrom(), dateRequest.getTo());
            paymentInfoList = enrichRPTList(rptList, dateRequest.getFrom(), dateRequest.getTo());
        }

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(paymentInfoList)
                .build();
    }

    public TransactionResponse getAttemptByIUVPaymentToken(String organizationFiscalCode, String iuv, String paymentToken, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

        List<PositionPayment> payments = getPaymentPositionListByIUVPaymentToken(organizationFiscalCode, iuv, paymentToken, dateRequest.getFrom(), dateRequest.getTo());
        List<PaymentAttemptInfo> paymentAttemptInfoList = enrichPaymentAttemptPositionList(payments, dateRequest.getFrom(), dateRequest.getTo());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(paymentAttemptInfoList)
                .build();
    }
    public TransactionResponse getAttemptByIUVCCP(String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

        List<RPT> rptList = getRPTListByIUVCCP(organizationFiscalCode, iuv, ccp, dateRequest.getFrom(), dateRequest.getTo());
        List<RPTAttemptInfo> rptAttemptInfoList = enrichRPTAttemptList(rptList, dateRequest.getFrom(), dateRequest.getTo());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(rptAttemptInfoList)
                .build();
    }






    /**
     * Check dates validity
     * @param dateFrom
     * @param dateTo
     */
    private DateRequest verifyDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo != null || dateFrom != null && dateTo == null) {
            throw new AppException(AppError.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from and date to must be both defined");
        }
        else if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new AppException(AppError.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from must be before date to");
        }
        if (dateFrom == null && dateTo == null) {
            dateFrom = LocalDate.now();
            dateTo = dateFrom.minusDays(10);
        }
        return DateRequest.builder()
                .from(dateFrom)
                .to(dateTo)
                .build();
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

    /**
     * Get payment position list by iuv and payment token
     * @param organizationFiscalCode
     * @param iuv
     * @param dateFrom
     * @param dateTo
     * @return
     */
    private List<PositionPayment> getPaymentPositionListByIUVPaymentToken(String organizationFiscalCode, String iuv, String paymentToken, LocalDate dateFrom, LocalDate dateTo) {
        String query = "organizationFiscalCode = :organizationFiscalCode and noticeNumber like :iuv and paymentToken = :paymentToken";
        Parameters parameters = Parameters
                .with("organizationFiscalCode", organizationFiscalCode)
                .and("iuv", "%" + iuv)
                .and("paymentToken", paymentToken);

        return getPaymentPositionList(query, parameters, dateFrom, dateTo);
    }



    private List<PositionPayment> getPaymentPositionList(String query, Parameters parameters, LocalDate dateFrom, LocalDate dateTo) {
        query += DATE_QUERY;
        parameters.and("dateFrom", dateFrom.atStartOfDay()).and("dateTo", dateTo.atTime(23, 59, 59));

        return PositionPayment.find(query, parameters.map()).list();
    }

    /**
     * Add status to payment
     * @param payments
     * @return payment info list
     */
    private List<PaymentInfo> enrichPaymentPositionList(List<PositionPayment> payments, LocalDate dateFrom, LocalDate dateTo) {
        List<PaymentInfo> paymentInfoList = payments.stream().map(payment -> {
            Optional<PositionPaymentStatusSnapshot> optPaymentStatus =
                    PositionPaymentStatusSnapshot.find("fkPositionPayment = :positionPaymentId" + DATE_QUERY,
                                    Parameters.with("positionPaymentId", payment.getId())
                                            .and("dateFrom", dateFrom.atStartOfDay())
                                            .and("dateTo", dateTo.atTime(23, 59, 59))
                                            .map()
                            )
                            .singleResultOptional();
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
                    .isOldPaymentModel(false)
                    .nodeId(nodeId)
                    .build();

            optPaymentStatus.ifPresent(positionPaymentStatusSnapshot -> paymentInfo.setStatus(positionPaymentStatusSnapshot.getStatus()));

            return paymentInfo;
        }).collect(Collectors.toList());

        paymentInfoList.sort(Comparator.comparing(PaymentInfo::getUpdatedTimestamp).reversed());
        return paymentInfoList;
    }

    /**
     * Add status to payment
     * @param payments
     * @return payment attempt info list
     */
    private List<PaymentAttemptInfo> enrichPaymentAttemptPositionList(List<PositionPayment> payments, LocalDate dateFrom, LocalDate dateTo) {
        List<PaymentAttemptInfo> paymentAttemptInfoList = payments.stream().map(payment -> {
            Optional<PositionPaymentStatusSnapshot> optPaymentStatus =
                    PositionPaymentStatusSnapshot.find("fkPositionPayment = :positionPaymentId" + DATE_QUERY,
                                    Parameters.with("positionPaymentId", payment.getId())
                                            .and("dateFrom", dateFrom.atStartOfDay())
                                            .and("dateTo", dateTo.atTime(23, 59, 59))
                                            .map()
                            )
                            .singleResultOptional();
            String status = optPaymentStatus.map(PositionPaymentStatusSnapshot::getStatus).orElse(null);

            PaymentAttemptInfo paymentAttemptInfo = PaymentAttemptInfo.builder()
                    .organizationFiscalCode(payment.getOrganizationFiscalCode())
                    .noticeNumber(payment.getNoticeNumber())
                    .pspId(payment.getPspId())
                    .brokerPspId(payment.getBrokerPspId())
                    .channelId(payment.getChannelId())
                    .brokerOrganizationId(payment.getBrokerOrganizationId())
                    .stationId(payment.getStationId())
                    .paymentMethod(payment.getPaymentMethod())
                    .flagIO(payment.getFlagIO())
                    .outcome(payment.getOutcome())
                    .status(status)
                    .paymentToken(payment.getPaymentToken())
                    .pmReceipt(payment.getPmReceipt())
                    .paymentMethod(payment.getPaymentMethod())
                    .flagPayPal(payment.getFlagPayPal())
                    .stationVersion(payment.getStationVersion())
                    .amount(payment.getAmount())
                    .fee(payment.getFee())
                    .feeSpo(payment.getFeeSpo())
                    .feeOrganization(payment.getFeeOrganization())
                    .bundleId(payment.getBundleId())
                    .bundleOrganizationId(payment.getBundleOrganizationId())
                    .applicationDate(payment.getApplicationDate())
                    .transferDate(payment.getTransferDate())
                    .insertedTimestamp(payment.getInsertedTimestamp())
                    .updatedTimestamp(payment.getUpdatedTimestamp())
                    .isOldPaymentModel(false)
                    .nodeId(nodeId)
                    .build();

            optPaymentStatus.ifPresent(positionPaymentStatusSnapshot -> paymentAttemptInfo.setStatus(positionPaymentStatusSnapshot.getStatus()));

            return paymentAttemptInfo;
        }).collect(Collectors.toList());

        paymentAttemptInfoList.sort(Comparator.comparing(PaymentAttemptInfo::getUpdatedTimestamp).reversed());
        return paymentAttemptInfoList;
    }

    /**
     * Add status to rpt
     * @param rptList
     * @return
     */
    private List<PaymentInfo> enrichRPTList(List<RPT> rptList, LocalDate dateFrom, LocalDate dateTo) {
        List<PaymentInfo> paymentInfoList = rptList.stream().map(rpt -> {

            Map<String, String> rptResponse = retrieveRPTResponseInfo(rpt, dateFrom, dateTo);

            return PaymentInfo.builder()
                    .organizationFiscalCode(rpt.getOrganizationFiscalCode())
                    .noticeNumber(rpt.getIuv())
                    .paymentToken(rpt.getCcp())
                    .pspId(rpt.getPspId())
                    .brokerPspId(rpt.getBrokerPspId())
                    .channelId(rpt.getChannelId())
                    .outcome(rptResponse.get("outcome"))
                    .status(rptResponse.get("status"))
                    .insertedTimestamp(rpt.getInsertedTimestamp())
                    .updatedTimestamp(rpt.getUpdatedTimestamp())
                    .isOldPaymentModel(true)
                    .nodeId(nodeId)
                    .build();
        }).collect(Collectors.toList());

        paymentInfoList.sort(Comparator.comparing(PaymentInfo::getUpdatedTimestamp).reversed());
        return paymentInfoList;
    }

    /**
     * Add status to rpt
     * @param rptList
     * @return
     */
    private List<RPTAttemptInfo> enrichRPTAttemptList(List<RPT> rptList, LocalDate dateFrom, LocalDate dateTo) {
        List<RPTAttemptInfo> rptAttemptInfoList = rptList.stream().map(rpt -> {

            Map<String, String> rptResponse = retrieveRPTResponseInfo(rpt, dateFrom, dateTo);

            return RPTAttemptInfo.builder()
                    .organizationFiscalCode(rpt.getOrganizationFiscalCode())
                    .noticeNumber(rpt.getIuv())
                    .pspId(rpt.getPspId())
                    .brokerPspId(rpt.getBrokerPspId())
                    .channelId(rpt.getChannelId())
                    .brokerOrganizationId(rpt.getBrokerPspId())
                    .stationId(rpt.getStationId())
                    .paymentMethod(rpt.getPaymentMethod())
                    .amount(rpt.getAmount())
                    .flagIO(rpt.getFlagIO())
                    .outcome(rptResponse.get("outcome"))
                    .status(rptResponse.get("status"))
                    .insertedTimestamp(rpt.getInsertedTimestamp())
                    .updatedTimestamp(rpt.getUpdatedTimestamp())
                    .nodeId(nodeId)
                    .ccp(rpt.getCcp())
                    .numberOfPayments(rpt.getNumberOfPayments())
                    .retriedRPT(rpt.getRetriedRPT())
                    .wispInitialization(rpt.getWispInitialization())
                    .pmReceipt(rpt.getPmReceipt())
                    .build();
        }).collect(Collectors.toList());

        rptAttemptInfoList.sort(Comparator.comparing(RPTAttemptInfo::getUpdatedTimestamp).reversed());
        return rptAttemptInfoList;
    }

    private Map<String, String> retrieveRPTResponseInfo(RPT rpt, LocalDate dateFrom, LocalDate dateTo) {
        Map<String, String> response = new HashMap<>();
        Optional<RT> optRT = RT.find("organizationFiscalCode = :organizationFiscalCode and iuv = :iuv and ccp = :ccp" + DATE_QUERY,
                Parameters.with("organizationFiscalCode", rpt.getOrganizationFiscalCode())
                        .and("iuv", rpt.getIuv())
                        .and("ccp", rpt.getCcp())
                        .and("dateFrom", dateFrom.atStartOfDay())
                        .and("dateTo", dateTo.atTime(23, 59, 59))
                        .map()
        ).singleResultOptional();
        String outcome = null;
        if (optRT.isPresent()) {
            RT rt = optRT.get();
            if (rt.getOutcome().equals("ESEGUITO")) {
                outcome = "OK";
            } else if (rt.getOutcome().equals("NON_ESEGUITO")) {
                outcome = "KO";
            }
        }

        Optional<StatiRPTSnapshot> optRPTStatus =
                StatiRPTSnapshot.find("id.organizationFiscalCode = :organizationFiscalCode and id.iuv = :iuv and id.ccp = :ccp" + DATE_QUERY,
                        Parameters.with("organizationFiscalCode", rpt.getOrganizationFiscalCode())
                                .and("iuv", rpt.getIuv())
                                .and("ccp", rpt.getCcp())
                                .and("dateFrom", dateFrom.atStartOfDay())
                                .and("dateTo", dateTo.atTime(23, 59, 59))
                                .map()
                ).singleResultOptional();
        String status = optRPTStatus.map(StatiRPTSnapshot::getStatus).orElse(null);

        response.put("outcome", outcome);
        response.put("status", status);
        return response;
    }

    /**
     * Retrieve RPT list by organizationFiscalCode e IUV
     *
     * @param organizationFiscalCode
     * @param iuv
     * @param dateFrom
     * @param dateTo
     * @return rpt list
     */
    private List<RPT> getRPTList(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {
        return RPT.find("organizationFiscalCode = :organizationFiscalCode and iuv = :iuv" + DATE_QUERY,
                Parameters.with("organizationFiscalCode", organizationFiscalCode)
                        .and("iuv", iuv)
                        .and("dateFrom", dateFrom.atStartOfDay())
                        .and("dateTo", dateTo.atTime(23, 59, 59))
                        .map()
        ).list();
    }

    /**
     * Get rpt list by iuv and ccp
     * @param organizationFiscalCode
     * @param iuv
     * @param ccp
     * @param dateFrom
     * @param dateTo
     * @return rpt list
     */
    private List<RPT> getRPTListByIUVCCP(String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
        String query = "organizationFiscalCode = :organizationFiscalCode and iuv = :iuv and ccp = :ccp";

        return RPT.find(query + DATE_QUERY,
                Parameters.with("organizationFiscalCode", organizationFiscalCode)
                        .and("iuv", iuv)
                        .and("ccp", ccp)
                        .and("dateFrom", dateFrom.atStartOfDay())
                        .and("dateTo", dateTo.atTime(23, 59, 59))
                        .map()
        ).list();
    }

}
