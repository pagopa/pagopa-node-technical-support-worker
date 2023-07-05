package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.Config;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.BasePaymentInfo;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.PaymentAttemptInfo;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.ReTableService;
import it.gov.pagopa.nodetsworker.repository.model.Count;
import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.model.PositiveBizEvent;
import it.gov.pagopa.nodetsworker.resources.response.TransactionResponse;
import it.gov.pagopa.nodetsworker.service.mapper.EventMapper;
import it.gov.pagopa.nodetsworker.util.StatusUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.openapi.quarkus.api_config_cache_json.model.ConfigDataV1;

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

    @Inject
    Logger log;

    @Inject
    Config configObject;

    @Inject
    EventMapper eventsMapper;

    @Inject
    CosmosBizEventClient positiveBizClient;
    @Inject
    CosmosNegBizEventClient negativeBizClient;

    @Inject
    ReTableService reTableService;

    private PaymentInfo eventToPaymentInfo(ConfigDataV1 config,EventEntity ee){
        String brokerid = Optional.ofNullable(config.getChannels().get(ee.getCanale())).map(s->s.getBrokerPspCode()).orElse(null);
        return PaymentInfo.builder()
                .pspId(ee.getPsp())
                .nodeId(ee.getServiceIdentifier())
                .channelId(ee.getCanale())
                .brokerPspId(brokerid)
                .insertedTimestamp(ee.getInsertedTimestamp())
                .paymentToken(ee.getPaymentToken())
                .noticeNumber(ee.getNoticeNumber())
                .iuv(ee.getIuv())
                .organizationFiscalCode(ee.getIdDominio())
                .status(StatusUtil.statoByReStatus(ee.getStatus()))
                .build();
    }

    private PaymentAttemptInfo eventToPaymentAttemptInfo(ConfigDataV1 config,EventEntity ee){
        String brokerid = Optional.ofNullable(config.getChannels().get(ee.getCanale())).map(s->s.getBrokerPspCode()).orElse(null);
        return PaymentAttemptInfo.builder()
                .pspId(ee.getPsp())
                .nodeId(ee.getServiceIdentifier())
                .channelId(ee.getCanale())
                .brokerPspId(brokerid)
                .insertedTimestamp(ee.getInsertedTimestamp())
                .paymentToken(ee.getPaymentToken())
                .noticeNumber(ee.getNoticeNumber())
                .iuv(ee.getIuv())
                .organizationFiscalCode(ee.getIdDominio())
                .status(StatusUtil.statoByReStatus(ee.getStatus()))
                .stationId(ee.getStazione())
                .build();
    }

    private void enrichPaymentAttemptInfo(ConfigDataV1 config,PaymentAttemptInfo pai, PositiveBizEvent pbe){
        Long stationVersion = Optional.ofNullable(config.getStations().get(pai.getStationId())).map(s->s.getVersion()).orElse(null);
        pai.setPaymentToken(pbe.getPaymentInfo().getPaymentToken());
        pai.setIsOldPaymentModel(pbe.getDebtorPosition().getModelType().equals("1"));
        if(pai.getBrokerPspId()==null){
            pai.setBrokerPspId(pbe.getPsp().getIdBrokerPsp());
        }
        pai.setStationVersion(stationVersion);
        pai.setAmount(pbe.getPaymentInfo().getAmount());
        pai.setFee(pbe.getPaymentInfo().getFee());
        pai.setFeeOrganization(pbe.getPaymentInfo().getPrimaryCiIncurredFee());
        pai.setPaymentMethod(pbe.getPaymentInfo().getPaymentMethod());
        pai.setPmReceipt(pbe.getTransactionDetails()!=null);
        pai.setPaymentChannel(pbe.getPaymentInfo().getTouchpoint());
        pai.setBundleId(pbe.getPaymentInfo().getIdBundle());
        pai.setBundleOrganizationId(pbe.getPaymentInfo().getIdCiBundle());
        pai.setApplicationDate(pbe.getPaymentInfo().getApplicationDate());
        pai.setTransferDate(pbe.getPaymentInfo().getTransferDate());
    }

    private void enrichPaymentAttemptInfo(ConfigDataV1 config,PaymentAttemptInfo pai, NegativeBizEvent nbe){
        Long stationVersion = Optional.ofNullable(config.getStations().get(pai.getStationId())).map(s->s.getVersion()).orElse(null);
        pai.setIsOldPaymentModel(nbe.getDebtorPosition().getModelType().equals("1"));
        if(pai.getBrokerPspId()==null) {
            pai.setBrokerPspId(nbe.getPsp().getIdBrokerPsp());
        }
        pai.setStationVersion(stationVersion);
        pai.setAmount(nbe.getPaymentInfo().getAmount());
        pai.setPaymentMethod(nbe.getPaymentInfo().getPaymentMethod());
        pai.setPmReceipt(nbe.getTransactionDetails()!=null);
        pai.setPaymentChannel(nbe.getPaymentInfo().getTouchpoint());
    }

    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode,
                                                     String noticeNumber,
                                                     LocalDate dateFrom,
                                                     LocalDate dateTo){

        log.infof("getInfoByNoticeNumber %s,%s,%s,%s",organizationFiscalCode,noticeNumber,dateFrom,dateTo);

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> reStorageEvents = reTableService.findReByCiAndNN(dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, noticeNumber);

        Map<String, List<EventEntity>> reGroups = reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getPaymentToken));

        log.infof("found %d different tokens",reGroups.size());

        List<BasePaymentInfo> collect = reGroups.keySet().stream().map(paymentToken->{
            List<EventEntity> events = reGroups.get(paymentToken);
            EventEntity lastEvent = events.get(events.size()-1);
            PaymentInfo pi = eventToPaymentInfo(config,lastEvent);

            Optional<Count> pos = positiveBizClient.countEventsByCiAndNN(
                    lastEvent.getIdDominio(),
                    lastEvent.getNoticeNumber(),
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent() && pos.get().getCount()>0){
                pi.setOutcome(outcomeOK);
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndNNAndToken(
                        lastEvent.getIdDominio(),
                        lastEvent.getNoticeNumber(),
                        paymentToken,
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        pi.setOutcome(outcomeKO);
                    }
                }
            }
            return pi;
        }).collect(Collectors.toList());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
                .payments(collect)
            .build();

    }

    public TransactionResponse getInfoByIUV(String organizationFiscalCode,
                                                String iuv,
                                                LocalDate dateFrom,
                                                LocalDate dateTo){

        log.infof("getInfoByIUV %s,%s,%s,%s",organizationFiscalCode,iuv,dateFrom,dateTo);

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> reStorageEvents = reTableService.findReByCiAndIUV(dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, iuv);

        Map<String, List<EventEntity>> reGroups = reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getCcp));

        log.infof("found %d different ccps",reGroups.size());

        List<BasePaymentInfo> collect = reGroups.keySet().stream().map(ccp->{
            List<EventEntity> events = reGroups.get(ccp);
            EventEntity lastEvent = events.get(events.size()-1);
            PaymentInfo pi = eventToPaymentInfo(config,lastEvent);

            Optional<Count> pos = positiveBizClient.countEventsByCiAndIUV(
                    lastEvent.getIdDominio(),
                    lastEvent.getIuv(),
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent() && pos.get().getCount()>0){
                pi.setOutcome(outcomeOK);
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndIUVAndCCP(
                        lastEvent.getIdDominio(),
                        lastEvent.getIuv(),
                        ccp,
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        pi.setOutcome(outcomeKO);
                    }
                }
            }

            return pi;
        }).collect(Collectors.toList());

        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .payments(collect)
                .build();

    }

    public TransactionResponse getAttemptByNoticeNumberAndPaymentToken(String organizationFiscalCode, String noticeNumber, String paymentToken, LocalDate dateFrom, LocalDate dateTo) {

        log.infof("getInfoByNoticeNumberAndPaymentToken %s,%s,%s,%s,%s",organizationFiscalCode,noticeNumber,paymentToken,dateFrom,dateTo);

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        List<EventEntity> events = reTableService.findReByCiAndNNAndToken(dateRequest.getFrom(),dateRequest.getTo(), organizationFiscalCode, noticeNumber,paymentToken);
        List<BasePaymentInfo> pais = new ArrayList<>();
        if(events.size()>0){
            EventEntity lastEvent = events.get(events.size()-1);
            ConfigDataV1 config = configObject.getClonedCache();
            PaymentAttemptInfo pai = eventToPaymentAttemptInfo(config,lastEvent);

            Optional<PositiveBizEvent> pos = positiveBizClient.findEventsByCiAndNNAndToken(
                    organizationFiscalCode,
                    noticeNumber,
                    paymentToken,
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent()){
                pai.setOutcome(outcomeOK);
                enrichPaymentAttemptInfo(config,pai,pos.get());
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndNNAndToken(
                        organizationFiscalCode,
                        noticeNumber,
                        paymentToken,
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        pai.setOutcome(outcomeKO);
                    }
                    enrichPaymentAttemptInfo(config,pai,neg.get());
                }
            }
            pais.add(pai);
        }


        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .payments(pais)
                .build();
    }



    public TransactionResponse getAttemptByIUVAndCCP(String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {

        log.infof("getAttemptByIUVAndCCP %s,%s,%s,%s,%s",organizationFiscalCode,iuv,ccp,dateFrom,dateTo);

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> events = reTableService.findReByCiAndIUVAndCCP(dateRequest.getFrom(),dateRequest.getTo(), organizationFiscalCode, iuv,ccp);
        List<BasePaymentInfo> pais = new ArrayList<>();
        if(events.size()>0) {
            EventEntity lastEvent = events.get(events.size() - 1);
            PaymentAttemptInfo pai = eventToPaymentAttemptInfo(config, lastEvent);
            String outcome = null;

            Optional<PositiveBizEvent> pos = positiveBizClient.findEventsByCiAndIUVAndCCP(
                    organizationFiscalCode,
                    iuv,
                    ccp,
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if (pos.isPresent()) {
                pai.setOutcome(outcomeOK);
                enrichPaymentAttemptInfo(config, pai, pos.get());
            } else {
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndIUVAndCCP(
                        organizationFiscalCode,
                        iuv,
                        ccp,
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if (neg.isPresent()) {
                    if (!neg.get().getReAwakable()) {
                        pai.setOutcome(outcomeKO);
                    }
                    enrichPaymentAttemptInfo(config, pai, neg.get());
                }
            }
            pais.add(pai);
        }

        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .payments(pais)
                .build();
    }

    /**
     * Check dates validity
     * @param dateFrom
     * @param dateTo
     */
    private DateRequest verifyDate(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo != null || dateFrom != null && dateTo == null) {
            throw new AppException(AppErrorCodeMessageEnum.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from and date to must be both defined");
        }
        else if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new AppException(AppErrorCodeMessageEnum.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from must be before date to");
        }
        if (dateFrom == null && dateTo == null) {
            dateTo = LocalDate.now();
            dateFrom = dateTo.minusDays(10);
        }
        return DateRequest.builder()
                .from(dateFrom)
                .to(dateTo)
                .build();
    }

}
