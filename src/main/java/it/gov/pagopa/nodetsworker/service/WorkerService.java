package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.FaultBean;
import it.gov.pagopa.nodetsworker.models.PaymentFullInfo;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosVerifyKOEventClient;
import it.gov.pagopa.nodetsworker.repository.models.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.models.PositiveBizEvent;
import it.gov.pagopa.nodetsworker.repository.models.VerifyKOEvent;
import it.gov.pagopa.nodetsworker.resources.response.PaymentsFullResponse;
import it.gov.pagopa.nodetsworker.resources.response.PaymentsResponse;
import it.gov.pagopa.nodetsworker.util.ValidationUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.nodetsworker.util.AppConstant.SERVICE_ID;
import static it.gov.pagopa.nodetsworker.util.AppConstant.STATUS_COMPLETED;

@ApplicationScoped
public class WorkerService {

    private static final String OUTCOME_OK = "OK";
    private static final String OUTCOME_KO = "KO";

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

    private PaymentInfo eventToPaymentInfo(VerifyKOEvent evt) {

        return PaymentInfo.builder()
                .businessProcess("VerifyPaymentNotice")
                .serviceIdentifier(evt.getServiceIdentifier())
                .pspId(evt.getPsp().getIdPsp())
                .verifyKoEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getFaultBean().getDateTime())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .outcome(OUTCOME_KO)
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
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .outcome(OUTCOME_OK)
                .build();
    }

    private PaymentFullInfo eventToPaymentFullInfo(PositiveBizEvent evt) {
        return PaymentFullInfo.builder()
                .status(STATUS_COMPLETED)
                .serviceIdentifier(evt.getProperties()!=null?evt.getProperties().get(SERVICE_ID):"n/a")
                .pspId(evt.getPsp().getIdPsp())
                .positiveBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .stationId(evt.getCreditor().getIdStation())
                .brokerOrganizationId(evt.getCreditor().getIdBrokerPA())
                .outcome(OUTCOME_OK)
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
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .outcome(evt.getReAwakable()!=null && evt.getReAwakable() ? OUTCOME_KO : null)
                .build();
    }

    private PaymentFullInfo eventToPaymentFullInfo(NegativeBizEvent evt) {
        return PaymentFullInfo.builder()
                .businessProcess(evt.getBusinessProcess())
                .serviceIdentifier(evt.getProperties()!=null?evt.getProperties().get(SERVICE_ID):"n/a")
                .pspId(evt.getPsp().getIdPsp())
                .negativeBizEvtId(evt.getId())
                .brokerPspId(evt.getPsp().getIdBrokerPsp())
                .channelId(evt.getPsp().getIdChannel())
                .insertedTimestamp(evt.getPaymentInfo().getPaymentDateTime())
                .paymentToken(evt.getPaymentInfo().getPaymentToken())
                .ccp(evt.getPaymentInfo().getPaymentToken())
                .noticeNumber(evt.getDebtorPosition().getNoticeNumber())
                .iuv(evt.getDebtorPosition().getIuv())
                .organizationFiscalCode(evt.getCreditor().getIdPA())
                .stationId(evt.getCreditor().getIdStation())
                .brokerOrganizationId(evt.getCreditor().getIdBrokerPA())
                .outcome(evt.getReAwakable()!=null && evt.getReAwakable() ? OUTCOME_KO : null)
                .build();
    }

    private PaymentFullInfo enrichPaymentFullInfo(PaymentFullInfo pai, PositiveBizEvent pbe) {
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

    private PaymentFullInfo enrichPaymentFullInfo(PaymentFullInfo pai, NegativeBizEvent nbe) {
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

    public PaymentsResponse getInfoByNoticeNumber(String organizationFiscalCode, String noticeNumber, Optional<String> paymentToken, LocalDate dateFrom, LocalDate dateTo) {

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

        List<PaymentInfo> collect = new ArrayList<>();

        collect.addAll(verifyKOEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(positiveEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(negativeEvents.stream().map(this::eventToPaymentInfo).toList());

        PaymentsResponse resp = new PaymentsResponse();
        resp.setPayments(collect.stream().sorted(Comparator.comparing(PaymentInfo::getInsertedTimestamp)).toList());
        resp.setDateFrom(dateRequest.getFrom());
        resp.setDateTo(dateRequest.getTo());
        resp.setCount(collect.size());

        return resp;
    }


    public PaymentsResponse getInfoByIUV(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {

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

        List<PaymentInfo> collect = new ArrayList<>();

        collect.addAll(verifyKOEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(positiveEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(negativeEvents.stream().map(this::eventToPaymentInfo).toList());

        PaymentsResponse resp = new PaymentsResponse();
        resp.setPayments(collect.stream().sorted(Comparator.comparing(PaymentInfo::getInsertedTimestamp)).toList());
        resp.setDateFrom(dateRequest.getFrom());
        resp.setDateTo(dateRequest.getTo());
        resp.setCount(collect.size());

        return resp;
    }

    public PaymentsFullResponse getPaymentsFullByNoticeNumberAndPaymentToken(
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

        List<PaymentFullInfo> collect = new ArrayList<>();

        collect.addAll(positiveEvents.stream().map(d-> enrichPaymentFullInfo(eventToPaymentFullInfo(d),d)).toList());
        collect.addAll(negativeEvents.stream().map(d-> enrichPaymentFullInfo(eventToPaymentFullInfo(d),d)).toList());

        PaymentsFullResponse resp = new PaymentsFullResponse();
        resp.setPayments(collect);
        resp.setDateFrom(dateRequest.getFrom());
        resp.setDateTo(dateRequest.getTo());
        resp.setCount(collect.size());
        return resp;
    }

    public PaymentsFullResponse getPaymentsFullByIUVAndCCP(
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

        List<PaymentFullInfo> collect = new ArrayList<>();

        collect.addAll(positiveEvents.stream().map(d-> enrichPaymentFullInfo(eventToPaymentFullInfo(d),d)).toList());
        collect.addAll(negativeEvents.stream().map(d-> enrichPaymentFullInfo(eventToPaymentFullInfo(d),d)).toList());

        PaymentsFullResponse resp = new PaymentsFullResponse();
        resp.setPayments(collect);
        resp.setDateFrom(dateRequest.getFrom());
        resp.setDateTo(dateRequest.getTo());
        resp.setCount(collect.size());
        return resp;
    }

    public NegativeBizEvent getNegativeBizEventById(String bizEventId) {
        /*
        List<NegativeBizEvent> negativeBizEvents = negativeBizClient.getNegativeBizEventById(bizEventId).stream().toList();
        if(negativeBizEvents.size() > 1) {
            throw new AppException(AppErrorCodeMessageEnum.ERROR, "More than one negative biz event with id %s", bizEventId);
        } else if (negativeBizEvents.isEmpty()) {
            throw new AppException(AppErrorCodeMessageEnum.NOT_FOUND, "No negative biz event with id %s", bizEventId);
        }
        return negativeBizEvents.get(0);
        */
        return negativeBizClient.getNegativeBizEventById(bizEventId).stream()
                .findFirst()
                .orElseThrow(
                        () -> new AppException(AppErrorCodeMessageEnum.NOT_FOUND, "No negative biz event with id %s", bizEventId)
                );
    }

}
