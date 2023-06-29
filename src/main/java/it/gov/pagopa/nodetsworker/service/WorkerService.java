package it.gov.pagopa.nodetsworker.service;

import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.BasePaymentInfo;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.models.TransactionResponse;
import it.gov.pagopa.nodetsworker.repository.*;
import it.gov.pagopa.nodetsworker.service.mapper.EventMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkerService {

    @Inject
    Logger log;

    @Inject
    EventMapper eventsMapper;

    @Inject
    CosmosBizEventClient positiveBizClient;
    @Inject
    CosmosNegBizEventClient negativeBizClient;

    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode,
                                                     String noticeNumber,
                                                     LocalDate dateFrom,
                                                     LocalDate dateTo){

        List<EventEntity>      re = EventEntity.findByCIAndNAV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
        CosmosPagedIterable<PositiveBizEvent> pos = positiveBizClient.findEvents(organizationFiscalCode, noticeNumber,dateFrom,dateTo);
        CosmosPagedIterable<NegativeBizEvent> neg = negativeBizClient.findEvents(organizationFiscalCode, noticeNumber,dateFrom,dateTo);

        log.infov("found {0} re events\n" +
                "found {1} pos biz\n" +
                "fount {2} neg biz",re.size(),pos.stream().count(),neg.stream().count());

        String serviceIdentifier;
        if(re.size()>0){
            EventEntity lastRe = re.get(re.size()-1);
            serviceIdentifier = lastRe.getServiceIdentifier();
        } else {
            serviceIdentifier = null;
        }

        List<BasePaymentInfo> collect = pos.stream().map(s->{
            return PaymentInfo.builder()
                    .brokerPspId(s.getCreditor().getIdBrokerPA())
                    .pspId(s.getPsp().getIdPsp())
                    .nodeId(serviceIdentifier)
                    .channelId(s.getPsp().getIdChannel())
                    .brokerPspId(s.getPsp().getIdBrokerPsp())
                    .insertedTimestamp(s.getPaymentInfo().getPaymentDateTime())
                    .paymentToken(s.getPaymentInfo().getPaymentToken())
                    .noticeNumber(s.getDebtorPosition()!=null?s.getDebtorPosition().getNoticeNumber():null)
                    .organizationFiscalCode(organizationFiscalCode)
                    .build();
        }).collect(Collectors.toList());
        return TransactionResponse.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .payments(collect)
            .build();

    }

    public TransactionResponse getInfoByIUV(String organizationFiscalCode,
                                                String noticeNumber,
                                                LocalDate dateFrom,
                                                LocalDate dateTo){

        List<EventEntity>      list = EventEntity.findByCIAndIUV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
//        List<PositiveBizEvent> biz  = PositiveBizEvent.findByCIAndIUV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
//        List<NegativeBizEvent> neg  = NegativeBizEvent.findByCIAndIUV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();

        return TransactionResponse.builder()
                .build();

    }

    public TransactionResponse getAttemptByIUVPaymentToken(String organizationFiscalCode, String iuv, String paymentToken, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

//        List<PositionPayment> payments = getPaymentPositionListByIUVPaymentToken(organizationFiscalCode, iuv, paymentToken, dateRequest.getFrom(), dateRequest.getTo());
//        List<PaymentAttemptInfo> paymentAttemptInfoList = enrichPaymentAttemptPositionList(payments, dateRequest.getFrom(), dateRequest.getTo());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
//                .payments(paymentAttemptInfoList)
                .build();
    }
    public TransactionResponse getAttemptByIUVCCP(String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
        DateRequest dateRequest = verifyDate(dateFrom, dateTo);

//        List<RPT> rptList = getRPTListByIUVCCP(organizationFiscalCode, iuv, ccp, dateRequest.getFrom(), dateRequest.getTo());
//        List<RPTAttemptInfo> rptAttemptInfoList = enrichRPTAttemptList(rptList, dateRequest.getFrom(), dateRequest.getTo());

        return TransactionResponse.builder()
                .dateFrom(dateRequest.getFrom())
                .dateTo(dateRequest.getTo())
//                .payments(rptAttemptInfoList)
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
            dateFrom = LocalDate.now();
            dateTo = dateFrom.minusDays(10);
        }
        return DateRequest.builder()
                .from(dateFrom)
                .to(dateTo)
                .build();
    }

}
