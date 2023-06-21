package it.gov.pagopa.nodetsworker.service;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.nodetsworker.exceptions.AppError;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.BasePaymentInfo;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.models.TransactionResponse;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.EventEntity;
import it.gov.pagopa.nodetsworker.repository.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.PositiveBizEvent;
import it.gov.pagopa.nodetsworker.service.mapper.EventMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
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


    public TransactionResponse getInfoByNoticeNumber(String organizationFiscalCode,
                                                     String noticeNumber,
                                                     LocalDate dateFrom,
                                                     LocalDate dateTo){

        List<EventEntity>      list = EventEntity.findByCIAndNAV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
//        List<PositiveBizEvent> biz  = PositiveBizEvent.findByCIAndNAV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
//        List<NegativeBizEvent> neg  = NegativeBizEvent.findByCIAndNAV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();

        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@noticeNumber", noticeNumber));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where " +
                "c.creditor.idPA = @organizationFiscalCode " //+
//                "and c.debtorPosition.noticeNumber = @noticeNumber"// +
//                "and c.paymentInfo.paymentDateTime > @from " +
//                "and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);

        CosmosPagedIterable<PositiveBizEvent> ax = positiveBizClient.query(q);
        List<BasePaymentInfo> collect = ax.stream().map(s->{
            return BasePaymentInfo.builder()
//                    .brokerPspId()
//                    .pspId()
//                    .nodeId()
//                    .channelId()
//                    .brokerPspId()
                    .noticeNumber(s.getDebtorPosition()!=null?s.getDebtorPosition().getNoticeNumber():"").build();
//                    .organizationFiscalCode();
        }).collect(Collectors.toList());
        return TransactionResponse.builder()
                .payments(collect)
            .build();

    }

    public TransactionResponse getInfoByIUV(String organizationFiscalCode,
                                                String noticeNumber,
                                                LocalDate dateFrom,
                                                LocalDate dateTo){

        List<EventEntity>      list = EventEntity.findByCIAndIUV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
        List<PositiveBizEvent> biz  = PositiveBizEvent.findByCIAndIUV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();
        List<NegativeBizEvent> neg  = NegativeBizEvent.findByCIAndIUV(organizationFiscalCode, noticeNumber,dateFrom,dateTo).list();

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
            throw new AppException(AppError.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from and date to must be both defined");
        }
        else if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new AppException(AppError.POSITION_SERVICE_DATE_BAD_REQUEST, "Date from must be before date to");
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
