package it.gov.pagopa.nodetsworker.observability.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.observability.domain.DbCheckContext;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import it.gov.pagopa.nodetsworker.observability.repository.OnlineDbCheckRepository;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.service.NodeSnapshotRouter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbCheckServiceTest {
  private final ProjectionRepository projection = mock(ProjectionRepository.class);
  private final NodeSnapshotRouter router = mock(NodeSnapshotRouter.class);
  private final OnlineDbCheckRepository online = mock(OnlineDbCheckRepository.class);
  private final DataSource source = mock(DataSource.class);
  private final DbCheckService service = new DbCheckService();
  private final Caller viewer = new Caller("viewer", "viewer", "00000000001", false, false);

  @BeforeEach
  void prepare() throws SQLException {
    service.projection = projection;
    service.router = router;
    service.online = online;
    when(projection.findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class)))
        .thenReturn(Optional.of(context(7, "ndp001")));
    when(router.resolveDataSource("ndp001")).thenReturn(source);
    when(online.read(source, "00000000001", "300000000000000001", "private-token"))
        .thenReturn(new OnlineDbCheckRepository.States("PAID", "NOTIFIED"));
  }

  @Test
  void allowsReadersWithoutTokenPrivilegesAndRetainsCapturedRevisionWhenProjectionAdvances() {
    when(projection.findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class)))
        .thenReturn(Optional.of(context(7, "ndp001")), Optional.of(context(8, "ndp001")));
    var result = service.check(viewer, "id");
    assertEquals(7, result.getProjectionRevision());
    assertEquals("NODO_ONLINE", result.getSource());
    assertEquals("PAYING", result.getObserved().getLogicalState().name());
    verify(projection, times(2)).findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class));
    verifyNoMoreInteractions(projection);
  }

  @Test
  void rejectsAnAttemptThatExpiresOrDisappearsDuringTheDatabaseRead() {
    when(projection.findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class)))
        .thenReturn(Optional.of(context(7, "ndp001")), Optional.empty());
    assertEquals(404, assertThrows(ObservabilityException.class, () -> service.check(viewer, "id")).getStatus());
  }

  @Test
  void rejectsAServiceChangeDuringTheDatabaseReadRatherThanMisattributingTheResult() {
    when(projection.findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class)))
        .thenReturn(Optional.of(context(7, "ndp001")), Optional.of(context(8, "ndp004")));
    assertEquals(500, assertThrows(ObservabilityException.class, () -> service.check(viewer, "id")).getStatus());
  }

  @Test
  void missingOrHiddenAttemptsNeverAcquireADatasource() {
    when(projection.findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class)))
        .thenReturn(Optional.empty());
    assertEquals(404, assertThrows(ObservabilityException.class, () -> service.check(viewer, "id")).getStatus());
    verifyNoInteractions(router, online, source);
  }

  @Test
  void unknownServiceIsExplicitAndDoesNotFallbackToAnotherNode() {
    when(router.resolveDataSource("ndp001")).thenThrow(new AppException(AppErrorCodeMessageEnum.BAD_REQUEST));
    var failure = assertThrows(ObservabilityException.class, () -> service.check(viewer, "id"));
    assertEquals(422, failure.getStatus());
    assertEquals("DB_SERVICE_UNCONFIGURED", failure.toBody().getCode());
    verifyNoInteractions(online, source);
  }

  @Test
  void sanitizesSqlFailuresAndRejectsIncompleteProjectionState() throws SQLException {
    when(online.read(any(), anyString(), anyString(), anyString()))
        .thenThrow(new SQLException("private-token jdbc:postgresql://private password"));
    var failure = assertThrows(ObservabilityException.class, () -> service.check(viewer, "id"));
    assertEquals(503, failure.getStatus());
    assertEquals("DB_UNAVAILABLE", failure.toBody().getCode());
    assertFalse(failure.getMessage().contains("private"));
    assertNull(failure.getCause());
  }

  @Test
  void incompleteProjectionDoesNotTriggerSourceQueriesOrInventAnObservedState() {
    var context = context(7, "ndp001");
    when(projection.findDbCheckContext(eq("id"), eq(viewer.allowedPa()), any(Instant.class)))
        .thenReturn(Optional.of(new DbCheckContext(context.attemptId(), context.paFiscalCode(),
            context.noticeNumber(), context.paymentToken(), context.serviceIdentifier(), null,
            context.attemptState(), context.projectionRevision(), context.expiresAt())));
    assertEquals(500, assertThrows(ObservabilityException.class, () -> service.check(viewer, "id")).getStatus());
    verifyNoInteractions(online, source);
  }

  @Test
  void contextDiagnosticStringNeverContainsTheRawToken() {
    assertFalse(context(7, "ndp001").toString().contains("private-token"));
  }

  private DbCheckContext context(long revision, String identifier) {
    return new DbCheckContext("id", "00000000001", "300000000000000001", "private-token",
        identifier, "PAYING", "PAYMENT_UNKNOWN", revision, Instant.now().plusSeconds(60));
  }
}
