package it.gov.pagopa.nodetsworker.observability.repository;

import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

/** Strict, read-only snapshot comparison; deliberately separate from authoritative sync writes. */
@ApplicationScoped
public class OnlineDbCheckRepository {

  private static final String LOGICAL_SQL = """
      SELECT status FROM online.position_status_snapshot
      WHERE pa_fiscal_code = ? AND notice_id = ?
      LIMIT 2
      """;
  private static final String ATTEMPT_SQL = """
      SELECT status FROM online.position_payment_status_snapshot
      WHERE pa_fiscal_code = ? AND notice_id = ? AND payment_token = ?
      LIMIT 2
      """;

  public States read(DataSource source, String paFiscalCode, String noticeNumber, String paymentToken)
      throws SQLException {
    try (Connection connection = source.getConnection()) {
      connection.setReadOnly(true);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setAutoCommit(false);
      try {
        String logical = state(connection, LOGICAL_SQL, false, paFiscalCode, noticeNumber);
        String attempt = state(connection, ATTEMPT_SQL, true, paFiscalCode, noticeNumber, paymentToken);
        connection.commit();
        return new States(logical, attempt);
      } catch (SQLException | InvalidSnapshotException failure) {
        try {
          connection.rollback();
        } catch (SQLException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        throw failure;
      }
    }
  }

  private String state(Connection connection, String sql, boolean attempt, String... keys)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(5);
      statement.setMaxRows(2);
      for (int i = 0; i < keys.length; i++) {
        statement.setString(i + 1, keys[i]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          return null;
        }
        String value = rows.getString("status");
        if (!(attempt ? AttemptState.isValid(value) : LogicalState.isValid(value)) || rows.next()) {
          throw new InvalidSnapshotException();
        }
        return value;
      }
    }
  }

  public record States(String logicalState, String attemptState) {}

  public static class InvalidSnapshotException extends RuntimeException {
    public InvalidSnapshotException() {
      super("Nodo Online snapshot is invalid or ambiguous.");
    }
  }
}
