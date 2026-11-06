package it.gov.pagopa.nodetsworker.observability.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlineStatusRepositoryTest {

  @Inject
  @io.quarkus.agroal.DataSource("online-ndp001")
  DataSource dataSource;

  @Inject
  OnlineStatusRepository repository;

  @BeforeEach
  void createSchemaAndSeedSnapshots() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE SCHEMA IF NOT EXISTS online");
        statement.execute("DROP TABLE IF EXISTS online.position_status_snapshot");
        statement.execute("DROP TABLE IF EXISTS online.position_payment_status_snapshot");
        statement.execute("""
            CREATE TABLE online.position_status_snapshot (
              id BIGINT PRIMARY KEY,
              pa_fiscal_code VARCHAR(16) NOT NULL,
              notice_id VARCHAR(32) NOT NULL,
              status VARCHAR(64) NOT NULL,
              inserted_timestamp TIMESTAMP NOT NULL,
              updated_timestamp TIMESTAMP
            )
            """);
        statement.execute("""
            CREATE TABLE online.position_payment_status_snapshot (
              id BIGINT PRIMARY KEY,
              pa_fiscal_code VARCHAR(16) NOT NULL,
              notice_id VARCHAR(32) NOT NULL,
              payment_token VARCHAR(128) NOT NULL,
              status VARCHAR(64) NOT NULL,
              inserted_timestamp TIMESTAMP NOT NULL,
              updated_timestamp TIMESTAMP
            )
            """);
      }

      insertPayment(connection, 1, "PA-1", "NOTICE-1", "EARLIER-UPDATE",
          "2026-01-01T13:00:00", "2026-01-01T10:00:00");
      insertPayment(connection, 2, "PA-1", "NOTICE-1", "TIED-LOW-ID",
          "2026-01-01T10:30:00", "2026-01-01T11:00:00");
      insertPayment(connection, 3, "PA-1", "NOTICE-1", "TIED-LOWER-ID",
          "2026-01-01T11:00:00", "2026-01-01T11:00:00");
      insertPayment(connection, 4, "PA-1", "NOTICE-1", "LATEST",
          "2026-01-01T11:00:00", "2026-01-01T11:00:00");
      insertPayment(connection, 8, "PA-1", "NOTICE-1", "NULL-UPDATE",
          "2026-01-01T15:00:00", null);
      insertPayment(connection, 9, "OTHER-PA", "NOTICE-1", "WRONG-FISCAL-CODE",
          "2026-01-01T16:00:00", "2026-01-01T16:00:00");
      insertPayment(connection, 10, "PA-1", "OTHER-NOTICE", "WRONG-NOTICE",
          "2026-01-01T17:00:00", "2026-01-01T17:00:00");

      insertAttempt(connection, 1, "PA-1", "NOTICE-1", "TOKEN-1", "EARLIER-UPDATE",
          "2026-01-01T13:00:00", "2026-01-01T10:00:00");
      insertAttempt(connection, 2, "PA-1", "NOTICE-1", "TOKEN-1", "TIED-LOW-ID",
          "2026-01-01T10:30:00", "2026-01-01T11:00:00");
      insertAttempt(connection, 3, "PA-1", "NOTICE-1", "TOKEN-1", "TIED-LOWER-ID",
          "2026-01-01T11:00:00", "2026-01-01T11:00:00");
      insertAttempt(connection, 4, "PA-1", "NOTICE-1", "TOKEN-1", "LATEST",
          "2026-01-01T11:00:00", "2026-01-01T11:00:00");
      insertAttempt(connection, 8, "PA-1", "NOTICE-1", "TOKEN-1", "NULL-UPDATE",
          "2026-01-01T15:00:00", null);
      insertAttempt(connection, 9, "OTHER-PA", "NOTICE-1", "TOKEN-1", "WRONG-FISCAL-CODE",
          "2026-01-01T16:00:00", "2026-01-01T16:00:00");
      insertAttempt(connection, 10, "PA-1", "OTHER-NOTICE", "TOKEN-1", "WRONG-NOTICE",
          "2026-01-01T17:00:00", "2026-01-01T17:00:00");
      insertAttempt(connection, 11, "PA-1", "NOTICE-1", "OTHER-TOKEN", "WRONG-TOKEN",
          "2026-01-01T18:00:00", "2026-01-01T18:00:00");
    }
  }

  @Test
  void findsLatestLogicalSnapshotUsingFiscalCodeAndNoticeId() throws SQLException {
    Optional<OnlineStatusSnapshot> result = repository.findPaymentStatus("PA-1", "NOTICE-1");

    assertEquals("LATEST", result.orElseThrow().state());
    assertEquals(LocalDateTime.parse("2026-01-01T11:00:00"),
        result.orElseThrow().insertedTimestamp());
    assertEquals(LocalDateTime.parse("2026-01-01T11:00:00"),
        result.orElseThrow().updatedTimestamp());
  }

  @Test
  void findsLatestAttemptSnapshotUsingAllThreeKeys() throws SQLException {
    Optional<OnlineStatusSnapshot> result =
        repository.findAttemptStatus("PA-1", "NOTICE-1", "TOKEN-1");

    assertEquals("LATEST", result.orElseThrow().state());
    assertEquals(LocalDateTime.parse("2026-01-01T11:00:00"),
        result.orElseThrow().insertedTimestamp());
    assertEquals(LocalDateTime.parse("2026-01-01T11:00:00"),
        result.orElseThrow().updatedTimestamp());
  }

  @Test
  void returnsEmptyWhenTheLogicalSnapshotDoesNotExist() throws SQLException {
    assertFalse(repository.findPaymentStatus("MISSING", "NOTICE").isPresent());
  }

  @Test
  void propagatesSqlFailuresInsteadOfReturningAnEmptySnapshot() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("DROP TABLE online.position_status_snapshot");
      }
    }

    assertThrows(SQLException.class, () -> repository.findPaymentStatus("PA-1", "NOTICE-1"));
  }

  private static void insertPayment(
      Connection connection,
      long id,
      String fiscalCode,
      String noticeId,
      String status,
      String insertedTimestamp,
      String updatedTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        INSERT INTO online.position_status_snapshot
          (id, pa_fiscal_code, notice_id, status, inserted_timestamp, updated_timestamp)
        VALUES (?, ?, ?, ?, ?, ?)
        """)) {
      statement.setLong(1, id);
      statement.setString(2, fiscalCode);
      statement.setString(3, noticeId);
      statement.setString(4, status);
      statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.parse(insertedTimestamp)));
      setTimestamp(statement, 6, updatedTimestamp);
      statement.executeUpdate();
    }
  }

  private static void insertAttempt(
      Connection connection,
      long id,
      String fiscalCode,
      String noticeId,
      String paymentToken,
      String status,
      String insertedTimestamp,
      String updatedTimestamp) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
        INSERT INTO online.position_payment_status_snapshot
          (id, pa_fiscal_code, notice_id, payment_token, status, inserted_timestamp, updated_timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """)) {
      statement.setLong(1, id);
      statement.setString(2, fiscalCode);
      statement.setString(3, noticeId);
      statement.setString(4, paymentToken);
      statement.setString(5, status);
      statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.parse(insertedTimestamp)));
      setTimestamp(statement, 7, updatedTimestamp);
      statement.executeUpdate();
    }
  }

  private static void setTimestamp(PreparedStatement statement, int index, String value)
      throws SQLException {
    if (value == null) {
      statement.setTimestamp(index, null);
    } else {
      statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.parse(value)));
    }
  }
}
