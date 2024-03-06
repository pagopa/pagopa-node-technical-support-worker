package it.gov.pagopa.nodetsworker.service;

import io.micrometer.common.util.StringUtils;
import it.gov.pagopa.nodetsworker.models.DateRequest;
import it.gov.pagopa.nodetsworker.repository.model.PositionPaymentSSEntity;
import it.gov.pagopa.nodetsworker.resources.mapper.SnapshotMapper;
import it.gov.pagopa.nodetsworker.resources.response.Metadata;
import it.gov.pagopa.nodetsworker.resources.response.PaymentResponse;
import it.gov.pagopa.nodetsworker.resources.response.PositionPaymentSSInfo;
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
import org.jboss.logging.Logger;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Transactional
public class SnapshotService {

    private final Logger log;
    private final SnapshotMapper snapshotMapper;

    @ConfigProperty(name = "date-range-limit")
    Integer dateRangeLimit;

    @PersistenceContext
    EntityManager em;

    public SnapshotService(Logger log, SnapshotMapper snapshotMapper) {
        this.log = log;
        this.snapshotMapper = snapshotMapper;
    }

    public PaymentResponse getPosPaymentStatusSnapshot(String paFiscalCode, String noticeNumber,
                                                                   String paymentToken, LocalDate dateFrom, LocalDate dateTo,
                                                                   long pageNumber, long pageSize) {
        DateRequest dateRequest = ValidationUtil.verifyDateRequest(dateFrom, dateTo, dateRangeLimit);

        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb
                .createQuery(Long.class);
        countQuery.select(cb
                .count(countQuery.from(PositionPaymentSSEntity.class)));
        Long count = em.createQuery(countQuery)
                .getSingleResult();

        CriteriaQuery<PositionPaymentSSEntity> cr = cb.createQuery(PositionPaymentSSEntity.class);
        Root<PositionPaymentSSEntity> root = cr.from(PositionPaymentSSEntity.class);
        CriteriaQuery<PositionPaymentSSEntity> select = cr.select(root);

        select.where(cb.equal(root.get("paFiscalCode"), paFiscalCode));
        if(StringUtils.isNotEmpty(noticeNumber)) {
            select.where(cb.equal(root.get("noticeId"), noticeNumber));
        }
        if(StringUtils.isNotEmpty(paymentToken)) {
            select.where(cb.equal(root.get("paymentToken"), paymentToken));
        }

        List<Predicate> conditionsList = new ArrayList<>();
        Predicate onStart = cb.greaterThanOrEqualTo(root.get("insertedTimestamp").as(Date.class), Date.valueOf(dateRequest.getFrom()));
        Predicate onEnd = cb.lessThanOrEqualTo(root.get("insertedTimestamp").as(Date.class), Date.valueOf(dateRequest.getTo()));
        conditionsList.add(onStart);
        conditionsList.add(onEnd);
        select.where(conditionsList.toArray(new Predicate[]{}));

        long newPageNumber = pageNumber - 1;
        long totPage = count / pageSize;

        TypedQuery<PositionPaymentSSEntity> typedQuery = em.createQuery(select);
        typedQuery.setFirstResult((int) (pageNumber - 1));
        typedQuery.setMaxResults((int) pageSize);

        List<PositionPaymentSSEntity> maxResults = typedQuery.getResultList();

        return PaymentResponse
                .builder()
                .count(count)
                .metadata(
                        Metadata.builder()
                                .pageNumber((int) newPageNumber)
                                .pageSize((int) pageSize)
                                .totPage((int) totPage)
                                .build()
                )
                .data(snapshotMapper.toPositionPaymentSSInfo(maxResults))
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
    }

}
