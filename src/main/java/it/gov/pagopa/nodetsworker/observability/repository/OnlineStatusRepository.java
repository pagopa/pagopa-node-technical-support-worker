package it.gov.pagopa.nodetsworker.observability.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.sql.DataSource;

@ApplicationScoped
public class OnlineStatusRepository {

  private static final String PAYMENT_STATUS_SQL = """
      SELECT status, inserted_timestamp, updated_timestamp
      FROM online.position_status_snapshot
      WHERE pa_fiscal_code = ? AND notice_id = ?
      ORDER BY updated_timestamp DESC NULLS LAST, inserted_timestamp DESC, id DESC
      LIMIT 1
      """;

  private static final String ATTEMPT_STATUS_SQL = """
      SELECT status, inserted_timestamp, updated_timestamp
      FROM online.position_payment_status_snapshot
      WHERE pa_fiscal_code = ? AND notice_id = ? AND payment_token = ?
      ORDER BY updated_timestamp DESC NULLS LAST, inserted_timestamp DESC, id DESC
      LIMIT 1
      """;

  @Inject
  @io.quarkus.agroal.DataSource("online-ndp001")
  DataSource dataSource;

  public Optional<OnlineStatusSnapshot> findPaymentStatus(String paFiscalCode, String noticeId)
      throws SQLException {
    return findLatest(PAYMENT_STATUS_SQL, paFiscalCode, noticeId);
  }

  public Optional<OnlineStatusSnapshot> findAttemptStatus(
      String paFiscalCode, String noticeId, String paymentToken) throws SQLException {
    return findLatest(ATTEMPT_STATUS_SQL, paFiscalCode, noticeId, paymentToken);
  }

  private Optional<OnlineStatusSnapshot> findLatest(String sql, String... keys) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < keys.length; i++) {
        statement.setString(i + 1, keys[i]);
      }
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        return Optional.of(new OnlineStatusSnapshot(
            result.getString("status"),
            result.getObject("inserted_timestamp", LocalDateTime.class),
            result.getObject("updated_timestamp", LocalDateTime.class)));
      }
    }
  }
}
