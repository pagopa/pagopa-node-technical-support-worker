package it.gov.pagopa.nodetsworker.service;

import com.azure.cosmos.implementation.apachecommons.lang.tuple.Pair;
import io.quarkus.mongodb.panache.PanacheQuery;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.BasePaymentInfo;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.PaymentAttemptInfo;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.ReTableService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkerService {

  private static String outcomeOK = "OK";
  private static String outcomeKO = "KO";

  @Inject Logger log;

  @Inject
  CosmosBizEventClient positiveBizClient;
  @Inject
  CosmosNegBizEventClient negativeBizClient;

  @Inject
  ReTableService reTableService;

  @ConfigProperty(name = "re-cosmos.day-limit")
  Integer reCosmosDayLimit;

  @ConfigProperty(name = "date-range-limit")
  Integer dateRangeLimit;

  private PaymentInfo eventToPaymentInfo(EventEntity firstEvent,EventEntity lastEvent) {
    return PaymentInfo.builder()
        .pspId(lastEvent.getPsp())
        .serviceIdentifier(lastEvent.getServiceIdentifier())
        .channelId(lastEvent.getCanale())
        .insertedTimestamp(firstEvent.getInsertedTimestamp())
        .updatedTimestamp(lastEvent.getInsertedTimestamp())
        .paymentToken(lastEvent.getPaymentToken())
        .ccp(lastEvent.getCcp())
        .noticeNumber(lastEvent.getNoticeNumber())
        .iuv(lastEvent.getIuv())
        .organizationFiscalCode(lastEvent.getIdDominio())
        .status(StatusUtil.statoByReStatus(lastEvent.getStatus()))
        .build();
  }

  private PaymentAttemptInfo eventToPaymentAttemptInfo(EventEntity firstEvent, EventEntity lastEvent) {
    return PaymentAttemptInfo.builder()
        .pspId(lastEvent.getPsp())
        .serviceIdentifier(lastEvent.getServiceIdentifier())
        .channelId(lastEvent.getCanale())
        .insertedTimestamp(lastEvent.getInsertedTimestamp())
        .updatedTimestamp(lastEvent.getInsertedTimestamp())
        .noticeNumber(lastEvent.getNoticeNumber())
        .paymentToken(lastEvent.getPaymentToken())
        .ccp(lastEvent.getCcp())
        .iuv(lastEvent.getIuv())
        .organizationFiscalCode(lastEvent.getIdDominio())
        .status(StatusUtil.statoByReStatus(lastEvent.getStatus()))
        .stationId(lastEvent.getStazione())
        .build();
  }

  private void enrichPaymentAttemptInfo(PaymentAttemptInfo pai, PositiveBizEvent pbe) {
    pai.setPaymentToken(pbe.getPaymentInfo().getPaymentToken());
    pai.setBrokerPspId(pbe.getPsp().getIdBrokerPsp());
    pai.setAmount(pbe.getPaymentInfo().getAmount());
    pai.setFee(pbe.getPaymentInfo().getFee());
    pai.setFeeOrganization(pbe.getPaymentInfo().getPrimaryCiIncurredFee());
    pai.setPaymentMethod(pbe.getPaymentInfo().getPaymentMethod());
    if("CP".equals(pbe.getPaymentInfo().getPaymentMethod())){
      pai.setPmReceipt(pbe.getTransactionDetails() != null?"sent":"notSent");
    }
    pai.setTouchPoint(pbe.getPaymentInfo().getTouchpoint());
    pai.setPositiveBizEvtId(pbe.getId());
  }

  private void enrichPaymentAttemptInfo( PaymentAttemptInfo pai, NegativeBizEvent nbe) {
    pai.setBrokerPspId(nbe.getPsp().getIdBrokerPsp());
    pai.setPaymentToken(nbe.getPaymentInfo().getPaymentToken());
    pai.setAmount(nbe.getPaymentInfo().getAmount());
    pai.setPaymentMethod(nbe.getPaymentInfo().getPaymentMethod());
    if("CP".equals(nbe.getPaymentInfo().getPaymentMethod())){
      pai.setPmReceipt(nbe.getTransactionDetails() != null?"sent":"notSent");
    }
    pai.setTouchPoint(nbe.getPaymentInfo().getTouchpoint());
    pai.setNegativeBizEvtId(nbe.getId());
  }

  public TransactionResponse getInfoByNoticeNumber(
      String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {

    DateRequest dateRequest = verifyDate(dateFrom, dateTo);

    Pair<DateRequest, DateRequest> reDates = getHistoryDates(dateRequest);
    List<EventEntity> reStorageEvents = new ArrayList<>();
    if(reDates.getLeft()!=null){
      log.infof("Querying re table storage");
      reStorageEvents.addAll(
              reTableService.findReByCiAndNN(
                      reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, noticeNumber)
      );
      log.infof("Done querying re table storage");
    }
    if(reDates.getRight()!=null){
      log.infof("Querying re cosmos");
      reStorageEvents.addAll(
        EventEntity.findReByCiAndNN(
                organizationFiscalCode,
                noticeNumber,
                reDates.getRight().getFrom(),
                reDates.getRight().getTo()
        ).stream().toList()
      );
      log.infof("Done querying re cosmos");
    }

    Map<String, List<EventEntity>> reGroups =
        reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getPaymentToken));

    log.infof("found %d different tokens", reGroups.size());

    List<BasePaymentInfo> collect =
        reGroups.keySet().stream()
            .map(
                paymentToken -> {
                  List<EventEntity> events = reGroups.get(paymentToken);
                  EventEntity firstEvent = events.get(0);
                  EventEntity lastEvent = events.get(events.size() - 1);
                  PaymentInfo pi = eventToPaymentInfo(firstEvent, lastEvent);
                  log.infof("Querying positive biz events");
                  Optional<PositiveBizEvent> pos =
                      positiveBizClient
                          .getEventsByCiAndNN(
                              lastEvent.getIdDominio(),
                              lastEvent.getNoticeNumber(),
                              dateRequest.getFrom(),
                              dateRequest.getTo())
                          .stream()
                          .findFirst();

                  log.infof("Done querying positive biz events");
                  if (pos.isPresent()) {
                    pi.setOutcome(outcomeOK);
                    pi.setBrokerPspId(pos.get().getPsp().getIdBrokerPsp());
                  } else {
                    log.infof("Querying negative biz events");
                    Optional<NegativeBizEvent> neg =
                        negativeBizClient
                            .findEventsByCiAndNNAndToken(
                                lastEvent.getIdDominio(),
                                lastEvent.getNoticeNumber(),
                                paymentToken,
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
                  return pi;
                })
            .collect(Collectors.toList());

    return TransactionResponse.builder()
        .dateFrom(dateRequest.getFrom())
        .dateTo(dateRequest.getTo())
        .payments(collect)
        .build();
  }

  public TransactionResponse getInfoByIUV(
      String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {

    DateRequest dateRequest = verifyDate(dateFrom, dateTo);
    Pair<DateRequest, DateRequest> reDates = getHistoryDates(dateRequest);
    List<EventEntity> reStorageEvents = new ArrayList<>();
    if(reDates.getLeft()!=null){
      log.infof("Querying re table storage");
      reStorageEvents.addAll(
              reTableService.findReByCiAndIUV(
                      reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, iuv)
      );
      log.infof("Done querying re table storage");
    }
    if(reDates.getRight()!=null){
      log.infof("Querying re cosmos");
      reStorageEvents.addAll(
              EventEntity.findReByCiAndIUV(
                      organizationFiscalCode,
                      iuv,
                      reDates.getRight().getFrom(),
                      reDates.getRight().getTo()
              ).stream().toList()
      );
      log.infof("Done querying re cosmos");
    }

    Map<String, List<EventEntity>> reGroups =
        reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getCcp));

    log.infof("found %d different ccps", reGroups.size());

    List<BasePaymentInfo> collect =
        reGroups.keySet().stream()
            .map(
                ccp -> {
                  List<EventEntity> events = reGroups.get(ccp);
                  EventEntity firstEvent = events.get(events.size() - 1);
                  EventEntity lastEvent = events.get(events.size() - 1);
                  PaymentInfo pi = eventToPaymentInfo(firstEvent, lastEvent);

                  Optional<PositiveBizEvent> pos =
                      positiveBizClient
                          .getEventsByCiAndIUV(
                              lastEvent.getIdDominio(),
                              lastEvent.getIuv(),
                              dateRequest.getFrom(),
                              dateRequest.getTo())
                          .stream()
                          .findFirst();
                  if (pos.isPresent()) {
                    pi.setOutcome(outcomeOK);
                    pi.setPositiveBizEvtId(pos.get().getId());
                    pi.setBrokerPspId(pos.get().getPsp().getIdBrokerPsp());
                  } else {
                    Optional<NegativeBizEvent> neg =
                        negativeBizClient
                            .findEventsByCiAndIUVAndCCP(
                                lastEvent.getIdDominio(),
                                lastEvent.getIuv(),
                                ccp,
                                dateRequest.getFrom(),
                                dateRequest.getTo())
                            .stream()
                            .findFirst();
                    if (neg.isPresent()) {
                      pi.setNegativeBizEvtId(neg.get().getId());
                      pi.setBrokerPspId(neg.get().getPsp().getIdBrokerPsp());
                      if (!neg.get().getReAwakable()) {
                        pi.setOutcome(outcomeKO);
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
    if(reDates.getLeft()!=null){
      log.infof("Querying re table storage");
      reStorageEvents.addAll(
              reTableService.findReByCiAndNNAndToken(
                      reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, noticeNumber,paymentToken)
      );
      log.infof("Done querying re table storage");
    }
    if(reDates.getRight()!=null){
      log.infof("Querying re cosmos");
      reStorageEvents.addAll(
              EventEntity.findReByCiAndNNAndToken(
                      organizationFiscalCode,
                      noticeNumber,
                      paymentToken,
                      reDates.getRight().getFrom(),
                      reDates.getRight().getTo()
              ).stream().toList()
      );
      log.infof("Done querying re cosmos");
    }

    List<BasePaymentInfo> pais = new ArrayList<>();
    if (reStorageEvents.size() > 0) {
      EventEntity firstEvent = reStorageEvents.get(0);
      EventEntity lastEvent = reStorageEvents.get(reStorageEvents.size() - 1);
      PaymentAttemptInfo pai = eventToPaymentAttemptInfo(firstEvent, lastEvent);

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
    if(reDates.getLeft()!=null){
      log.infof("Querying re table storage");
      reStorageEvents.addAll(
              reTableService.findReByCiAndIUVAndCCP(
                      reDates.getLeft().getFrom(), reDates.getLeft().getTo(), organizationFiscalCode, iuv,ccp)
      );
      log.infof("Done querying re table storage");
    }
    if(reDates.getRight()!=null){
      log.infof("Querying re cosmos");
      reStorageEvents.addAll(
        EventEntity.findReByCiAndIUVAndCCP(
                organizationFiscalCode,
                iuv,
                ccp,
                reDates.getRight().getFrom(),
                reDates.getRight().getTo()
        ).stream().toList()
      );
      log.infof("Done querying re cosmos");
    }
    List<BasePaymentInfo> pais = new ArrayList<>();
    if (reStorageEvents.size() > 0) {
      EventEntity firstEvent = reStorageEvents.get(0);
      EventEntity lastEvent = reStorageEvents.get(reStorageEvents.size() - 1);
      PaymentAttemptInfo pai = eventToPaymentAttemptInfo(firstEvent, lastEvent);
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
        enrichPaymentAttemptInfo( pai, pos.get());
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
    if(ChronoUnit.DAYS.between(dateFrom, dateTo)>dateRangeLimit){
      throw new AppException(
              AppErrorCodeMessageEnum.INTERVAL_TOO_LARGE,
              dateRangeLimit);
    }
    return DateRequest.builder().from(dateFrom).to(dateTo).build();
  }

  private Pair<DateRequest, DateRequest> getHistoryDates(DateRequest dateRequest) {

    LocalDate dateLimit = LocalDate.now().minusDays(reCosmosDayLimit);

    if(dateRequest.getFrom().isBefore(dateLimit) && dateRequest.getTo().isBefore(dateLimit)){
      return Pair.of(
              DateRequest.builder().from(dateRequest.getFrom()).to(dateRequest.getTo()).build(),
              null
      );
    } else if(dateRequest.getFrom().isBefore(dateLimit) && dateRequest.getTo().isAfter(dateLimit)) {
      return Pair.of(
              DateRequest.builder().from(dateRequest.getFrom()).to(dateLimit).build(),
              DateRequest.builder().from(dateLimit).to(dateRequest.getTo()).build()
      );
    } else {
      return Pair.of(
              null,
              DateRequest.builder().from(dateRequest.getFrom()).to(dateRequest.getTo()).build()
      );
    }
  }

  public Map countByPartitionKey(String pk) {
    log.infof("Querying partitionKey on table storage: %s", pk);
    Instant start = Instant.now();
    long tableItems = reTableService.findReByPartition(pk);
    Instant finish = Instant.now();
    long tableTimeElapsed = Duration.between(start, finish).toMillis();
    log.infof("Done querying partitionKey %s on table storage. Count %s", pk, tableItems);




    log.infof("Querying partitionKey on cosmos: %s", pk);
    start = Instant.now();
    Long cosmosItems = EventEntity.findReByPartitionKey(pk);
    finish = Instant.now();
    long cosmosTimeElapsed = Duration.between(start, finish).toMillis();
    log.infof("Done querying partitionKey %s on cosmos. Count %s", pk, cosmosItems);

    log.infof("Querying partitionKey on cosmos with panache: %s", pk);
    start = Instant.now();
    Long panacheItems = EventEntity.findReByPartitionKeyPanache(pk);
    finish = Instant.now();
    Long panacheTimeElapsed = Duration.between(start, finish).toMillis();
    log.infof("Done querying partitionKey %s on cosmos with panache. Count %s", pk, panacheItems);


    Map<String, Map> response = new HashMap<>();
    Map<String, Long> table = new HashMap<>();
    table.put("items", tableItems);
    table.put("millis", tableTimeElapsed);

    Map<String, Long> cosmos = new HashMap<>();
    cosmos.put("items", cosmosItems);
    cosmos.put("millis", cosmosTimeElapsed);

    Map<String, Long> panache = new HashMap<>();
    panache.put("items", panacheItems);
    panache.put("millis", panacheTimeElapsed);

    response.put("table", table);
    response.put("cosmos", cosmos);
    response.put("panache", panache);

    return response;
  }
}
