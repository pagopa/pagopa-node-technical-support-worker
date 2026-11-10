package it.gov.pagopa.nodetsworker.repository;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class JdbcPositionPaymentSnapshotReader implements PositionPaymentSnapshotReader {

    private static final String FIND_BY_PAYMENT_TOKEN = """
            SELECT ID, PA_FISCAL_CODE, NOTICE_ID, CREDITOR_REFERENCE_ID, PAYMENT_TOKEN, STATUS,
                   INSERTED_TIMESTAMP, UPDATED_TIMESTAMP, FK_POSITION_PAYMENT, INSERTED_BY, UPDATED_BY
            FROM V_POSITION_PAYMENT_STATUS_SNAPSHOT
            WHERE PAYMENT_TOKEN = ?
            ORDER BY ID DESC
            LIMIT 1
            """;

    private final DataSource dataSource;

    public JdbcPositionPaymentSnapshotReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PositionPaymentSSEntity> findByPaymentToken(String paymentToken) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(FIND_BY_PAYMENT_TOKEN)) {
            statement.setString(1, paymentToken);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException exception) {
            throw new AppException(exception, AppErrorCodeMessageEnum.ERROR);
        }
    }

    private PositionPaymentSSEntity map(ResultSet resultSet) throws SQLException {
        return PositionPaymentSSEntity.builder()
                .id(resultSet.getLong("ID"))
                .paFiscalCode(resultSet.getString("PA_FISCAL_CODE"))
                .noticeId(resultSet.getString("NOTICE_ID"))
                .creditorReferenceId(resultSet.getString("CREDITOR_REFERENCE_ID"))
                .paymentToken(resultSet.getString("PAYMENT_TOKEN"))
                .status(resultSet.getString("STATUS"))
                .insertedTimestamp(toInstant(resultSet.getTimestamp("INSERTED_TIMESTAMP")))
                .updatedTimestamp(toInstant(resultSet.getTimestamp("UPDATED_TIMESTAMP")))
                .fkPositionPayment(resultSet.getObject("FK_POSITION_PAYMENT", Long.class))
                .insertedBy(resultSet.getString("INSERTED_BY"))
                .updatedBY(resultSet.getString("UPDATED_BY"))
                .build();
    }

    private java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
