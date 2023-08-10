package it.gov.pagopa.nodetsworker.service;

import com.azure.cosmos.implementation.apachecommons.lang.tuple.Pair;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.BasePaymentInfo;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.PaymentAttemptInfo;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosReEventClient;
import it.gov.pagopa.nodetsworker.repository.ReTableStorageClient;
import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.model.PositiveBizEvent;
import it.gov.pagopa.nodetsworker.resources.response.TransactionResponse;
import it.gov.pagopa.nodetsworker.util.StatusUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkerService {

    private static String outcomeOK = "OK";
    private static String outcomeKO = "KO";

    @Inject
    Logger log;

    @Inject
    CosmosReEventClient reClient;
    @Inject
    CosmosBizEventClient positiveBizClient;
    @Inject
    CosmosNegBizEventClient negativeBizClient;

    @Inject
    ReTableStorageClient reTableStorageClient;

    @ConfigProperty(name = "re-cosmos.day-limit")
    Integer reCosmosDayLimit;

    @ConfigProperty(name = "date-range-limit")
    Integer dateRangeLimit;

    private List<String> tipiEventoAttempts = Arrays.asList("activatePaymentNotice","nodoInviaRPT","nodoInviaCarrelloRPT");

    private PaymentInfo eventToPaymentInfo(EventEntity activation) {
        return PaymentInfo.builder()
                .primitive(activation.getTipoEvento())
                .pspId(activation.getPsp())
                .serviceIdentifier(activation.getServiceIdentifier())
                .channelId(activation.getCanale())
                .insertedTimestamp(activation.getInsertedTimestamp())
                .updatedTimestamp(activation.getInsertedTimestamp())
                .paymentToken(activation.getPaymentToken())
                .ccp(activation.getCcp())
                .noticeNumber(activation.getNoticeNumber())
                .iuv(activation.getIuv())
                .organizationFiscalCode(activation.getIdDominio())
                .build();
    }

    private PaymentAttemptInfo eventToPaymentAttemptInfo(EventEntity activation, EventEntity lastEvent) {
        return PaymentAttemptInfo.builder()
                .primitive(activation.getTipoEvento())
                .pspId(activation.getPsp())
                .serviceIdentifier(activation.getServiceIdentifier())
                .channelId(activation.getCanale())
                .insertedTimestamp(activation.getInsertedTimestamp())
                .updatedTimestamp(lastEvent.getInsertedTimestamp())
                .noticeNumber(activation.getNoticeNumber())
                .paymentToken(activation.getPaymentToken())
                .ccp(activation.getCcp())
                .iuv(activation.getIuv())
                .organizationFiscalCode(activation.getIdDominio())
                .status(StatusUtil.statoByReStatus(lastEvent.getStatus()))
                .paymentStatus(lastEvent.getStatus())
                .stationId(activation.getStazione())
                .build();
    }

    private void enrichPaymentAttemptInfo(PaymentAttemptInfo pai, PositiveBizEvent pbe) {
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
    }

    private void enrichPaymentAttemptInfo(PaymentAttemptInfo pai, NegativeBizEvent nbe) {
        pai.setBrokerPspId(nbe.getPsp().getIdBrokerPsp());
        pai.setPaymentToken(nbe.getPaymentInfo().getPaymentToken());
        pai.setAmount(nbe.getPaymentInfo().getAmount());
        pai.setPaymentMethod(nbe.getPaymentInfo().getPaymentMethod());
        if ("CP".equals(nbe.getPaymentInfo().getPaymentMethod())) {
            pai.setPmReceipt(nbe.getTransactionDetails() != null ? "sent" : "notSent");
        }
        pai.setTouchPoint(nbe.getPaymentInfo().getTouchpoint());
        pai.setNegativeBizEvtId(nbe.getId());
    }

    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

        Pair<DateRequest, DateRequest> reDates = getHistoryDates(dateRequest);
        List<EventEntity> reStorageEvents = new ArrayList<>();
        if (reDates.getLeft() != null) {
            log.infof("Querying re table storage");
            reStorageEvents.addAll(
                    reTableStorageClient.findReByCiAndNN(
                            reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, noticeNumber)
            );
            log.infof("Done querying re table storage");
        }
        if (reDates.getRight() != null) {
            log.infof("Querying re cosmos");
            reStorageEvents.addAll(
                    reClient.findReByCiAndNNAndToken(
                            organizationFiscalCode,
                            noticeNumber,
                            Optional.empty(),
                            reDates.getRight().getFrom(),
                            reDates.getRight().getTo()
                    ).stream().toList()
            );
            log.infof("Done querying re cosmos");
        }

        AtomicInteger pcount = new AtomicInteger(0);
        Map<String, List<EventEntity>> paymentAttempts = reStorageEvents
                .stream()
                .filter(ev -> "REQ".equals(ev.getSottoTipoEvento()) && ev.getEsito().equals("RICEVUTA") && tipiEventoAttempts.contains(ev.getTipoEvento()))
                .collect(Collectors.groupingBy(
                        payment -> (payment.getPaymentToken() == null) ? "attempt_"+(pcount.incrementAndGet()) : payment.getPaymentToken()
                ));


        log.infof("found %d different payment attempts", paymentAttempts.size());

        List<BasePaymentInfo> collect =
                paymentAttempts.values().stream()
                        .map(
                                values -> {
                                    EventEntity activation = values.get(0);
                                    PaymentInfo pi = eventToPaymentInfo(activation);
                                    if (activation.getPaymentToken() != null) {
                                        List<EventEntity> lastStatoPayment = reStorageEvents.stream()
                                                .filter(e -> "CAMBIO_STATO".equals(e.getEsito()) && !e.getStatus().startsWith("position_") && !e.getStatus().startsWith("receipt_recipient_") && activation.getPaymentToken().equals(e.getPaymentToken()))
                                                .sorted(Comparator.comparing(EventEntity::getInsertedTimestamp)).toList();
                                        pi.setStatus(StatusUtil.statoByReStatus(lastStatoPayment.get(lastStatoPayment.size() - 1).getStatus()));
                                        pi.setPaymentStatus(lastStatoPayment.get(lastStatoPayment.size() - 1).getStatus());
                                        log.infof("Querying positive biz events");
                                        Optional<PositiveBizEvent> pos =
                                                positiveBizClient
                                                        .findEventsByCiAndNNAndToken(
                                                                organizationFiscalCode,
                                                                noticeNumber,
                                                                activation.getPaymentToken(),
                                                                dateRequest.getFrom(),
                                                                dateRequest.getTo())
                                                        .stream()
                                                        .findFirst();
                                        log.infof("Done querying positive biz events");
                                        if (pos.isPresent()) {
                                            pi.setOutcome(outcomeOK);
                                            pi.setPositiveBizEvtId(pos.get().getId());
                                            pi.setBrokerPspId(pos.get().getPsp().getIdBrokerPsp());
                                        } else {
                                            log.infof("Querying negative biz events");
                                            Optional<NegativeBizEvent> neg =
                                                    negativeBizClient
                                                            .findEventsByCiAndNNAndToken(
                                                                    organizationFiscalCode,
                                                                    noticeNumber,
                                                                    activation.getPaymentToken(),
                                                                    dateRequest.getFrom(),
                                                                    dateRequest.getTo())
                                                            .stream()
                                                            .findFirst();

                                            log.infof("Done querying negative biz events");
                                            if (neg.isPresent()) {
                                                pi.setNegativeBizEvtId(neg.get().getId());
                                                pi.setBrokerPspId(neg.get().getPsp().getIdBrokerPsp());
                                                if (!neg.get().getReAwakable()) {
                                                    pi.setOutcome(outcomeKO);

                                                }
                                            }
                                        }
                                    }
                                    return pi;
                                })
                        .collect(Collectors.toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .count(collect.size())
                .payments(collect.stream().sorted(Comparator.comparing(BasePaymentInfo::getInsertedTimestamp)).toList())
                .build();
    }

    public TransactionResponse getInfoByIUV(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        Pair<DateRequest, DateRequest> reDates = getHistoryDates(dateRequest);
        List<EventEntity> reStorageEvents = new ArrayList<>();
        if (reDates.getLeft() != null) {
            log.infof("Querying re table storage");
            reStorageEvents.addAll(
                    reTableStorageClient.findReByCiAndIUV(
                            reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, iuv)
            );
            log.infof("Done querying re table storage");
        }
        if (reDates.getRight() != null) {
            log.infof("Querying re cosmos");
            reStorageEvents.addAll(
                    reClient.findReByCiAndIUVAndCCP(
                            organizationFiscalCode,
                            iuv,
                            Optional.empty(),
                            reDates.getRight().getFrom(),
                            reDates.getRight().getTo()
                    ).stream().toList()
            );
            log.infof("Done querying re cosmos");
        }

        List<EventEntity> paymentAttemps = reStorageEvents.stream().filter(ev -> "REQ".equals(ev.getSottoTipoEvento()) && tipiEventoAttempts.contains(ev.getTipoEvento())).toList();
        log.infof("found %d different payment attempts", paymentAttemps.size());

        List<BasePaymentInfo> collect =
                paymentAttemps.stream()
                        .map(
                                activation -> {
                                    PaymentInfo pi = eventToPaymentInfo(activation);
                                    if (activation.getCcp() != null) {
                                        List<EventEntity> lastStatoPayment = reStorageEvents.stream()
                                                .filter(e -> "CAMBIO_STATO".equals(e.getEsito()) && !e.getStatus().startsWith("position_") && !e.getStatus().startsWith("receipt_recipient_") && activation.getCcp().equals(e.getCcp()))
                                                .sorted(Comparator.comparing(EventEntity::getInsertedTimestamp)).toList();
                                        pi.setStatus(StatusUtil.statoByReStatus(lastStatoPayment.get(lastStatoPayment.size() - 1).getStatus()));
                                        pi.setPaymentStatus(lastStatoPayment.get(lastStatoPayment.size() - 1).getStatus());
                                        log.infof("Querying positive biz events");
                                        Optional<PositiveBizEvent> pos =
                                                positiveBizClient
                                                        .findEventsByCiAndIUVAndCCP(
                                                                organizationFiscalCode,
                                                                iuv,
                                                                activation.getCcp(),
                                                                dateRequest.getFrom(),
                                                                dateRequest.getTo())
                                                        .stream()
                                                        .findFirst();
                                        log.infof("Done querying positive biz events");
                                        if (pos.isPresent()) {
                                            pi.setOutcome(outcomeOK);
                                            pi.setPositiveBizEvtId(pos.get().getId());
                                            pi.setBrokerPspId(pos.get().getPsp().getIdBrokerPsp());
                                        } else {
                                            log.infof("Querying negative biz events");
                                            Optional<NegativeBizEvent> neg =
                                                    negativeBizClient
                                                            .findEventsByCiAndIUVAndCCP(
                                                                    organizationFiscalCode,
                                                                    iuv,
                                                                    activation.getCcp(),
                                                                    dateRequest.getFrom(),
                                                                    dateRequest.getTo())
                                                            .stream()
                                                            .findFirst();

                                            log.infof("Done querying negative biz events");
                                            if (neg.isPresent()) {
                                                pi.setNegativeBizEvtId(neg.get().getId());
                                                pi.setBrokerPspId(neg.get().getPsp().getIdBrokerPsp());
                                                if (!neg.get().getReAwakable()) {
                                                    pi.setOutcome(outcomeKO);

                                                }
                                            }
                                        }
                                    }
                                    return pi;
                                })
                        .collect(Collectors.toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(collect)
                .build();
    }

    public TransactionResponse getAttemptByNoticeNumberAndPaymentToken(
            String organizationFiscalCode,
            String noticeNumber,
            String paymentToken,
            LocalDate dateFrom,
            LocalDate dateTo) {

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

        Pair<DateRequest, DateRequest> reDates = getHistoryDates(dateRequest);
        List<EventEntity> reStorageEvents = new ArrayList<>();
        if (reDates.getLeft() != null) {
            log.infof("Querying re table storage");
            reStorageEvents.addAll(
                    reTableStorageClient.findReByCiAndNNAndToken(
                            reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, noticeNumber, paymentToken)
            );
            log.infof("Done querying re table storage");
        }
        if (reDates.getRight() != null) {
            log.infof("Querying re cosmos");
            reStorageEvents.addAll(
                    reClient.findReByCiAndNNAndToken(
                            organizationFiscalCode,
                            noticeNumber,
                            Optional.of(paymentToken),
                            reDates.getRight().getFrom(),
                            reDates.getRight().getTo()
                    ).stream().toList()
            );
            log.infof("Done querying re cosmos");
        }

        List<EventEntity> activations = reStorageEvents.stream().filter(ev -> "REQ".equals(ev.getSottoTipoEvento()) && tipiEventoAttempts.contains(ev.getTipoEvento())).toList();

        List<BasePaymentInfo> pais = new ArrayList<>();
        if (!activations.isEmpty()) {
            EventEntity activation = activations.get(0);
            List<EventEntity> lastStatoPayment = reStorageEvents.stream()
                    .filter(e -> "CAMBIO_STATO".equals(e.getEsito()) && !e.getStatus().startsWith("position_") && !e.getStatus().startsWith("receipt_recipient_"))
                    .sorted(Comparator.comparing(EventEntity::getInsertedTimestamp)).toList();
            PaymentAttemptInfo pai = eventToPaymentAttemptInfo(activation, lastStatoPayment.get(lastStatoPayment.size()-1));

            Optional<PositiveBizEvent> pos =
                    positiveBizClient
                            .findEventsByCiAndNNAndToken(
                                    organizationFiscalCode,
                                    noticeNumber,
                                    paymentToken,
                                    dateRequest.getFrom(),
                                    dateRequest.getTo())
                            .stream()
                            .findFirst();
            if (pos.isPresent()) {
                pai.setOutcome(outcomeOK);
                enrichPaymentAttemptInfo(pai, pos.get());
            } else {
                Optional<NegativeBizEvent> neg =
                        negativeBizClient
                                .findEventsByCiAndNNAndToken(
                                        organizationFiscalCode,
                                        noticeNumber,
                                        paymentToken,
                                        dateRequest.getFrom(),
                                        dateRequest.getTo())
                                .stream()
                                .findFirst();
                if (neg.isPresent()) {
                    if (!neg.get().getReAwakable()) {
                        pai.setOutcome(outcomeKO);
                    }
                    enrichPaymentAttemptInfo(pai, neg.get());
                }
            }
            pais.add(pai);
        } else {
            throw new AppException(
                    AppErrorCodeMessageEnum.NOT_FOUND);
        }

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(pais)
                .build();
    }

    public TransactionResponse getAttemptByIUVAndCCP(
            String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        Pair<DateRequest, DateRequest> reDates = getHistoryDates(dateRequest);
        List<EventEntity> reStorageEvents = new ArrayList<>();
        if (reDates.getLeft() != null) {
            log.infof("Querying re table storage");
            reStorageEvents.addAll(
                    reTableStorageClient.findReByCiAndIUVAndCCP(
                            reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, iuv, ccp)
            );
            log.infof("Done querying re table storage");
        }
        if (reDates.getRight() != null) {
            log.infof("Querying re cosmos");
            reStorageEvents.addAll(
                    reClient.findReByCiAndIUVAndCCP(
                            organizationFiscalCode,
                            iuv,
                            Optional.of(ccp),
                            reDates.getRight().getFrom(),
                            reDates.getRight().getTo()
                    ).stream().toList()
            );
            log.infof("Done querying re cosmos");
        }
        List<EventEntity> activations = reStorageEvents.stream().filter(ev -> "REQ".equals(ev.getSottoTipoEvento()) && tipiEventoAttempts.contains(ev.getTipoEvento())).toList();

        List<BasePaymentInfo> pais = new ArrayList<>();
        if (!activations.isEmpty()) {
            EventEntity activation = activations.get(0);
            List<EventEntity> lastStatoPayment = reStorageEvents.stream()
                    .filter(e -> "CAMBIO_STATO".equals(e.getEsito()) && !e.getStatus().startsWith("position_") && !e.getStatus().startsWith("receipt_recipient_"))
                    .sorted(Comparator.comparing(EventEntity::getInsertedTimestamp)).toList();
            PaymentAttemptInfo pai = eventToPaymentAttemptInfo(activation, lastStatoPayment.get(lastStatoPayment.size()-1));
            String outcome = null;

            Optional<PositiveBizEvent> pos =
                    positiveBizClient
                            .findEventsByCiAndIUVAndCCP(
                                    organizationFiscalCode, iuv, ccp, dateRequest.getFrom(), dateRequest.getTo())
                            .stream()
                            .findFirst();
            if (pos.isPresent()) {
                pai.setOutcome(outcomeOK);
                pai.setBrokerPspId(pos.get().getPsp().getIdPsp());
                enrichPaymentAttemptInfo(pai, pos.get());
            } else {
                Optional<NegativeBizEvent> neg =
                        negativeBizClient
                                .findEventsByCiAndIUVAndCCP(
                                        organizationFiscalCode, iuv, ccp, dateRequest.getFrom(), dateRequest.getTo())
                                .stream()
                                .findFirst();
                if (neg.isPresent()) {
                    pai.setBrokerPspId(neg.get().getPsp().getIdBrokerPsp());
                    if (!neg.get().getReAwakable()) {
                        pai.setOutcome(outcomeKO);
                    }
                    enrichPaymentAttemptInfo(pai, neg.get());
                }
            }
            pais.add(pai);
        } else {
            throw new AppException(
                    AppErrorCodeMessageEnum.NOT_FOUND);
        }

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(pais)
                .build();
    }

    /**
     * Check dates validity
     *
     * @param dateFrom
     * @param dateTo
     */
    private DateRequest verifyDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo != null || dateFrom != null && dateTo == null) {
            throw new AppException(
                    AppErrorCodeMessageEnum.POSITION_SERVICE_DATE_BAD_REQUEST,
                    "Date from and date to must be both defined");
        } else if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new AppException(
                    AppErrorCodeMessageEnum.POSITION_SERVICE_DATE_BAD_REQUEST,
                    "Date from must be before date to");
        }
        if (dateFrom == null && dateTo == null) {
            dateTo = LocalDate.now();
            dateFrom = dateTo.minusDays(dateRangeLimit);
        }
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) > dateRangeLimit - 1) {
            throw new AppException(
                    AppErrorCodeMessageEnum.INTERVAL_TOO_LARGE,
                    dateRangeLimit);
        }
        return DateRequest.builder().from(dateFrom).to(dateTo).build();
    }

    private Pair<DateRequest, DateRequest> getHistoryDates(DateRequest dateRequest) {
        LocalDate dateLimit = LocalDate.now().minusDays(reCosmosDayLimit);
        LocalDate historyDateFrom = null;
        LocalDate historyDateTo = null;
        LocalDate actualDateFrom = null;
        LocalDate actualDateTo = null;

        if(dateRequest.getFrom().isBefore(dateLimit)){
            historyDateFrom = dateRequest.getFrom();
            historyDateTo = Arrays.asList(dateLimit,dateRequest.getTo()).stream().min(LocalDate::compareTo).get();
        }

        if(dateRequest.getTo().isAfter(dateLimit)){
            actualDateFrom = Arrays.asList(dateLimit,dateRequest.getFrom()).stream().max(LocalDate::compareTo).get();
            if(historyDateTo!=null){
                actualDateFrom = actualDateFrom.plusDays(1);
            }
            actualDateTo = dateRequest.getTo();
        }

        return Pair.of(
                historyDateFrom!=null? DateRequest.builder().from(historyDateFrom).to(historyDateTo).build():null,
                actualDateFrom!=null? DateRequest.builder().from(actualDateFrom).to(actualDateTo).build():null
        );
    }

    public Map countByPartitionKey(String pk) {
        log.infof("Querying partitionKey on table storage: %s", pk);
        Instant start = Instant.now();
        long tableItems = reTableStorageClient.findReByPartitionKey(pk);
        Instant finish = Instant.now();
        long tableTimeElapsed = Duration.between(start, finish).toMillis();
        log.infof("Done querying partitionKey %s on table storage. Count %s", pk, tableItems);


        log.infof("Querying partitionKey on cosmos: %s", pk);
        start = Instant.now();
        Long cosmosItems = reClient.findReByPartitionKey(pk).stream().findFirst().get().getCount();
        finish = Instant.now();
        long cosmosTimeElapsed = Duration.between(start, finish).toMillis();
        log.infof("Done querying partitionKey %s on cosmos. Count %s", pk, cosmosItems);


        Map<String, Map> response = new HashMap<>();
        Map<String, Long> table = new HashMap<>();
        table.put("items", tableItems);
        table.put("millis", tableTimeElapsed);

        Map<String, Long> cosmos = new HashMap<>();
        cosmos.put("items", cosmosItems);
        cosmos.put("millis", cosmosTimeElapsed);

        response.put("table", table);
        response.put("cosmos", cosmos);

        return response;
    }
}
