package it.gov.pagopa.nodetsworker.observability.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineDbCheckRepositoryTest {
  private final DataSource source = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement logical = mock(PreparedStatement.class);
  private final PreparedStatement attempt = mock(PreparedStatement.class);
  private final ResultSet logicalRows = mock(ResultSet.class);
  private final ResultSet attemptRows = mock(ResultSet.class);
  private final OnlineDbCheckRepository repository = new OnlineDbCheckRepository();

  @BeforeEach
  void prepare() throws SQLException {
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(contains("online.position_status_snapshot"))).thenReturn(logical);
    when(connection.prepareStatement(contains("online.position_payment_status_snapshot"))).thenReturn(attempt);
    when(logical.executeQuery()).thenReturn(logicalRows);
    when(attempt.executeQuery()).thenReturn(attemptRows);
    when(logicalRows.next()).thenReturn(true, false);
    when(attemptRows.next()).thenReturn(true, false);
    when(logicalRows.getString("status")).thenReturn("PAYING");
    when(attemptRows.getString("status")).thenReturn("PAYMENT_UNKNOWN");
  }

  @Test
  void readsBothStatesWithCompleteBoundKeysInOneReadOnlySnapshot() throws SQLException {
    var result = repository.read(source, "00000000001", "300000000000000001", "token'?");
    assertEquals("PAYING", result.logicalState());
    assertEquals("PAYMENT_UNKNOWN", result.attemptState());
    var order = inOrder(connection, logical, attempt);
    order.verify(connection).setReadOnly(true);
    order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    order.verify(connection).setAutoCommit(false);
    for (PreparedStatement statement : new PreparedStatement[]{logical, attempt}) {
      verify(statement).setString(1, "00000000001");
      verify(statement).setString(2, "300000000000000001");
      verify(statement).setQueryTimeout(5);
      verify(statement).setMaxRows(2);
      verify(statement).close();
    }
    verify(attempt).setString(3, "token'?");
    verify(source, times(1)).getConnection();
    verify(connection).commit();
    verify(connection, never()).rollback();
    verify(connection).close();
    verify(logicalRows).close();
    verify(attemptRows).close();
  }

  @Test
  void representsMissingRowsIndependentlyWithoutInferringStates() throws SQLException {
    when(logicalRows.next()).thenReturn(false);
    var result = repository.read(source, "pa", "notice", "token");
    assertNull(result.logicalState());
    assertEquals("PAYMENT_UNKNOWN", result.attemptState());
  }

  @Test
  void allowsBothRowsToBeAbsent() throws SQLException {
    when(logicalRows.next()).thenReturn(false);
    when(attemptRows.next()).thenReturn(false);
    var result = repository.read(source, "pa", "notice", "token");
    assertNull(result.logicalState());
    assertNull(result.attemptState());
  }

  @Test
  void rejectsDuplicateLogicalSnapshotsEvenWhenStatesMatch() throws SQLException {
    when(logicalRows.next()).thenReturn(true, true);
    assertThrows(OnlineDbCheckRepository.InvalidSnapshotException.class,
        () -> repository.read(source, "pa", "notice", "token"));
    verify(connection).rollback();
    verify(connection, never()).commit();
  }

  @Test
  void rejectsDuplicateAttemptSnapshots() throws SQLException {
    when(attemptRows.next()).thenReturn(true, true);
    assertThrows(OnlineDbCheckRepository.InvalidSnapshotException.class,
        () -> repository.read(source, "pa", "notice", "token"));
    verify(connection).rollback();
  }

  @Test
  void rejectsNullOrWrongEntityStates() throws SQLException {
    when(logicalRows.getString("status")).thenReturn("CANCELLED");
    assertThrows(OnlineDbCheckRepository.InvalidSnapshotException.class,
        () -> repository.read(source, "pa", "notice", "token"));
    when(logicalRows.getString("status")).thenReturn(null);
    when(logicalRows.next()).thenReturn(true, false);
    assertThrows(OnlineDbCheckRepository.InvalidSnapshotException.class,
        () -> repository.read(source, "pa", "notice", "token"));
  }

  @Test
  void failsTheWholeReadWhenTheSecondQueryFails() throws SQLException {
    when(attempt.executeQuery()).thenThrow(new SQLException("sensitive details"));
    assertThrows(SQLException.class, () -> repository.read(source, "pa", "notice", "token"));
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).close();
  }
}
