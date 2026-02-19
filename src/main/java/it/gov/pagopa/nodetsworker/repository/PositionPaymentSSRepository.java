package it.gov.pagopa.nodetsworker.repository;

import java.util.List;
import java.util.Optional;

import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;
import it.gov.pagopa.nodetsworker.repository.qualifiers.DefaultNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
@DefaultNode
public class PositionPaymentSSRepository implements PositionPaymentSnapshotReader{

	@PersistenceContext
	EntityManager em;

	@Override
	public Optional<PositionPaymentSSEntity> findByPaymentToken(String paymentToken) {
		List<PositionPaymentSSEntity> res = em.createQuery("""
				    SELECT p
				    FROM PositionPaymentSSEntity p
				    WHERE p.paymentToken = :token
				    ORDER BY p.id DESC
				""", PositionPaymentSSEntity.class)
				.setParameter("token", paymentToken)
				.setMaxResults(1)
				.getResultList();

		return res.stream().findFirst();
	}
}
