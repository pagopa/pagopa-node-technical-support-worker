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
                .positiveBizEvtId(evt.getId())
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

    private PaymentAttemptInfo eventToPaymentAttemptInfo(PositiveBizEvent evt) {
        return PaymentAttemptInfo.builder()
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

    private PaymentAttemptInfo eventToPaymentAttemptInfo(NegativeBizEvent evt) {
        return PaymentAttemptInfo.builder()
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

        collect.addAll(verifyKOEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(positiveEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(negativeEvents.stream().map(this::eventToPaymentInfo).toList());

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

        collect.addAll(verifyKOEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(positiveEvents.stream().map(this::eventToPaymentInfo).toList());
        collect.addAll(negativeEvents.stream().map(this::eventToPaymentInfo).toList());

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

}
