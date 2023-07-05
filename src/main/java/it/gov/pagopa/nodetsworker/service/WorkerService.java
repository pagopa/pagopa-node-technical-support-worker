package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.Config;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.*;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.ReTableService;
import it.gov.pagopa.nodetsworker.repository.model.Count;
import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.model.PositiveBizEvent;
import it.gov.pagopa.nodetsworker.service.mapper.EventMapper;
import it.gov.pagopa.nodetsworker.util.StatusUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.openapi.quarkus.api_config_cache_json.model.ConfigDataV1;

import java.time.LocalDate;
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
        pai.setBrokerPspId(pbe.getPsp().getIdBrokerPsp());
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
        pai.setBrokerPspId(nbe.getPsp().getIdBrokerPsp());
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

        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> reStorageEvents = reTableService.findReByCiAndNN(dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, noticeNumber);

        Map<String, List<EventEntity>> reGroups = reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getPaymentToken));

        List<BasePaymentInfo> collect = reGroups.keySet().stream().map(gkey->{
            List<EventEntity> events = reGroups.get(gkey);
            EventEntity lastEvent = events.get(events.size()-1);
            String outcome = null;

            Optional<Count> pos = positiveBizClient.countEventsByCiAndNN(
                    lastEvent.getIdDominio(),
                    lastEvent.getNoticeNumber(),
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent() && pos.get().getCount()>0){
                outcome = outcomeOK;
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndNN(
                        lastEvent.getIdDominio(),
                        lastEvent.getNoticeNumber(),
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        outcome = outcomeKO;
                    }
                }
            }
            PaymentInfo pi = eventToPaymentInfo(config,lastEvent);
            pi.setOutcome(outcome);
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
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> reStorageEvents = reTableService.findReByCiAndIUV(dateRequest.getFrom(), dateRequest.getTo(), organizationFiscalCode, iuv);

        Map<String, List<EventEntity>> reGroups = reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getCcp));

        List<BasePaymentInfo> collect = reGroups.keySet().stream().map(gkey->{
            List<EventEntity> events = reGroups.get(gkey);
            EventEntity lastEvent = events.get(events.size()-1);
            String outcome = null;

            Optional<Count> pos = positiveBizClient.countEventsByCiAndIUV(
                    lastEvent.getIdDominio(),
                    lastEvent.getIuv(),
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent() && pos.get().getCount()>0){
                outcome = outcomeOK;
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndIUV(
                        lastEvent.getIdDominio(),
                        lastEvent.getIuv(),
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        outcome = outcomeKO;
                    }
                }
            }

            PaymentInfo pi = eventToPaymentInfo(config,lastEvent);
            pi.setOutcome(outcome);
            return pi;
        }).collect(Collectors.toList());

        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .payments(collect)
                .build();

    }

    public TransactionResponse getInfoByNoticeNumberAndPaymentToken(String organizationFiscalCode, String noticeNumber, String paymentToken, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> reStorageEvents = reTableService.findReByCiAndNNAndToken(dateRequest.getFrom(),
                dateRequest.getTo(), organizationFiscalCode, noticeNumber,paymentToken);

        Map<String, List<EventEntity>> reGroups = reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getPaymentToken));
        List<BasePaymentInfo> collect = reGroups.keySet().stream().map(gkey->{
            List<EventEntity> events = reGroups.get(gkey);
            EventEntity lastEvent = events.get(events.size()-1);
            PaymentAttemptInfo pi = eventToPaymentAttemptInfo(config,lastEvent);
            String outcome = null;

            Optional<PositiveBizEvent> pos = positiveBizClient.findEventsByCiAndIUVAndCCP(
                    lastEvent.getIdDominio(),
                    lastEvent.getIuv(),
                    lastEvent.getCcp(),
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent()){
                outcome = outcomeOK;
                pi.setOutcome(outcome);
                enrichPaymentAttemptInfo(config,pi,pos.get());
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndNNAndToken(
                        lastEvent.getIdDominio(),
                        lastEvent.getIuv(),
                        lastEvent.getPaymentToken(),
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        outcome = outcomeKO;
                    }
                    enrichPaymentAttemptInfo(config,pi,neg.get());
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



    public TransactionResponse getAttemptByIUVAndCCP(String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);
        ConfigDataV1 config = configObject.getClonedCache();
        List<EventEntity> reStorageEvents = reTableService.findReByCiAndIUVAndCCP(dateRequest.getFrom(),
                dateRequest.getTo(), organizationFiscalCode, iuv,ccp);

        Map<String, List<EventEntity>> reGroups = reStorageEvents.stream().collect(Collectors.groupingBy(EventEntity::getCcp));

        List<BasePaymentInfo> collect = reGroups.keySet().stream().map(gkey->{
            List<EventEntity> events = reGroups.get(gkey);
            EventEntity lastEvent = events.get(events.size()-1);
            PaymentAttemptInfo pi = eventToPaymentAttemptInfo(config,lastEvent);
            String outcome = null;

            Optional<PositiveBizEvent> pos = positiveBizClient.findEventsByCiAndIUVAndCCP(
                    lastEvent.getIdDominio(),
                    lastEvent.getIuv(),
                    lastEvent.getCcp(),
                    dateRequest.getFrom(),
                    dateRequest.getTo()
            ).stream().findFirst();
            if(pos.isPresent()){
                outcome = outcomeOK;
                pi.setOutcome(outcome);
                enrichPaymentAttemptInfo(config,pi,pos.get());
            }else{
                Optional<NegativeBizEvent> neg = negativeBizClient.findEventsByCiAndIUVAndCCP(
                        lastEvent.getIdDominio(),
                        lastEvent.getIuv(),
                        lastEvent.getPaymentToken(),
                        dateRequest.getFrom(),
                        dateRequest.getTo()
                ).stream().findFirst();
                if(neg.isPresent()){
                    if(!neg.get().getReAwakable()){
                        outcome = outcomeKO;
                    }
                    enrichPaymentAttemptInfo(config,pi,neg.get());
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
