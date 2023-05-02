package it.gov.pagopa.nodetsworker.services;

import io.quarkus.panache.common.Parameters;
import it.gov.pagopa.nodetsworker.entities.PositionPayment;
import it.gov.pagopa.nodetsworker.entities.PositionService;
import it.gov.pagopa.nodetsworker.entities.RPT;
import it.gov.pagopa.nodetsworker.exceptions.AppError;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
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

    public List<TransactionResponse> getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {
        verifyDate(dateFrom, dateTo);

        // if payments > 0 it is a new payment model
        List<PositionPayment> payments = getPaymentPositionListByNoticeNumber(organizationFiscalCode, noticeNumber, dateFrom, dateTo);
        boolean newPaymentModel = payments.size() > 0;

        Map<Long, RPT> rptMap;
        if (!newPaymentModel) {
            int beginIndex = noticeNumber.startsWith("0") || noticeNumber.startsWith("3") ? 3 : 1;
            List<RPT> rptList = getRPTList(organizationFiscalCode, noticeNumber.substring(beginIndex));

            Map<String, Object> data = getPaymentsByRPT(rptList, organizationFiscalCode);
            payments = (List<PositionPayment>) data.get("payments");
            rptMap = (Map<Long, RPT>) data.get("rpts");
        } else {
            rptMap = new HashMap<>();
        }

        PositionService positionService = getPositionService(organizationFiscalCode, noticeNumber);

        return getTransactionResponseList(payments, positionService, newPaymentModel, rptMap);
    }

    public List<TransactionResponse> getInfoByIUV(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {
        verifyDate(dateFrom, dateTo);

        // if payments > 0 it is a new model payment
        List<PositionPayment> payments = getPaymentPositionListByIUV(organizationFiscalCode, iuv, dateFrom, dateTo);
        boolean newPaymentModel = payments.size() > 0;

        Map<Long, RPT> rptMap = new HashMap<>();
        if (!newPaymentModel) {
            List<RPT> rptList = getRPTList(organizationFiscalCode, iuv);
            Map<String, Object> data = getPaymentsByRPT(rptList, organizationFiscalCode);

            payments = (List<PositionPayment>) data.get("payments");
            rptMap = (Map<Long, RPT>) data.get("rpts");
        }

        // TODO review
        String noticeNumber = payments.get(0).getNoticeNumber();
        PositionService positionService = getPositionService(organizationFiscalCode, noticeNumber);

        return getTransactionResponseList(payments, positionService, newPaymentModel, rptMap);
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


    /**
     * Get payment position list by RPT
     * @param rptList
     * @param organizationFiscalCode
     * @return Map containing payment list and RPT map
     */
    private Map<String, Object> getPaymentsByRPT(List<RPT> rptList, String organizationFiscalCode) {
        List<PositionPayment> payments = new ArrayList<>();
        Map<Long, RPT> rptMap = new HashMap<>();
        rptList.forEach(rpt -> {
            rptMap.put(rpt.getId(), rpt);

            payments.addAll(PositionPayment.find("organizationFiscalCode = :organizationFiscalCode and rptId = :rptId",
                    Parameters.with("organizationFiscalCode", organizationFiscalCode).and("rptId", rpt.getId()).map()
            ).list());
        });
        Map<String, Object> response = new HashMap<>();
        response.put("rpts", rptMap);
        response.put("payments", payments);
        return response;
    }

    /**
     * Get Position Service by organization code and notice number
     * @param organizationFiscalCode
     * @param noticeNumber
     * @return Position service
     */
    private PositionService getPositionService(String organizationFiscalCode, String noticeNumber) {
        Optional<PositionService> optPositionService = PositionService.find("organizationFiscalCode = :organizationFiscalCode and noticeNumber = :noticeNumber",
                Parameters.with("organizationFiscalCode", organizationFiscalCode).and("noticeNumber", noticeNumber).map()
        ).firstResultOptional();

        if (optPositionService.isEmpty()) {
            throw new AppException(AppError.POSITION_SERVICE_NOT_FOUND, organizationFiscalCode, noticeNumber);
        }

        return optPositionService.get();
    }

    private List<TransactionResponse> getTransactionResponseList(List<PositionPayment> payments, PositionService positionService, Boolean newPaymentModel, Map<Long, RPT> rptMap) {
        return payments.stream().map(payment -> {
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
            if (!newPaymentModel) {
                RPT rpt = rptMap.get(payment.getRptId());
                if (rpt != null) {
                    transactionResponse.setIsOldModelPayment(true);
                    transactionResponse.setCcp(rpt.getCcp());
                    transactionResponse.setBic(rpt.getBic());
                    transactionResponse.setPaymentRequestTimestamp(rpt.getPaymentRequestTimestamp());
                    transactionResponse.setRevokeRequest(rpt.getRevokeRequest());
                }
            }
            return transactionResponse;
        }).collect(Collectors.toList());
    }
}
