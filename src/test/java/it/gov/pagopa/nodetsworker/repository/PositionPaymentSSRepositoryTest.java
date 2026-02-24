package it.gov.pagopa.nodetsworker.repository;

import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PositionPaymentSSRepositoryTest {

    private PositionPaymentSSRepository repo;
    private EntityManager em;
    private TypedQuery<PositionPaymentSSEntity> query;

    @SuppressWarnings("unchecked")
	@BeforeEach
    void setup() throws Exception {
        repo = new PositionPaymentSSRepository();
        em = mock(EntityManager.class);
        query = mock(TypedQuery.class);

        setField(repo, "em", em);

        when(em.createQuery(anyString(), eq(PositionPaymentSSEntity.class))).thenReturn(query);
        when(query.setParameter(eq("token"), any())).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
    }

    @Test
    void findByPaymentToken_shouldReturnEmpty_whenNoRows() {
        when(query.getResultList()).thenReturn(List.of());

        Optional<PositionPaymentSSEntity> res = repo.findByPaymentToken("pt-1");

        assertTrue(res.isEmpty());

        verify(em).createQuery(anyString(), eq(PositionPaymentSSEntity.class));
        verify(query).setParameter("token", "pt-1");
        verify(query).setMaxResults(1);
        verify(query).getResultList();
        verifyNoMoreInteractions(em, query);
    }

    @Test
    void findByPaymentToken_shouldReturnFirst_whenHasRow() {
        PositionPaymentSSEntity e = new PositionPaymentSSEntity();
        when(query.getResultList()).thenReturn(List.of(e));

        Optional<PositionPaymentSSEntity> res = repo.findByPaymentToken("pt-2");

        assertTrue(res.isPresent());
        assertSame(e, res.get());

        verify(em).createQuery(anyString(), eq(PositionPaymentSSEntity.class));
        verify(query).setParameter("token", "pt-2");
        verify(query).setMaxResults(1);
        verify(query).getResultList();
        verifyNoMoreInteractions(em, query);
    }

    @Test
    void findByPaymentToken_shouldUseExpectedJpqlShape() {
        when(query.getResultList()).thenReturn(List.of());

        repo.findByPaymentToken("pt-3");

        verify(em).createQuery(argThat(jpql ->
                        jpql.contains("FROM PositionPaymentSSEntity") &&
                        jpql.contains("p.paymentToken = :token") &&
                        jpql.contains("ORDER BY p.id DESC")
                ),
                eq(PositionPaymentSSEntity.class)
        );
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}