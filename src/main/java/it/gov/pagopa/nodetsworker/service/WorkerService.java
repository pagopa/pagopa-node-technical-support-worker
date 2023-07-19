package it.gov.pagopa.nodetsworker.service;

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
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkerService {

  private static String outcomeOK = "OK";
  private static String outcomeKO = "KO";

  @Inject Logger log;

//  @Inject EventMapper eventsMapper;

  @Inject CosmosBizEventClient positiveBizClient;
  @Inject CosmosNegBizEventClient negativeBizClient;

  @Inject ReTableService reTableService;

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
    pai.setIsOldPaymentModel(pbe.getDebtorPosition().getModelType().equals("1"));
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
    pai.setIsOldPaymentModel(nbe.getDebtorPosition().getModelType().equals("1"));
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
    List<EventEntity> reStorageEvents =
        reTableService.findReByCiAndNN(
            dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, noticeNumber);

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

                  Optional<PositiveBizEvent> pos =
                      positiveBizClient
                          .getEventsByCiAndNN(
                              lastEvent.getIdDominio(),
                              lastEvent.getNoticeNumber(),
                              dateRequest.getFrom(),
                              dateRequest.getTo())
                          .stream()
                          .findFirst();
                  if (pos.isPresent()) {
                    pi.setOutcome(outcomeOK);
                    pi.setBrokerPspId(pos.get().getPsp().getIdBrokerPsp());
                  } else {
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
    List<EventEntity> reStorageEvents =
        reTableService.findReByCiAndIUV(
            dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, iuv);

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
    List<EventEntity> events =
        reTableService.findReByCiAndNNAndToken(
            dateRequest.getFrom(),
            dateRequest.getTo(),
            organizationFiscalCode,
            noticeNumber,
            paymentToken);
    List<BasePaymentInfo> pais = new ArrayList<>();
    if (events.size() > 0) {
      EventEntity firstEvent = events.get(0);
      EventEntity lastEvent = events.get(events.size() - 1);
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
    List<EventEntity> events =
        reTableService.findReByCiAndIUVAndCCP(
            dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, iuv, ccp);
    List<BasePaymentInfo> pais = new ArrayList<>();
    if (events.size() > 0) {
      EventEntity firstEvent = events.get(0);
      EventEntity lastEvent = events.get(events.size() - 1);
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
      dateFrom = dateTo.minusDays(10);
    }
    return DateRequest.builder().from(dateFrom).to(dateTo).build();
  }
}
