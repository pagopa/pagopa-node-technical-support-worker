package it.gov.pagopa.nodetsworker.service;

import io.micrometer.common.util.StringUtils;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.repository.model.PositionPaymentSSEntity;
import it.gov.pagopa.nodetsworker.resources.mapper.SnapshotMapper;
import it.gov.pagopa.nodetsworker.resources.response.Metadata;
import it.gov.pagopa.nodetsworker.resources.response.PaymentResponse;
import it.gov.pagopa.nodetsworker.util.AppDBUtil;
import it.gov.pagopa.nodetsworker.util.ValidationUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.Serializable;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Transactional
public class SnapshotService implements Serializable {

    private final SnapshotMapper snapshotMapper;

    @ConfigProperty(name = "date-range-limit")
    Integer dateRangeLimit;

    @ConfigProperty(name = "db.serviceIdentifier")
    String dbServiceIdentifier;

    @PersistenceContext
    EntityManager em;

    public SnapshotService(SnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    public PaymentResponse getPosPaymentStatusSnapshot(String paFiscalCode, String noticeNumber,
                                                                   String paymentToken, LocalDate dateFrom, LocalDate dateTo,
                                                                   long pageNumber, long pageSize) {
        DateRequest dateRequest = ValidationUtil.verifyDateRequest(dateFrom, dateTo, dateRangeLimit);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<PositionPaymentSSEntity> cr = cb.createQuery(PositionPaymentSSEntity.class);
        Root<PositionPaymentSSEntity> root = cr.from(PositionPaymentSSEntity.class);
        CriteriaQuery<PositionPaymentSSEntity> select = cr.select(root);

        List<Predicate> conditionsList = new ArrayList<>();
        Predicate onStart = cb.greaterThanOrEqualTo(root.get("insertedTimestamp").as(Date.class), Date.valueOf(dateRequest.getFrom()));
        Predicate onEnd = cb.lessThanOrEqualTo(root.get("insertedTimestamp").as(Date.class), Date.valueOf(dateRequest.getTo()));
        conditionsList.add(onStart);
        conditionsList.add(onEnd);
        conditionsList.add(cb.equal(root.get("paFiscalCode"), paFiscalCode));
        if(StringUtils.isNotEmpty(noticeNumber)) {
            conditionsList.add(cb.equal(root.get("noticeId"), noticeNumber));
        }
        if(StringUtils.isNotEmpty(paymentToken)) {
            conditionsList.add(cb.equal(root.get("paymentToken"), paymentToken));
        }

        select.where(conditionsList.toArray(new Predicate[]{}));

        select.orderBy(cb.asc(root.get("id")));

        int count = em.createQuery(select).getResultList().size();
        long newPageNumber = pageNumber - 1;
        long totPage = AppDBUtil.getPageCount(count, (int) pageSize);

        TypedQuery<PositionPaymentSSEntity> typedQuery = em.createQuery(select);
        typedQuery.setFirstResult((int) (newPageNumber));
        typedQuery.setMaxResults((int) pageSize);

        List<PositionPaymentSSEntity> positionPaymentSSEntityList = typedQuery.getResultList();

        return PaymentResponse
                .builder()
                .count(count)
                .metadata(
                        Metadata.builder()
                                .pageNumber((int) pageNumber)
                                .pageSize((int) pageSize)
                                .totPage((int) totPage)
                                .build()
                )
                .data(snapshotMapper.toPositionPaymentSSInfoList(positionPaymentSSEntityList))
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
    }

}
