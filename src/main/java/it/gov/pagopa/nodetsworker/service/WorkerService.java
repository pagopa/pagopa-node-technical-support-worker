package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.models.*;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosVerifyKOEventClient;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.model.PositiveBizEvent;
import it.gov.pagopa.nodetsworker.repository.model.VerifyKOEvent;
import it.gov.pagopa.nodetsworker.resources.response.TransactionResponse;
import it.gov.pagopa.nodetsworker.util.ValidationUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.*;

import static it.gov.pagopa.nodetsworker.util.AppConstant.SERVICE_ID;
import static it.gov.pagopa.nodetsworker.util.AppConstant.STATUS_COMPLETED;

@ApplicationScoped
public class WorkerService {

    private static String outcomeOK = "OK";
    private static String outcomeKO = "KO";

    @Inject
    Logger log;
    @Inject
    CosmosBizEventClient positiveBizClient;
    @Inject
    CosmosNegBizEventClient negativeBizClient;
    @Inject
    CosmosVerifyKOEventClient verifyKOEventClient;

    @ConfigProperty(name = "date-range-limit")
    Integer dateRangeLimit;

    private List<String> tipiEventoAttempts = Arrays.asList("activatePaymentNotice","activatePaymentNoticeV2","nodoInviaRPT","nodoInviaCarrelloRPT");

    private PaymentInfo eventToPaymentInfo(VerifyKOEvent evt) {

        return PaymentInfo.builder()
                .businessProcess("VerifyPaymentNotice")
                .serviceIdentifier(evt.getServiceIdentifier())
                .pspId(evt.getPsp().getIdPsp())
                .positiveBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getFaultBean().getDateTime().toString())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .outcome(outcomeKO)
                .faultBean(FaultBean.builder().faultCode(evt.getFaultBean().getFaultCode()).description(evt.getFaultBean().getDescription()).timestamp(evt.getFaultBean().getDateTime()).build())
                .build();
    }

    private PaymentInfo eventToPaymentInfo(PositiveBizEvent evt) {
        return PaymentInfo.builder()
                .status(STATUS_COMPLETED)
                .serviceIdentifier(evt.getProperties()!=null?evt.getProperties().get(SERVICE_ID):"n/a")
                .pspId(evt.getPsp().getIdPsp())
                .positiveBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime().toString())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .outcome(outcomeOK)
                .build();
    }

    private PaymentAttemptInfo eventToPaymentAttemptInfo(PositiveBizEvent evt) {
        return PaymentAttemptInfo.builder()
                .status(STATUS_COMPLETED)
                .serviceIdentifier(evt.getProperties()!=null?evt.getProperties().get(SERVICE_ID):"n/a")
                .pspId(evt.getPsp().getIdPsp())
                .positiveBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime().toString())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .stationId(evt.getCreditor().getIdStation())
                .brokerOrganizationId(evt.getCreditor().getIdBrokerPA())
                .outcome(outcomeOK)
                .build();
    }

    private PaymentInfo eventToPaymentInfo(NegativeBizEvent evt) {
        return PaymentInfo.builder()
                .businessProcess(evt.getBusinessProcess())
                .serviceIdentifier(evt.getProperties()!=null?evt.getProperties().get(SERVICE_ID):"n/a")
                .pspId(evt.getPsp().getIdPsp())
                .negativeBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime().toString())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .outcome(evt.getReAwakable()!=null && evt.getReAwakable() ? outcomeKO : null)
                .build();
    }

    private PaymentAttemptInfo eventToPaymentAttemptInfo(NegativeBizEvent evt) {
        return PaymentAttemptInfo.builder()
                .businessProcess(evt.getBusinessProcess())
                .serviceIdentifier(evt.getProperties()!=null?evt.getProperties().get(SERVICE_ID):"n/a")
                .pspId(evt.getPsp().getIdPsp())
                .negativeBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime().toString())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .stationId(evt.getCreditor().getIdStation())
                .brokerOrganizationId(evt.getCreditor().getIdBrokerPA())
                .outcome(evt.getReAwakable()!=null && evt.getReAwakable() ? outcomeKO : null)
                .build();
    }

    private PaymentAttemptInfo enrichPaymentAttemptInfo(PaymentAttemptInfo pai, PositiveBizEvent pbe) {
        pai.setPaymentToken(pbe.getPaymentInfo().getPaymentToken());
        pai.setBrokerPspId(pbe.getPsp().getIdBrokerPsp());
        pai.setAmount(pbe.getPaymentInfo().getAmount());
        pai.setFee(pbe.getPaymentInfo().getFee());
        pai.setFeeOrganization(pbe.getPaymentInfo().getPrimaryCiIncurredFee());
        pai.setPaymentMethod(pbe.getPaymentInfo().getPaymentMethod());
        if ("CP".equals(pbe.getPaymentInfo().getPaymentMethod())) {
            pai.setPmReceipt(pbe.getTransactionDetails() != null ? "sent" : "notSent");
        }
        pai.setTouchPoint(pbe.getPaymentInfo().getTouchpoint());
        pai.setPositiveBizEvtId(pbe.getId());
        return pai;
    }

    private PaymentAttemptInfo enrichPaymentAttemptInfo(PaymentAttemptInfo pai, NegativeBizEvent nbe) {
        pai.setBrokerPspId(nbe.getPsp().getIdBrokerPsp());
        pai.setPaymentToken(nbe.getPaymentInfo().getPaymentToken());
        pai.setAmount(nbe.getPaymentInfo().getAmount());
        pai.setPaymentMethod(nbe.getPaymentInfo().getPaymentMethod());
        if ("CP".equals(nbe.getPaymentInfo().getPaymentMethod())) {
            pai.setPmReceipt(nbe.getTransactionDetails() != null ? "sent" : "notSent");
        }
        pai.setTouchPoint(nbe.getPaymentInfo().getTouchpoint());
        pai.setNegativeBizEvtId(nbe.getId());
        return pai;
    }

    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, Optional<String> paymentToken, LocalDate dateFrom, LocalDate dateTo) {

        DateRequest dateRequest = ValidationUtil.verifyDateRequest(dateFrom, dateTo, dateRangeLimit);

        List<VerifyKOEvent> verifyKOEvents = verifyKOEventClient
                .findEventsByCiAndNN(
                        organizationFiscalCode,
                        noticeNumber,
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<PositiveBizEvent> positiveEvents = positiveBizClient
                .findEventsByCiAndNNAndToken(
                        organizationFiscalCode,
                        noticeNumber,
                        paymentToken,
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<NegativeBizEvent> negativeEvents = negativeBizClient
                .findEventsByCiAndNNAndToken(
                        organizationFiscalCode,
                        noticeNumber,
                        paymentToken,
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<BasePaymentInfo> collect = new ArrayList<>();

        collect.addAll(verifyKOEvents.stream().map(d->eventToPaymentInfo(d)).toList());
        collect.addAll(positiveEvents.stream().map(d->eventToPaymentInfo(d)).toList());
        collect.addAll(negativeEvents.stream().map(d->eventToPaymentInfo(d)).toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .count(collect.size())
                .payments(collect.stream().sorted(Comparator.comparing(BasePaymentInfo::getInsertedTimestamp)).toList())
                .build();
    }


    public TransactionResponse getInfoByIUV(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {

        DateRequest dateRequest = ValidationUtil.verifyDateRequest(dateFrom, dateTo, dateRangeLimit);

        List<VerifyKOEvent> verifyKOEvents = verifyKOEventClient
                .findEventsByCiAndNN(
                        organizationFiscalCode,
                        noticeNumber,
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<PositiveBizEvent> positiveEvents = positiveBizClient
                .findEventsByCiAndIUVAndCCP(
                        organizationFiscalCode,
                        noticeNumber,
                        Optional.empty(),
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<NegativeBizEvent> negativeEvents = negativeBizClient
                .findEventsByCiAndIUVAndCCP(
                        organizationFiscalCode,
                        noticeNumber,
                        Optional.empty(),
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<BasePaymentInfo> collect = new ArrayList<>();

        collect.addAll(verifyKOEvents.stream().map(d->eventToPaymentInfo(d)).toList());
        collect.addAll(positiveEvents.stream().map(d->eventToPaymentInfo(d)).toList());
        collect.addAll(negativeEvents.stream().map(d->eventToPaymentInfo(d)).toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .count(collect.size())
                .payments(collect.stream().sorted(Comparator.comparing(BasePaymentInfo::getInsertedTimestamp)).toList())
                .build();
    }

    public TransactionResponse getAttemptByNoticeNumberAndPaymentToken(
            String organizationFiscalCode,
            String noticeNumber,
            String paymentToken,
            LocalDate dateFrom,
            LocalDate dateTo) {

        DateRequest dateRequest = ValidationUtil.verifyDateRequest(dateFrom, dateTo, dateRangeLimit);

        List<PositiveBizEvent> positiveEvents = positiveBizClient
                .findEventsByCiAndNNAndToken(
                        organizationFiscalCode,
                        noticeNumber,
                        Optional.of(paymentToken),
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<NegativeBizEvent> negativeEvents = negativeBizClient
                .findEventsByCiAndNNAndToken(
                        organizationFiscalCode,
                        noticeNumber,
                        Optional.of(paymentToken),
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<BasePaymentInfo> collect = new ArrayList<>();

        collect.addAll(positiveEvents.stream().map(d->enrichPaymentAttemptInfo(eventToPaymentAttemptInfo(d),d)).toList());
        collect.addAll(negativeEvents.stream().map(d->enrichPaymentAttemptInfo(eventToPaymentAttemptInfo(d),d)).toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .count(collect.size())
                .payments(collect)
                .build();
    }

    public TransactionResponse getAttemptByIUVAndCCP(
            String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {

        DateRequest dateRequest = ValidationUtil.verifyDateRequest(dateFrom, dateTo, dateRangeLimit);

        List<PositiveBizEvent> positiveEvents = positiveBizClient
                .findEventsByCiAndIUVAndCCP(
                        organizationFiscalCode,
                        iuv,
                        Optional.of(ccp),
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<NegativeBizEvent> negativeEvents = negativeBizClient
                .findEventsByCiAndIUVAndCCP(
                        organizationFiscalCode,
                        iuv,
                        Optional.of(ccp),
                        dateRequest.getFrom(),
                        dateRequest.getTo()).stream().toList();

        List<BasePaymentInfo> collect = new ArrayList<>();

        collect.addAll(positiveEvents.stream().map(d->enrichPaymentAttemptInfo(eventToPaymentAttemptInfo(d),d)).toList());
        collect.addAll(negativeEvents.stream().map(d->enrichPaymentAttemptInfo(eventToPaymentAttemptInfo(d),d)).toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .count(collect.size())
                .payments(collect)
                .build();
    }

//    private Pair<DateRequest, DateRequest> getHistoryDates(DateRequest dateRequest) {
//        LocalDate dateLimit = LocalDate.now().minusDays(reCosmosDayLimit);
//        LocalDate historyDateFrom = null;
//        LocalDate historyDateTo = null;
//        LocalDate actualDateFrom = null;
//        LocalDate actualDateTo = null;
//
//        if(dateRequest.getFrom().isBefore(dateLimit)){
//            historyDateFrom = dateRequest.getFrom();
//            historyDateTo = Arrays.asList(dateLimit,dateRequest.getTo()).stream().min(LocalDate::compareTo).get();
//        }
//
//        if(dateRequest.getTo().isAfter(dateLimit)){
//            actualDateFrom = Arrays.asList(dateLimit,dateRequest.getFrom()).stream().max(LocalDate::compareTo).get();
//            if(historyDateTo!=null){
//                actualDateFrom = actualDateFrom.plusDays(1);
//            }
//            actualDateTo = dateRequest.getTo();
//        }
//
//        return Pair.of(
//                historyDateFrom!=null? DateRequest.builder().from(historyDateFrom).to(historyDateTo).build():null,
//                actualDateFrom!=null? DateRequest.builder().from(actualDateFrom).to(actualDateTo).build():null
//        );
//    }

//    public Map countByPartitionKey(String pk) {
//        log.infof("Querying partitionKey on table storage: %s", pk);
//        Instant start = Instant.now();
//        long tableItems = reTableStorageClient.findReByPartitionKey(pk);
//        Instant finish = Instant.now();
//        long tableTimeElapsed = Duration.between(start, finish).toMillis();
//        log.infof("Done querying partitionKey %s on table storage. Count %s", pk, tableItems);
//
//
//        log.infof("Querying partitionKey on cosmos: %s", pk);
//        start = Instant.now();
//        Long cosmosItems = reClient.findReByPartitionKey(pk).stream().findFirst().get().getCount();
//        finish = Instant.now();
//        long cosmosTimeElapsed = Duration.between(start, finish).toMillis();
//        log.infof("Done querying partitionKey %s on cosmos. Count %s", pk, cosmosItems);
//
//
//        Map<String, Map> response = new HashMap<>();
//        Map<String, Long> table = new HashMap<>();
//        table.put("items", tableItems);
//        table.put("millis", tableTimeElapsed);
//
//        Map<String, Long> cosmos = new HashMap<>();
//        cosmos.put("items", cosmosItems);
//        cosmos.put("millis", cosmosTimeElapsed);
//
//        response.put("table", table);
//        response.put("cosmos", cosmos);
//
//        return response;
//    }
}
